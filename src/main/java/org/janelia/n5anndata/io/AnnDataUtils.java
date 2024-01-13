package org.janelia.n5anndata.io;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import org.janelia.n5anndata.datastructures.CscMatrix;
import org.janelia.n5anndata.datastructures.CsrMatrix;
import org.janelia.n5anndata.io.constraints.Checker;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static org.janelia.n5anndata.io.AnnDataDetails.*;


public class AnnDataUtils {
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

    public static void createMapping(final N5Writer writer, final String path) {
        createMapping(writer, AnnDataPath.fromString(path));
    }

    public static void createMapping(final N5Writer writer, final AnnDataPath path) {
        writer.createGroup(path.toString());
        setFieldType(writer, path, AnnDataFieldType.MAPPING);
    }
}
