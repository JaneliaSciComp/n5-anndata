package org.janelia.n5anndata.io;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import org.janelia.n5anndata.datastructures.CscMatrix;
import org.janelia.n5anndata.datastructures.CsrMatrix;
import org.janelia.n5anndata.datastructures.SparseArray;
import org.janelia.n5anndata.io.constraints.Checker;
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


public class AnnDataUtils {

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

        if (writer.list(AnnDataPath.ROOT.toString()).length > 0) {
            throw new AnnDataException("Cannot initialize AnnData: target container is not empty.");
        }

        writer.createGroup(AnnDataPath.ROOT.toString());
        setFieldType(writer, AnnDataPath.ROOT, AnnDataFieldType.ANNDATA);

        createDataFrame(obsNames, writer, new AnnDataPath(AnnDataField.OBS), obsOptions);
        createDataFrame(varNames, writer, new AnnDataPath(AnnDataField.VAR), varOptions);
        createMapping(writer, new AnnDataPath(AnnDataField.LAYERS));
        createMapping(writer, new AnnDataPath(AnnDataField.OBSM));
        createMapping(writer, new AnnDataPath(AnnDataField.OBSP));
        createMapping(writer, new AnnDataPath(AnnDataField.VARM));
        createMapping(writer, new AnnDataPath(AnnDataField.VARP));
        createMapping(writer, new AnnDataPath(AnnDataField.UNS));

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
                    && reader.exists(AnnDataField.OBS.getPath()) && isDataFrame(reader, new AnnDataPath(AnnDataField.OBS))
                    && reader.exists(AnnDataField.VAR.getPath()) && isDataFrame(reader, new AnnDataPath(AnnDataField.VAR))
                    && reader.exists(AnnDataField.LAYERS.getPath()) && getFieldType(reader, new AnnDataPath(AnnDataField.LAYERS)).equals(AnnDataFieldType.MAPPING)
                    && reader.exists(AnnDataField.OBSM.getPath()) && getFieldType(reader, new AnnDataPath(AnnDataField.OBSM)).equals(AnnDataFieldType.MAPPING)
                    && reader.exists(AnnDataField.OBSP.getPath()) && getFieldType(reader, new AnnDataPath(AnnDataField.OBSP)).equals(AnnDataFieldType.MAPPING)
                    && reader.exists(AnnDataField.VARM.getPath()) && getFieldType(reader, new AnnDataPath(AnnDataField.VARM)).equals(AnnDataFieldType.MAPPING)
                    && reader.exists(AnnDataField.VARP.getPath()) && getFieldType(reader, new AnnDataPath(AnnDataField.VARP)).equals(AnnDataFieldType.MAPPING)
                    && reader.exists(AnnDataField.UNS.getPath()) && getFieldType(reader, new AnnDataPath(AnnDataField.UNS)).equals(AnnDataFieldType.MAPPING);
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
       return getDataFrameIndexSize(reader, AnnDataPath.fromString(path));
    }

    public static long getDataFrameIndexSize(final N5Reader reader, final AnnDataPath path) {
        final AnnDataPath indexPath = getDataFrameIndexPath(reader, path);
        final DatasetAttributes attributes = reader.getDatasetAttributes(indexPath.toString());
        return attributes.getDimensions()[0];
    }

    public static List<String> readDataFrameIndex(final N5Reader reader, final String path) {
        return readDataFrameIndex(reader, AnnDataPath.fromString(path));
    }

    public static List<String> readDataFrameIndex(final N5Reader reader, final AnnDataPath path) {
        final AnnDataPath indexPath = getDataFrameIndexPath(reader, path);
        return readStringArray(reader, indexPath);
    }

    public static AnnDataFieldType getFieldType(final N5Reader reader, final String path) {
        return getFieldType(reader, AnnDataPath.fromString(path));
    }

    public static AnnDataFieldType getFieldType(final N5Reader reader, final AnnDataPath path) {
        final String encoding = reader.getAttribute(path.toString(), ENCODING_KEY, String.class);
        final String version = reader.getAttribute(path.toString(), VERSION_KEY, String.class);
        return AnnDataFieldType.fromString(encoding, version);
    }

    public static <T extends NativeType<T> & RealType<T>>
    Img<T> readNumericalArray(final N5Reader reader, final String path) {
        return readNumericalArray(reader, AnnDataPath.fromString(path));
    }

    public static <T extends NativeType<T> & RealType<T>, I extends NativeType<I> & IntegerType<I>>
    Img<T> readNumericalArray(final N5Reader reader, final AnnDataPath path) {
        final AnnDataFieldType type = getFieldType(reader, path);
        switch (type) {
            case MISSING:
                System.out.println("Array is missing metadata. Assuming dense array.");
                return N5Utils.open(reader, path.toString());
            case DENSE_ARRAY:
                return N5Utils.open(reader, path.toString());
            case CSR_MATRIX:
                return readSparseArray(reader, path, CsrMatrix<T,I>::new); // row
            case CSC_MATRIX:
                return readSparseArray(reader, path, CscMatrix<T,I>::new); // column
            default:
                throw new UnsupportedOperationException("Reading numerical array data from " + type + " not supported.");
        }
    }

    private static <T extends NativeType<T> & RealType<T>, I extends NativeType<I> & IntegerType<I>>
    SparseArray<T, I> readSparseArray(
            final N5Reader reader,
            final AnnDataPath path,
            final SparseArrayConstructor<T, I> constructor) {

        final Img<T> sparseData = N5Utils.open(reader, path.append(DATA_DIR).toString());
        final Img<I> indices = N5Utils.open(reader, path.append(INDICES_DIR).toString());
        final Img<I> indptr = N5Utils.open(reader, path.append(INDPTR_DIR).toString());

        final long[] shape = reader.getAttribute(path.toString(), SHAPE_KEY, long[].class);
        return constructor.apply(shape[1], shape[0], sparseData, indices, indptr);
    }

    public static List<String> readStringArray(final N5Reader reader, final String path) {
        return readStringArray(reader, AnnDataPath.fromString(path));
    }

    public static List<String> readStringArray(final N5Reader reader, final AnnDataPath path) {
        final AnnDataFieldType type = getFieldType(reader, path.toString());
        switch (type) {
            case STRING_ARRAY:
                return N5StringUtils.open(reader, path.toString());
            case CATEGORICAL_ARRAY:
                return readCategoricalList(reader, path);
            default:
                throw new AnnDataException("Reading string array for '" + type + "' not supported.");
        }
    }

    public static Set<String> getDataFrameDatasetNames(final N5Reader reader, final String path) {
        return getDataFrameDatasetNames(reader, AnnDataPath.fromString(path));
    }

    public static Set<String> getDataFrameDatasetNames(final N5Reader reader, final AnnDataPath path) {
        if (!reader.exists(path.toString())) {
            return new HashSet<>();
        }

        String[] rawArray = reader.getAttribute(path.toString(), COLUMN_ORDER_KEY, String[].class);
        rawArray = (rawArray == null) ? reader.list(path.toString()) : rawArray;

        if (rawArray == null || rawArray.length == 0) {
            return new HashSet<>();
        }

        final Set<String> datasets = new HashSet<>(Arrays.asList(rawArray));
        datasets.remove(INDEX_KEY);
        return datasets;
    }

    private static List<String> readCategoricalList(final N5Reader reader, final AnnDataPath path) {
        final List<String> categoryNames = N5StringUtils.open(reader, path.append(CATEGORIES_DIR).toString());
        final Img<? extends IntegerType<?>> categories = N5Utils.open(reader, path.append(CODES_DIR).toString());

        final int nElements = (int) categories.size();
        final List<String> denormalizedNames = new ArrayList<>(nElements);

        for (final IntegerType<?> category : categories) {
            denormalizedNames.add(categoryNames.get(category.getInteger()));
        }
        return denormalizedNames;
    }

    private static void setFieldType(final N5Writer writer, final AnnDataPath path, final AnnDataFieldType type) {
        writer.setAttribute(path.toString(), ENCODING_KEY, type.getEncoding());
        writer.setAttribute(path.toString(), VERSION_KEY, type.getVersion());
    }

    public static <T extends NativeType<T> & RealType<T>> void writeNumericalArray(
            final RandomAccessibleInterval<T> data,
            final N5Writer writer,
            final String path,
            final N5Options options,
            final AnnDataFieldType type) throws IOException {

        writeNumericalArray(data, writer, AnnDataPath.fromString(path), options, type);
    }

    public static <T extends NativeType<T> & RealType<T>> void writeNumericalArray(
            final RandomAccessibleInterval<T> data,
            final N5Writer writer,
            final AnnDataPath path,
            final N5Options options,
            final AnnDataFieldType type) throws IOException {

        AnnDataFieldType.ensureNumericalArray(type);
        final long[] shape = flip(data.dimensionsAsLongArray());
        checker.check(writer, path, type, shape);

        if (writer.exists(path.toString()))
            throw new IllegalArgumentException("Dataset '" + path + "' already exists.");

        try {
            if (type == AnnDataFieldType.DENSE_ARRAY) {
                if (options.exec == null) {
                    N5Utils.save(data, writer, path.toString(), options.blockSize, options.compression);
                } else {
                    N5Utils.save(data, writer, path.toString(), options.blockSize, options.compression, options.exec);
                }
            } else if (type == AnnDataFieldType.CSR_MATRIX || type == AnnDataFieldType.CSC_MATRIX) {
                writeSparseArray(writer, path, data, options, type);
            }
            setFieldType(writer, path, type);
            conditionallyAddToDataFrame(writer, path);
        } catch (final ExecutionException | InterruptedException e) {
            throw new IOException("Could not write dataset at '" + path + "'.", e);
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

    private static void conditionallyAddToDataFrame(final N5Writer writer, final AnnDataPath path) {
        final AnnDataPath parent = path.getParentPath();

        if (parent == AnnDataPath.ROOT || !isDataFrame(writer, parent))
            return;

        final String columnName = path.getLeaf();
        if (! columnName.equals(INDEX_KEY)) {
            final Set<String> existingData = getDataFrameDatasetNames(writer, parent);
            existingData.add(columnName);
            writer.setAttribute(parent.toString(), COLUMN_ORDER_KEY, existingData.toArray());
        }
    }

    private static <T extends NativeType<T> & RealType<T>> void writeSparseArray(
            final N5Writer writer,
            final AnnDataPath path,
            final RandomAccessibleInterval<T> data,
            final N5Options options,
            final AnnDataFieldType type) throws ExecutionException, InterruptedException {

        if (type != AnnDataFieldType.CSR_MATRIX && type != AnnDataFieldType.CSC_MATRIX)
            throw new IllegalArgumentException("Sparse array type must be CSR or CSC.");

        final boolean typeFitsData = (type == AnnDataFieldType.CSR_MATRIX && data instanceof CsrMatrix)
                || (type == AnnDataFieldType.CSC_MATRIX && data instanceof CscMatrix);
        final SparseArray<T, ?> sparse;
        if (typeFitsData) {
           sparse = (SparseArray<T, ?>) data;
        } else {
            final int leadingDim = (type == AnnDataFieldType.CSR_MATRIX) ? 0 : 1;
            sparse = SparseArray.convertToSparse(data, leadingDim);
        }

        writer.createGroup(path.toString());
        final int[] blockSize = (options.blockSize.length == 1) ? options.blockSize : new int[]{options.blockSize[0] * options.blockSize[1]};
        if (options.exec == null) {
            N5Utils.save(sparse.getDataArray(), writer, path.append(DATA_DIR).toString(), blockSize, options.compression);
            N5Utils.save(sparse.getIndicesArray(), writer, path.append(INDICES_DIR).toString(), blockSize, options.compression);
            N5Utils.save(sparse.getIndexPointerArray(), writer, path.append(INDPTR_DIR).toString(), blockSize, options.compression);
        } else {
            N5Utils.save(sparse.getDataArray(), writer, path.append(DATA_DIR).toString(), blockSize, options.compression, options.exec);
            N5Utils.save(sparse.getIndicesArray(), writer, path.append(INDICES_DIR).toString(), blockSize, options.compression, options.exec);
            N5Utils.save(sparse.getIndexPointerArray(), writer, path.append(INDPTR_DIR).toString(), blockSize, options.compression, options.exec);
        }

        final long[] shape = flip(data.dimensionsAsLongArray());
        writer.setAttribute(path.toString(), SHAPE_KEY, shape);
    }

    public static void createDataFrame(final List<String> index, final N5Writer writer, final String path, final N5Options options) {
        createDataFrame(index, writer, AnnDataPath.fromString(path), options);
    }

    public static void createDataFrame(final List<String> index, final N5Writer writer, final AnnDataPath path, final N5Options options) {
        checker.check(writer, path, AnnDataFieldType.DATA_FRAME, new long[] {index.size(), Integer.MAX_VALUE});

        writer.createGroup(path.toString());
        setFieldType(writer, path, AnnDataFieldType.DATA_FRAME);

        final boolean isHDF5 = (writer instanceof N5HDF5Reader);
        writer.setAttribute(path.toString(), COLUMN_ORDER_KEY, isHDF5 ? "" : new String[0]);
        writer.setAttribute(path.toString(), INDEX_KEY, DEFAULT_INDEX_DIR);

        final Checker oldChecker = checker;
        setChecker(Checker.NONE);
        writeStringArray(index, writer, path.append(DEFAULT_INDEX_DIR), options, AnnDataFieldType.STRING_ARRAY);
        setChecker(oldChecker);
    }

    public static void writeStringArray(final List<String> data, final N5Writer writer, final String path, final N5Options options, final AnnDataFieldType type) {
        writeStringArray(data, writer, AnnDataPath.fromString(path), options, type);
    }

    public static void writeStringArray(final List<String> data, final N5Writer writer, final AnnDataPath path, final N5Options options, final AnnDataFieldType type) {
        checker.check(writer, path, type, new long[] {data.size()});
        AnnDataFieldType.ensureStringArray(type);

        switch (type) {
            case STRING_ARRAY:
                N5StringUtils.save(data, writer, path.toString(), options.blockSize, options.compression);
                setFieldType(writer, path, type);
                break;
            case CATEGORICAL_ARRAY:
                writeCategoricalList(data, writer, path, options);
        }
        conditionallyAddToDataFrame(writer, path);
    }

    private static void writeCategoricalList(final List<String> data, final N5Writer writer, final AnnDataPath path, final N5Options options) {
        final List<String> uniqueElements = data.stream().distinct().collect(Collectors.toList());
        final Map<String, Integer> elementToCode = IntStream.range(0, uniqueElements.size()).boxed().collect(Collectors.toMap(uniqueElements::get, i -> i));
        final Img<IntType> categories = ArrayImgs.ints(data.stream().mapToInt(elementToCode::get).toArray(), data.size());

        writer.createGroup(path.toString());
        setFieldType(writer, path, AnnDataFieldType.CATEGORICAL_ARRAY);
        writer.setAttribute(path.toString(), ORDERED_KEY, false);

        N5StringUtils.save(uniqueElements, writer, path.append(CATEGORIES_DIR).toString(), options.blockSize, options.compression);
        N5Utils.save(categories, writer, path.append(CODES_DIR).toString(), options.blockSize, options.compression);
    }

    public static void createMapping(final N5Writer writer, final String path) {
        createMapping(writer, AnnDataPath.fromString(path));
    }

    public static void createMapping(final N5Writer writer, final AnnDataPath path) {
        writer.createGroup(path.toString());
        setFieldType(writer, path, AnnDataFieldType.MAPPING);
    }

    private static boolean isDataFrame(final N5Reader reader, final AnnDataPath path) {
        final AnnDataFieldType type = getFieldType(reader, path);
        final AnnDataPath indexPath = getDataFrameIndexPath(reader, path);
        if (indexPath == null) return false;
        return type == AnnDataFieldType.DATA_FRAME && reader.exists(indexPath.toString());
    }

    private static AnnDataPath getDataFrameIndexPath(final N5Reader reader, final AnnDataPath path) {
        final String indexDir;
        indexDir = reader.getAttribute(path.toString(), INDEX_KEY, String.class);
        return path.append(indexDir);
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
