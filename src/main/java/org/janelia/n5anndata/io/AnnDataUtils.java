package org.janelia.n5anndata.io;

import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import org.janelia.n5anndata.datastructures.CscArray;
import org.janelia.n5anndata.datastructures.CsrArray;
import org.janelia.n5anndata.datastructures.SparseArray;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


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
    private static final String ORDERED_KEY = "ordered";
    private static final String CATEGORIES_DIR = "categories";
    private static final String CODES_DIR = "codes";

    private static Checker checker = Checker.STRICT;


    public static void setChecker(final Checker checker) {
        AnnDataUtils.checker = checker;
    }

    public static void initializeAnnData(
            final List<String> obsNames,
            final N5Options obsOptions,
            final List<String> varNames,
            final N5Options varOptions,
            final N5Writer writer) {
        // temporarily disable checker to avoid cyclical dependencies
        final Checker oldChecker = checker;
        setChecker(Checker.NONE);

        if (writer.list(AnnDataPath.ROOT).length > 0) {
            throw new AnnDataException("Cannot initialize AnnData: target container is not empty.");
        }

        writer.createGroup(AnnDataPath.ROOT);
        writeFieldType(writer, AnnDataPath.ROOT, AnnDataFieldType.ANNDATA);

        createDataFrame(obsNames, writer, AnnDataField.OBS, "", obsOptions);
        createDataFrame(varNames, writer, AnnDataField.VAR, "", varOptions);
        createMapping(writer, AnnDataField.LAYERS.getPath());
        createMapping(writer, AnnDataField.OBSM.getPath());
        createMapping(writer, AnnDataField.OBSP.getPath());
        createMapping(writer, AnnDataField.VARM.getPath());
        createMapping(writer, AnnDataField.VARP.getPath());
        createMapping(writer, AnnDataField.UNS.getPath());

        setChecker(oldChecker);
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
                    && reader.exists(AnnDataField.OBS.getPath()) && isDataFrame(reader, AnnDataField.OBS.getPath())
                    && reader.exists(AnnDataField.VAR.getPath()) && isDataFrame(reader, AnnDataField.VAR.getPath())
                    && reader.exists(AnnDataField.LAYERS.getPath()) && getFieldType(reader, AnnDataField.LAYERS.getPath()).equals(AnnDataFieldType.MAPPING)
                    && reader.exists(AnnDataField.OBSM.getPath()) && getFieldType(reader, AnnDataField.OBSM.getPath()).equals(AnnDataFieldType.MAPPING)
                    && reader.exists(AnnDataField.OBSP.getPath()) && getFieldType(reader, AnnDataField.OBSP.getPath()).equals(AnnDataFieldType.MAPPING)
                    && reader.exists(AnnDataField.VARM.getPath()) && getFieldType(reader, AnnDataField.VARM.getPath()).equals(AnnDataFieldType.MAPPING)
                    && reader.exists(AnnDataField.VARP.getPath()) && getFieldType(reader, AnnDataField.VARP.getPath()).equals(AnnDataFieldType.MAPPING)
                    && reader.exists(AnnDataField.UNS.getPath()) && getFieldType(reader, AnnDataField.UNS.getPath()).equals(AnnDataFieldType.MAPPING);
        } catch (final Exception e) {
            return false;
        }
    }

    public static long getNObs(final N5Reader reader) {
        return getDataFrameIndexSize(reader, AnnDataField.OBS.getPath());
    }

    public static long getNVar(final N5Reader reader) {
        return getDataFrameIndexSize(reader, AnnDataField.VAR.getPath());
    }

    public static long getDataFrameIndexSize(final N5Reader reader, final String path) {
        final AnnDataPath indexPath = getDataFrameIndexPath(reader, path);
        final DatasetAttributes attributes = reader.getDatasetAttributes(indexPath.toString());
        return attributes.getDimensions()[0];
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
                return N5StringUtils.open(reader, completePath);
            case CATEGORICAL_ARRAY:
                return readCategoricalList(reader, completePath);
            default:
                throw new AnnDataException("Reading string array for '" + type + "' not supported.");
        }
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
        final AnnDataPath basePath = AnnDataPath.fromString(path);
        final List<String> categoryNames = N5StringUtils.open(reader, basePath.append(CATEGORIES_DIR).toString());
        final Img<? extends IntegerType<?>> categories = N5Utils.open(reader, basePath.append(CODES_DIR).toString());

        final int nElements = (int) categories.size();
        final List<String> denormalizedNames = new ArrayList<>(nElements);

        for (final IntegerType<?> category : categories) {
            denormalizedNames.add(categoryNames.get(category.getInteger()));
        }
        return denormalizedNames;
    }

    private static void writeFieldType(final N5Writer writer, final AnnDataField field, final String path, final AnnDataFieldType type) {
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

        AnnDataFieldType.checkIfNumericalArray(type);
        final long[] shape = flip(data.dimensionsAsLongArray());
        final String completePath = field.getPath(path);
        checker.check(writer, completePath, type, shape);

        if (writer.exists(completePath))
            throw new IllegalArgumentException("Dataset '" + completePath + "' already exists.");

        try {
            if (type == AnnDataFieldType.DENSE_ARRAY) {
                N5Utils.save(data, writer, completePath, options.blockSize, options.compression, options.exec);
            } else if (type == AnnDataFieldType.CSR_MATRIX || type == AnnDataFieldType.CSC_MATRIX) {
                writeSparseArray(writer, field, path, data, options, type);
            }
            writeFieldType(writer, completePath, type);
            conditionallyAddToDataFrame(writer, completePath);
        } catch (final ExecutionException | InterruptedException e) {
            throw new IOException("Could not write dataset at '" + completePath + "'.", e);
        }
    }

    private static long[] flip(final long[] array) {
        if (array.length == 1) {
            return array;
        } else if (array.length == 2) {
            return new long[]{array[1], array[0]};
        }
        throw new IllegalArgumentException("Array must have length 1 or 2.");
    }

    private static void conditionallyAddToDataFrame(final N5Writer writer, final String completePath) {
        final AnnDataPath path = AnnDataPath.fromString(completePath);
        final String parent = path.getParentPath();

        if (parent.isEmpty() || parent.equals(AnnDataPath.ROOT) || !isDataFrame(writer, parent))
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

        final boolean typeFitsData = (type == AnnDataFieldType.CSR_MATRIX && data instanceof CsrArray)
                || (type == AnnDataFieldType.CSC_MATRIX && data instanceof CscArray);
        final SparseArray<T, ?> sparse;
        if (typeFitsData) {
           sparse = (SparseArray<T, ?>) data;
        } else {
            final int leadingDim = (type == AnnDataFieldType.CSR_MATRIX) ? 0 : 1;
            sparse = SparseArray.convertToSparse(data, leadingDim);
        }

        final String completePath = field.getPath(path);
        writer.createGroup(completePath);
        final int[] blockSize = (options.blockSize.length == 1) ? options.blockSize : new int[]{options.blockSize[0]*options.blockSize[1]};
        N5Utils.save(sparse.getDataArray(), writer, completePath + DATA_DIR, blockSize, options.compression, options.exec);
        N5Utils.save(sparse.getIndicesArray(), writer, completePath + INDICES_DIR, blockSize, options.compression, options.exec);
        N5Utils.save(sparse.getIndexPointerArray(), writer, completePath + INDPTR_DIR, blockSize, options.compression, options.exec);

        final long[] shape = flip(data.dimensionsAsLongArray());
        writer.setAttribute(completePath, SHAPE_KEY, shape);
    }

    public static void createDataFrame(final List<String> index, final N5Writer writer, final AnnDataField field, final String path, final N5Options options) {
        final String completePath = field.getPath(path);
        checker.check(writer, completePath, AnnDataFieldType.DATA_FRAME, new long[] {index.size()});

        writer.createGroup(completePath);
        writeFieldType(writer, completePath, AnnDataFieldType.DATA_FRAME);

        final boolean isHDF5 = (writer instanceof N5HDF5Reader);
        writer.setAttribute(completePath, COLUMN_ORDER_KEY, isHDF5 ? "" : new String[0]);
        writer.setAttribute(completePath, INDEX_KEY, DEFAULT_INDEX_DIR);

        writeStringArray(index, writer, field, DEFAULT_INDEX_DIR, options, AnnDataFieldType.STRING_ARRAY);
    }

    public static void writeStringArray(final List<String> data, final N5Writer writer, final AnnDataField field, final String path, final N5Options options, final AnnDataFieldType type) {
        final String completePath = field.getPath(path);
        checker.check(writer, completePath, type, new long[] {data.size()});
        AnnDataFieldType.checkIfStringArray(type);

        switch (type) {
            case STRING_ARRAY:
                N5StringUtils.save(data, writer, completePath, options.blockSize, options.compression); break;
            case CATEGORICAL_ARRAY:
                writeCategoricalList(data, writer, completePath, options);
                throw new UnsupportedOperationException("Writing categorical arrays not supported.");
		}
        conditionallyAddToDataFrame(writer, completePath);
    }

    private static void writeCategoricalList(final List<String> data, final N5Writer writer, final String path, final N5Options options) {
        final List<String> uniqueElements = data.stream().distinct().collect(Collectors.toList());
        final Map<String, Integer> elementToCode = IntStream.range(0, uniqueElements.size()).boxed().collect(Collectors.toMap(uniqueElements::get, i -> i));
        final Img<IntType> categories = ArrayImgs.ints(data.stream().mapToInt(elementToCode::get).toArray(), data.size(), 1);

        writer.createGroup(path);
        writeFieldType(writer, path, AnnDataFieldType.CATEGORICAL_ARRAY);
        writer.setAttribute(path, ORDERED_KEY, false);

        final AnnDataPath basePath = AnnDataPath.fromString(path);
        N5StringUtils.save(uniqueElements, writer, basePath.append(CATEGORIES_DIR).toString(), options.blockSize, options.compression);
        N5Utils.save(categories, writer, basePath.append(CODES_DIR).toString(), options.blockSize, options.compression);
    }

    private static void createMapping(final N5Writer writer, final String path) {
        writer.createGroup(path);
        writeFieldType(writer, path, AnnDataFieldType.MAPPING);
    }

    private static boolean isDataFrame(final N5Reader reader, final String path) {
        final AnnDataFieldType type = getFieldType(reader, path);
        final AnnDataPath indexPath = getDataFrameIndexPath(reader, path);
        if (indexPath == null) return false;
        return type == AnnDataFieldType.DATA_FRAME && reader.exists(indexPath.toString());
    }

    private static AnnDataPath getDataFrameIndexPath(final N5Reader reader, final String path) {
        final String indexDir;
        try {
           indexDir = reader.getAttribute(path, INDEX_KEY, String.class);
        } catch (final Exception e) {
            return null;
        }
		return AnnDataPath.fromString(path).append(indexDir);
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
