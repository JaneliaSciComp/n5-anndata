package org.janelia.n5anndata.io;

import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ch.systemsx.cisd.hdf5.IHDF5StringWriter;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.IntegerType;
import org.janelia.n5anndata.datastructures.CscArray;
import org.janelia.n5anndata.datastructures.CsrArray;
import org.janelia.n5anndata.datastructures.SparseArray;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;


class AnnDataUtils {

    private static final String ROOT = "/";
    private static final String SEPARATOR = "/";

    // encoding metadata
    private static final String ENCODING_KEY = "encoding-type";
    private static final String VERSION_KEY = "encoding-version";
    private static final String SHAPE_KEY = "shape";

    // dataframe metadata
    private static final String INDEX_KEY = "_index";
    private static final String COLUMN_ORDER_KEY = "column-order";
    private static final String INDEX_DIR = "/_index";

    // sparse metadata
    private static final String DATA_DIR = "/data";
    private static final String INDICES_DIR = "/indices";
    private static final String INDPTR_DIR = "/indptr";

    // categorical metadata
    private static final String CATEGORIES_DIR = "/categories";
    private static final String CODES_DIR = "/codes";


    public static void initializeAnnData(final N5Writer writer) {
        if (writer.list(ROOT).length > 0) {
            throw new AnnDataException("Cannot initialize AnnData: target container is not empty.");
        }
        writer.createGroup(ROOT);
        writeFieldType(writer, ROOT, AnnDataFieldType.ANNDATA);
    }

