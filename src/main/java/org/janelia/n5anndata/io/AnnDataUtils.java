package org.janelia.n5anndata.io;

import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ch.systemsx.cisd.hdf5.IHDF5StringWriter;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.n5anndata.datastructures.CscArray;
import org.janelia.n5anndata.datastructures.CsrArray;
import org.janelia.n5anndata.datastructures.SparseArray;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;


class AnnDataUtils {

    // encoding metadata
    private static final String ENCODING_KEY = "encoding-type";
    private static final String VERSION_KEY = "encoding-version";
    private static final String SHAPE_KEY = "shape";

    // dataframe metadata
    private static final String INDEX_KEY = "_index";
    private static final String COLUMN_ORDER_KEY = "column-order";
    private static final String DEFAULT_INDEX_DIR = "_index";

    // sparse metadata
    private static final String DATA_DIR = "data";
    private static final String INDICES_DIR = "indices";
    private static final String INDPTR_DIR = "indptr";

    // categorical metadata
    private static final String CATEGORIES_DIR = "categories";
    private static final String CODES_DIR = "codes";


    public static void initializeAnnData(
            final List<String> obsNames,
            final N5Options obsOptions,
            final List<String> varNames,
            final N5Options varOptions,
            final N5Writer writer) {
        if (writer.list(AnnDataPath.ROOT).length > 0) {
            throw new AnnDataException("Cannot initialize AnnData: target container is not empty.");
        }
        writer.createGroup(AnnDataPath.ROOT);
        writeFieldType(writer, AnnDataPath.ROOT, AnnDataFieldType.ANNDATA);
        createDataFrame(obsNames, writer, AnnDataField.OBS, "", obsOptions);
        createDataFrame(varNames, writer, AnnDataField.VAR, "", varOptions);
    }

    public static void initializeAnnData(
            final List<String> obsNames,
            final List<String> varNames,
            final N5Writer writer,
            final N5Options options) {
        initializeAnnData(obsNames, options, varNames, options, writer);
    }

    // TODO: check metadata for all fields
    public static boolean isValidAnnData(final N5Reader reader) {
        try {
            return getFieldType(reader, AnnDataPath.ROOT).equals(AnnDataFieldType.ANNDATA)
                    && reader.exists(AnnDataField.OBS.getPath())
                    && isDataFrame(reader, AnnDataField.OBS.getPath())
                    && reader.exists(AnnDataField.VAR.getPath())
                    && isDataFrame(reader, AnnDataField.VAR.getPath());
        } catch (final Exception e) {
            return false;
        }
    }

    public static AnnDataFieldType getFieldType(final N5Reader reader, final String path) {
        final String encoding = reader.getAttribute(path, ENCODING_KEY, String.class);
        final String version = reader.getAttribute(path, VERSION_KEY, String.class);
        return AnnDataFieldType.fromString(encoding, version);
    }

    public static <T extends NativeType<T> & RealType<T>, I extends NativeType<I> & IntegerType<I>>
    Img<T> readNumericalArray(final N5Reader reader, final AnnDataField field, final String path) {
        final String completePath = field.getPath(path);
        final AnnDataFieldType type = getFieldType(reader, completePath);
        switch (type) {
            case MISSING:
                System.out.println("Array is missing metadata. Assuming dense array.");
                return N5Utils.open(reader, completePath);
            case DENSE_ARRAY:
                return N5Utils.open(reader, completePath);
            case CSR_MATRIX:
                return openSparseArray(reader, completePath, CsrArray<T,I>::new); // row
            case CSC_MATRIX:
                return openSparseArray(reader, completePath, CscArray<T,I>::new); // column
            default:
                throw new UnsupportedOperationException("Reading numerical array data from " + type + " not supported.");
        }
    }

    private static <T extends NativeType<T> & RealType<T>, I extends NativeType<I> & IntegerType<I>>
    SparseArray<T, I> openSparseArray(
            final N5Reader reader,
            final String path,
            final SparseArrayConstructor<T, I> constructor) {

        final Img<T> sparseData = N5Utils.open(reader, path + DATA_DIR);
        final Img<I> indices = N5Utils.open(reader, path + INDICES_DIR);
        final Img<I> indptr = N5Utils.open(reader, path + INDPTR_DIR);

        final long[] shape = reader.getAttribute(path, SHAPE_KEY, long[].class);
        return constructor.apply(shape[1], shape[0], sparseData, indices, indptr);
    }

    public static List<String> readStringArray(final N5Reader reader, final AnnDataField field, final String path) {
        final String completePath = field.getPath(path);
        final AnnDataFieldType type = getFieldType(reader, path);
        switch (type) {
            case STRING_ARRAY:
                return readStringList(reader, completePath);
            case CATEGORICAL_ARRAY:
                return readCategoricalList(reader, completePath);
            default:
                throw new UnsupportedOperationException("Reading string annotations for " + type + " not supported.");
        }
    }