    // TODO: check metadata for all fields
    public static boolean isValidAnnData(final N5Reader n5) {
        try {
            return getFieldType(n5, ROOT).toString().equals(AnnDataFieldType.ANNDATA.toString());
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

    public static List<String> readStringAnnotation(final N5Reader reader, final AnnDataField field, final String path) {
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

    public static <T extends NativeType<T> & RealType<T>> RandomAccessibleInterval<T>
    readColumnFromDataFrame(final N5Reader reader, final AnnDataField field, final String dataFrame, final String columnName) {
        final String completePath = field.getPath(dataFrame);
        final List<String> existingData = getExistingDataFrameDatasets(reader, field, dataFrame);
        if (!existingData.contains(columnName))
            throw new IllegalArgumentException("Dataframe '" + completePath + "' does not contain '" + columnName + "'.");

        // TODO: this returns null for non-readable datatypes (e.g. string); is there a better way to treat string annotations?
        final DatasetAttributes attributes = reader.getDatasetAttributes(completePath + SEPARATOR + columnName);
        if (attributes == null || attributes.getDataType() == null) {
            return null;
        }

        return N5Utils.open(reader, completePath + SEPARATOR + columnName);
    }

    public static List<String> getExistingDataFrameDatasets(final N5Reader reader, final AnnDataField field, final String dataFrame) {
        final String completePath = field.getPath(dataFrame);
        if (!reader.exists(completePath)) {
            return new ArrayList<>();
        }

        String[] rawArray = reader.getAttribute(completePath, COLUMN_ORDER_KEY, String[].class);
        rawArray = (rawArray == null) ? reader.list(completePath) : rawArray;

        if (rawArray == null || rawArray.length == 0) {
            return new ArrayList<>();
        }

        final List<String> datasets = new ArrayList<>(Arrays.asList(rawArray));
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

    public static void writeFieldType(final N5Writer writer, final AnnDataField field, final String path, final AnnDataFieldType type) {
        if (!field.allows(type)) {
            throw new AnnDataException("Field '" + field + "' does not allow type '" + type + "'.");
        }
        final String completePath = field.getPath(path);
        writeFieldType(writer, completePath, type);
    }

    private static void writeFieldType(final N5Writer writer, final String path, final AnnDataFieldType type) {
        writer.setAttribute(path, ENCODING_KEY, type.getEncoding());
        writer.setAttribute(path, VERSION_KEY, type.getVersion());
    }

    public static <T extends NativeType<T> & RealType<T>> void writeArray(
            final N5Writer writer,
            final AnnDataField field,
            final String path,
            final Img<T> data,
            final N5Options options) throws IOException {

        AnnDataFieldType type = AnnDataFieldType.DENSE_ARRAY;
        if (data instanceof CsrArray) {
            type = AnnDataFieldType.CSR_MATRIX;
        }
        if (data instanceof CscArray) {
            type = AnnDataFieldType.CSC_MATRIX;
        }

        writeArray(writer, field, path, data, options, type);
    }

    public static <T extends NativeType<T> & RealType<T>> void writeArray(
            final N5Writer writer,
            final AnnDataField field,
            final String path,
            final Img<T> data,
            final N5Options options,
            final AnnDataFieldType type) throws IOException {

        if (!field.allows(type)) {
            throw new AnnDataException("Field '" + field + "' does not allow type '" + type + "'.");
        }

        try {
            if (type == AnnDataFieldType.DENSE_ARRAY) {
                final String completePath = field.getPath(path);
                N5Utils.save(data, writer, completePath, options.blockSize, options.compression, options.exec);
            } else if (type == AnnDataFieldType.CSR_MATRIX || type == AnnDataFieldType.CSC_MATRIX) {
                writeSparseArray(writer, field, path, data, options, type);
            } else {
                throw new UnsupportedOperationException("Writing array data for " + type.toString() + " not supported.");
            }
            writer.setAttribute(path, SHAPE_KEY, new long[]{data.dimension(1), data.dimension(0)});
            writeFieldType(writer, path, type);
        } catch (final ExecutionException | InterruptedException e) {
            throw new IOException("Could not load dataset at '" + path + "'.", e);
        }
    }

    public static <T extends NativeType<T> & RealType<T>> void writeSparseArray(
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

    public static void createDataFrame(final N5Writer writer, final AnnDataField field, final String path, final List<String> index) {
        final String completePath = field.getPath(path);
        writer.createGroup(completePath);
        writeFieldType(writer, completePath, AnnDataFieldType.DATA_FRAME);
        writer.setAttribute(completePath, INDEX_KEY, INDEX_KEY);
        // this should be an empty attribute, which N5 doesn't support -> use "" as surrogate
        writer.setAttribute(completePath, COLUMN_ORDER_KEY, "");

        writePrimitiveStringArray((N5HDF5Writer) writer, completePath + INDEX_DIR, index.toArray(new String[0]));
        writeFieldType(writer, completePath + INDEX_DIR, AnnDataFieldType.STRING_ARRAY);
    }

    public static <T extends NativeType<T> & RealType<T>> void addColumnToDataFrame(
            final N5Writer writer,
            final AnnDataField field,
            final String dataFrame,
            final String columnName,
            final RandomAccessibleInterval<T> data,
            final N5Options options) throws IOException {

        final String completePath = field.getPath(dataFrame);
        final List<String> existingData = getExistingDataFrameDatasets(writer, field, dataFrame);
        if (existingData.contains(columnName))
            throw new IllegalArgumentException("Dataframe '" + completePath + "' already contains '" + columnName + "'.");

        try {
            N5Utils.save(data, writer, completePath + SEPARATOR + columnName, options.blockSize, options.compression, options.exec);
            existingData.add(columnName);
            writer.setAttribute(completePath, COLUMN_ORDER_KEY, existingData.toArray());
        } catch (final InterruptedException | ExecutionException e) {
            throw new IOException("Could not write dataset '" + completePath + SEPARATOR + columnName + "'.", e);
        }
    }

    private static void writePrimitiveStringArray(final N5HDF5Writer writer, final String path, final String[] array) {
        final IHDF5StringWriter stringWriter = HDF5Factory.open(writer.getFilename()).string();
        stringWriter.writeArrayVL(path, array);
    }

    private static void createMapping(final N5Writer writer, final String path) {
        writer.createGroup(path);
        writeFieldType(writer, path, AnnDataFieldType.MAPPING);
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