    private static List<String> readStringList(final N5Reader reader, final String path) {
        final String[] array = readPrimitiveStringArray((N5HDF5Reader) reader, path);
        return Arrays.asList(array);
    }

    private static String[] readPrimitiveStringArray(final N5HDF5Reader reader, final String path) {
        final IHDF5Reader hdf5Reader = HDF5Factory.openForReading(reader.getFilename());
        return hdf5Reader.readStringArray(path);
    }

    public static Set<String> getExistingDataFrameDatasets(final N5Reader reader, final AnnDataField field, final String dataFrame) {
        final String completePath = field.getPath(dataFrame);
        return getExistingDataFrameDatasets(reader, completePath);
    }

    private static Set<String> getExistingDataFrameDatasets(final N5Reader reader, final String path) {
        if (!reader.exists(path)) {
            return new HashSet<>();
        }

        String[] rawArray = reader.getAttribute(path, COLUMN_ORDER_KEY, String[].class);
        rawArray = (rawArray == null) ? reader.list(path) : rawArray;

        if (rawArray == null || rawArray.length == 0) {
            return new HashSet<>();
        }

        final Set<String> datasets = new HashSet<>(Arrays.asList(rawArray));
        datasets.remove(INDEX_KEY);
        return datasets;
    }

    private static List<String> readCategoricalList(final N5Reader reader, final String path) {
        final String[] categoryNames = readPrimitiveStringArray((N5HDF5Reader) reader, path + CATEGORIES_DIR);
        final Img<? extends IntegerType<?>> category = N5Utils.open(reader, path + CODES_DIR);
        final RandomAccess<? extends IntegerType<?>> ra = category.randomAccess();

        // assume that minimal index = 0
        final int max = (int) category.max(0);
        final String[] names = new String[max];
        for (int i = 0; i < max; ++i) {
            names[i] = categoryNames[ra.setPositionAndGet(i).getInteger()];
        }

        return Arrays.asList(names);
    }

    private static void writeFieldType(final N5Writer writer, final AnnDataField field, final String path, final AnnDataFieldType type) {
        if (! field.canHaveAsChild(type)) {
            throw new IllegalArgumentException("Field " + field + " cannot have child of type " + type);
        }
        final String completePath = field.getPath(path);
        writeFieldType(writer, completePath, type);
    }

    private static void writeFieldType(final N5Writer writer, final String path, final AnnDataFieldType type) {
        writer.setAttribute(path, ENCODING_KEY, type.getEncoding());
        writer.setAttribute(path, VERSION_KEY, type.getVersion());
    }

    public static <T extends NativeType<T> & RealType<T>> void writeArray(
            final Img<T> data,
            final N5Writer writer,
            final AnnDataField field,
            final String path,
            final N5Options options) throws IOException {

        AnnDataFieldType type = AnnDataFieldType.DENSE_ARRAY;
        if (data instanceof CsrArray) {
            type = AnnDataFieldType.CSR_MATRIX;
        }
        if (data instanceof CscArray) {
            type = AnnDataFieldType.CSC_MATRIX;
        }

        writeArray(data, writer, field, path, options, type);
    }

    public static <T extends NativeType<T> & RealType<T>> void writeArray(
            final Img<T> data,
            final N5Writer writer,
            final AnnDataField field,
            final String path,
            final N5Options options,
            final AnnDataFieldType type) throws IOException {

        if (! field.canHaveAsChild(type)) {
            throw new IllegalArgumentException("Field " + field + " cannot have child of type " + type);
        }
        AnnDataFieldType.checkIfNumericalArray(type);

        final String completePath = field.getPath(path);
        if (writer.exists(completePath))
            throw new IllegalArgumentException("Dataset '" + completePath + "' already exists.");

        try {
            if (type == AnnDataFieldType.DENSE_ARRAY) {
                N5Utils.save(data, writer, completePath, options.blockSize, options.compression, options.exec);
            } else if (type == AnnDataFieldType.CSR_MATRIX || type == AnnDataFieldType.CSC_MATRIX) {
                writeSparseArray(writer, field, path, data, options, type);
            }
            writer.setAttribute(path, SHAPE_KEY, new long[]{data.dimension(1), data.dimension(0)});
            writeFieldType(writer, path, type);
            conditionallyAddToDataFrame(writer, completePath);
        } catch (final ExecutionException | InterruptedException e) {
            throw new IOException("Could not write dataset at '" + path + "'.", e);
        }
    }

    private static void conditionallyAddToDataFrame(final N5Writer writer, final String completePath) {
        final AnnDataPath path = AnnDataPath.fromString(completePath);
        final String parent = path.getParentPath();

        if (parent.isEmpty() || !isDataFrame(writer, parent))
            return;

        final String columnName = path.getLeaf();
        final Set<String> existingData = getExistingDataFrameDatasets(writer, parent);
        existingData.add(columnName);
        writer.setAttribute(completePath, COLUMN_ORDER_KEY, existingData.toArray());
    }

    private static <T extends NativeType<T> & RealType<T>> void writeSparseArray(
            final N5Writer writer,
            final AnnDataField field,
            final String path,
            final Img<T> data,
            final N5Options options,
            final AnnDataFieldType type) throws ExecutionException, InterruptedException {

        if (type != AnnDataFieldType.CSR_MATRIX && type != AnnDataFieldType.CSC_MATRIX)
            throw new IllegalArgumentException("Sparse array type must be CSR or CSC.");

        final SparseArray<T, ?> sparse;
        final boolean typeFitsData = (type == AnnDataFieldType.CSR_MATRIX && data instanceof CsrArray)
                || (type == AnnDataFieldType.CSC_MATRIX && data instanceof CscArray);
        if (typeFitsData) {
           sparse = (SparseArray<T, ?>) data;
        }
        else {
            final int leadingDim = (type == AnnDataFieldType.CSR_MATRIX) ? 0 : 1;
            sparse = SparseArray.convertToSparse(data, leadingDim);
        }

        final String completePath = field.getPath(path);
        writer.createGroup(completePath);
        final int[] blockSize = (options.blockSize.length == 1) ? options.blockSize : new int[]{options.blockSize[0]*options.blockSize[1]};
        N5Utils.save(sparse.getDataArray(), writer, completePath + DATA_DIR, blockSize, options.compression, options.exec);
        N5Utils.save(sparse.getIndicesArray(), writer, completePath + INDICES_DIR, blockSize, options.compression, options.exec);
        N5Utils.save(sparse.getIndexPointerArray(), writer, completePath + INDPTR_DIR, blockSize, options.compression, options.exec);
    }

    public static void createDataFrame(final List<String> index, final N5Writer writer, final AnnDataField field, final String path, final N5Options options) {
        final String completePath = field.getPath(path);
        writer.createGroup(completePath);
        writeFieldType(writer, completePath, AnnDataFieldType.DATA_FRAME);
        writer.setAttribute(completePath, INDEX_KEY, INDEX_KEY);
        // TODO: does empty list/array work?
        // this should be an empty attribute, which N5 doesn't support -> use "" as surrogate
        writer.setAttribute(completePath, COLUMN_ORDER_KEY, "");
        writer.setAttribute(completePath, INDEX_KEY, DEFAULT_INDEX_DIR);

        writeStringArray(index, writer, field, DEFAULT_INDEX_DIR, options, AnnDataFieldType.STRING_ARRAY);
    }

    public static void writeStringArray(final List<String> data, final N5Writer writer, final AnnDataField field, final String path, final N5Options options, final AnnDataFieldType type) {
        if (! field.canHaveAsChild(type)) {
            throw new IllegalArgumentException("Field " + field + " cannot have child of type " + type);
        }
        AnnDataFieldType.checkIfStringArray(type);
        
        final String completePath = field.getPath(path);
        switch (type) {
            case STRING_ARRAY:
                if (writer instanceof N5HDF5Writer) {
                    writePrimitiveStringArray((N5HDF5Writer) writer, completePath, data.toArray(new String[0]));
                } else {
                    throw new UnsupportedOperationException("Writing string arrays only supported for HDF5.");
                }
            case CATEGORICAL_ARRAY:
                throw new UnsupportedOperationException("Writing categorical arrays not supported.");
        }
        conditionallyAddToDataFrame(writer, completePath);
    }
    
    private static void writePrimitiveStringArray(final N5HDF5Writer writer, final String path, final String[] array) {
        final IHDF5StringWriter stringWriter = HDF5Factory.open(writer.getFilename()).string();
        stringWriter.writeArrayVL(path, array);
    }

    private static void createMapping(final N5Writer writer, final String path) {
        writer.createGroup(path);
        writeFieldType(writer, path, AnnDataFieldType.MAPPING);
    }

    private static boolean isDataFrame(final N5Reader reader, final String path) {
        final AnnDataFieldType type = getFieldType(reader, path);
        final String indexDir;
        try {
           indexDir = reader.getAttribute(path, INDEX_KEY, String.class);
        } catch (final Exception e) {
            return false;
        }
        final AnnDataPath indexPath = AnnDataPath.fromString(path).append(indexDir);
        return type == AnnDataFieldType.DATA_FRAME
                && reader.exists(indexPath.toString())
                && (reader.getAttribute(path, COLUMN_ORDER_KEY, String[].class) != null);
    }


    // interface used to make reading sparse arrays more generic
    @FunctionalInterface
    private interface SparseArrayConstructor<
            D extends NativeType<D> & RealType<D>,
            I extends NativeType<I> & IntegerType<I>> {
        SparseArray<D, I> apply(
                long numCols,
                long numRows,
                Img<D> data,
                Img<I> indices,
                Img<I> indptr);
    }
}
