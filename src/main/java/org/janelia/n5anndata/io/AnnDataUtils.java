/*-
 * #%L
 * N5 AnnData Utilities
 * %%
 * Copyright (C) 2023 - 2024 Howard Hughes Medical Institute
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the HHMI nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
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
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static org.janelia.n5anndata.io.AnnDataDetails.*;


/**
 * This class provides utility methods for working with AnnData structures.
 * The methods in this class perform type and dimension checking if applicable
 * and according to the given {@link Checker}. The methods can be divided into
 * three categories:
 * <p>
 * <ul> <li> <b>Initialization</b>: Methods for initializing and validating an AnnData container on disk. </li>
 * <li> <b>I/O</b>: Methods for reading and writing arrays in AnnData containers. </li>
 * <li> <b>Dataframes</b>: Methods for working with dataframes on disk. </li> </ul>
 *
 * @author Michael Innerberger
 */
public class AnnDataUtils {
    private static final String HOUSEKEEPING_GROUP = "/__DATA_TYPES__";
    private static Checker checker = Checker.STRICT;


    /**
     * Sets the checker to use for validating AnnData structures.
     *
     * @param checker The checker to use.
     */
    public static void setChecker(final Checker checker) {
        AnnDataUtils.checker = checker;
    }

    /**
     * Initializes an AnnData structure with the given parameters in an empty container.
     * In particular, all required groups and metadata are set.
     *
     * @param obsNames The names of the observations.
     * @param obsOptions The options for the observations.
     * @param varNames The names of the variables.
     * @param varOptions The options for the variables.
     * @param writer The N5 writer to use.
     * @throws AnnDataException If the target container is not empty.
     */
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

    /**
     * Initializes an AnnData structure with the given parameters in an empty container.
     * In particular, all required groups and metadata are set.
     *
     * @param obsNames The names of the observations.
     * @param varNames The names of the variables.
     * @param writer The N5 writer to use.
     * @param options The options for the observations and variables.
     * @throws AnnDataException If the target container is not empty.
     */
    public static void initializeAnnData(
            final List<String> obsNames,
            final List<String> varNames,
            final N5Writer writer,
            final N5Options options) {
        initializeAnnData(obsNames, options, varNames, options, writer);
    }

    /**
     * Finalizes writing to an AnnData container. In particular, JHDF5 sets up a housekeeping group
     * (usually called "/__DATA_TYPES__") that causes anndata to fail when reading the container from
     * Python.
     * <p>
     * It is recommended to call this method after every time an  AnnData container was accessed
     * with an N5Writer.
     *
     * @param writer The N5 writer pointing to the AnnData container.
     * @throws AnnDataException If the housekeeping group could not be removed.
     */
    public static void finalizeAnnData(final N5Writer writer) {
        if (writer instanceof N5HDF5Writer) {
            final boolean removalSuccessful = writer.remove(HOUSEKEEPING_GROUP);
            if (! removalSuccessful) {
                throw new AnnDataException("Could not remove housekeeping group for HDF5 container. File might not be readable from Python.");
            }
        }
    }

    /**
     * Checks if the given reader points to a valid AnnData structure, i.e.,
     * if all required groups and metadata are present.
     *
     * @param reader The N5 reader to use.
     * @return True if the reader contains a valid AnnData structure, false otherwise.
     */
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

    /**
     * Returns the number of observations in the AnnData structure.
     *
     * @param reader The N5 reader to use.
     * @return The number of observations.
     */
    public static long getNObs(final N5Reader reader) {
        return getDataFrameIndexSize(reader, AnnDataField.OBS.getPath());
    }

    /**
     * Returns the number of variables in the AnnData structure.
     *
     * @param reader The N5 reader to use.
     * @return The number of variables.
     */
    public static long getNVar(final N5Reader reader) {
        return getDataFrameIndexSize(reader, AnnDataField.VAR.getPath());
    }

    /**
     * Returns the size of the index of the data frame at the given path. Per default,
     * the index is stored in a dataset called "_index" in the data frame group, but this
     * can be changed by setting the "index" attribute of the data frame group.
     *
     * @param reader The N5 reader to use.
     * @param path The path to the data frame.
     * @return The size of the index.
     */
    public static long getDataFrameIndexSize(final N5Reader reader, final String path) {
       return getDataFrameIndexSize(reader, AnnDataPath.fromString(path));
    }

    /**
     * Returns the size of the index of the data frame at the given path. Per default,
     * the index is stored in a dataset called "_index" in the data frame group, but this
     * can be changed by setting the "index" attribute of the data frame group.
     *
     * @param reader The N5 reader to use.
     * @param path The path to the data frame.
     * @return The size of the index.
     */
    public static long getDataFrameIndexSize(final N5Reader reader, final AnnDataPath path) {
        final AnnDataPath indexPath = getDataFrameIndexPath(reader, path);
        final DatasetAttributes attributes = reader.getDatasetAttributes(indexPath.toString());
        return attributes.getDimensions()[0];
    }

    /**
     * Reads the index of the data frame at the given path.
     *
     * @param reader The N5 reader to use.
     * @param path The path to the data frame.
     * @return The index of the data frame as a list of strings.
     */
    public static List<String> readDataFrameIndex(final N5Reader reader, final String path) {
        return readDataFrameIndex(reader, AnnDataPath.fromString(path));
    }

    /**
     * Reads the index of the data frame at the given path.
     *
     * @param reader The N5 reader to use.
     * @param path The path to the data frame.
     * @return The index of the data frame as a list of strings.
     */
    public static List<String> readDataFrameIndex(final N5Reader reader, final AnnDataPath path) {
        final AnnDataPath indexPath = getDataFrameIndexPath(reader, path);
        return readStringArray(reader, indexPath);
    }

    /**
     * Returns the field type of the data at the given path.
     *
     * @param reader The N5 reader to use.
     * @param path The path to the data.
     * @return The field type of the data.
     */
    public static AnnDataFieldType getFieldType(final N5Reader reader, final String path) {
        return getFieldType(reader, AnnDataPath.fromString(path));
    }

    /**
     * Returns the field type of the data at the given path.
     *
     * @param reader The N5 reader to use.
     * @param path The path to the data.
     * @return The field type of the data.
     */
    public static AnnDataFieldType getFieldType(final N5Reader reader, final AnnDataPath path) {
        final String encoding = reader.getAttribute(path.toString(), ENCODING_KEY, String.class);
        final String version = reader.getAttribute(path.toString(), VERSION_KEY, String.class);
        return AnnDataFieldType.fromString(encoding, version);
    }

    /**
     * Creates a mapping of the datasets in the given path to their field types.
     *
     * @param reader The N5 reader to use.
     * @param path The path to be investigated.
     * @return A mapping of the present datasets to their field types.
     */
    public static Map<AnnDataPath, AnnDataFieldType> listDatasets(final N5Reader reader, final String path) {
        return listDatasets(reader, AnnDataPath.fromString(path));
    }

    /**
     * Creates a mapping of the datasets in the given path to their field types.
     *
     * @param reader The N5 reader to use.
     * @param path The path to be investigated.
     * @return A mapping of the present datasets to their field types.
     */
    public static Map<AnnDataPath, AnnDataFieldType> listDatasets(final N5Reader reader, final AnnDataPath path) {
        if (! reader.exists(path.toString())) {
            return new HashMap<>();
        }

        final AnnDataFieldType parentType = getFieldType(reader, path);
        if (parentType != AnnDataFieldType.MAPPING && parentType != AnnDataFieldType.DATA_FRAME) {
            return new HashMap<>();
        }

        final Map<AnnDataPath, AnnDataFieldType> datasets = new HashMap<>();
        final String[] subfields = reader.list(path.toString());
        for (final String field : subfields) {
            final AnnDataPath subPath = path.append(field);
            final AnnDataFieldType type = getFieldType(reader, subPath);
            datasets.put(subPath, type);
        }
        return datasets;
    }

    /**
     * Reads a numerical array (dense or sparse) from the given path.
     *
     * @param reader The N5 reader to use.
     * @param path The path to the array.
     * @return The array as an Img.
     * @throws AnnDataException If the array is not a numerical array.
     */
    public static <T extends NativeType<T> & RealType<T>>
    Img<T> readNumericalArray(final N5Reader reader, final String path) {
        return readNumericalArray(reader, AnnDataPath.fromString(path));
    }

    /**
     * Reads a numerical array (dense or sparse) from the given path.
     *
     * @param reader The N5 reader to use.
     * @param path The path to the array.
     * @return The array as an Img.
     * @throws AnnDataException If the array is not a numerical array.
     */
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

    /**
     * Reads a string array (string or categorical type) from the given path.
     *
     * @param reader The N5 reader to use.
     * @param path The path to the array.
     * @return The array as a list of strings.
     * @throws AnnDataException If the array is not a string array.
     */
    public static List<String> readStringArray(final N5Reader reader, final String path) {
        return readStringArray(reader, AnnDataPath.fromString(path));
    }

    /**
     * Reads a string array (string or categorical type) from the given path.
     *
     * @param reader The N5 reader to use.
     * @param path The path to the array.
     * @return The array as a list of strings.
     * @throws AnnDataException If the array is not a string array.
     */
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

    /**
     * Returns the names of the datasets in the data frame at the given path
     * as given in the "column-order" attribute of the data frame group.
     *
     * @param reader The N5 reader to use.
     * @param path The path to the data frame.
     * @return The names of the datasets.
     */
    public static Set<String> getDataFrameDatasetNames(final N5Reader reader, final String path) {
        return getDataFrameDatasetNames(reader, AnnDataPath.fromString(path));
    }

    /**
     * Returns the names of the datasets in the data frame at the given path
     * as given in the "column-order" attribute of the data frame group.
     *
     * @param reader The N5 reader to use.
     * @param path The path to the data frame.
     * @return The names of the datasets.
     */
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

    /**
     * Writes a numerical array (dense or sparse) to the given path. If the
     * given type doesn't match the array type, the array is converted to the
     * given type.
     *
     * @param data The data to write.
     * @param writer The N5 writer to use.
     * @param path The path to write to.
     * @param options The options to use.
     * @param type The field type of the data.
     * @throws IOException If an error occurs while writing.
     */
    public static <T extends NativeType<T> & RealType<T>> void writeNumericalArray(
            final RandomAccessibleInterval<T> data,
            final N5Writer writer,
            final String path,
            final N5Options options,
            final AnnDataFieldType type) throws IOException {

        writeNumericalArray(data, writer, AnnDataPath.fromString(path), options, type);
    }

    /**
     * Writes a numerical array (dense or sparse) to the given path. If the
     * given type doesn't match the array type, the array is converted to the
     * given type.
     *
     * @param data The data to write.
     * @param writer The N5 writer to use.
     * @param path The path to write to.
     * @param options The options to use.
     * @param type The field type of the data.
     * @throws IOException If an error occurs while writing.
     */
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
                if (options.hasExecutorService()) {
                    N5Utils.save(data, writer, path.toString(), options.blockSize(), options.compression());
                } else {
                    N5Utils.save(data, writer, path.toString(), options.blockSize(), options.compression(), options.executorService());
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

    /**
     * Creates a data frame at the given path.
     *
     * @param index The index of the data frame.
     * @param writer The N5 writer to use.
     * @param path The path to create the data frame at.
     * @param options The options to use.
     */
    public static void createDataFrame(final List<String> index, final N5Writer writer, final String path, final N5Options options) {
        createDataFrame(index, writer, AnnDataPath.fromString(path), options);
    }

    /**
     * Creates a data frame at the given path.
     *
     * @param index The index of the data frame.
     * @param writer The N5 writer to use.
     * @param path The path to create the data frame at.
     * @param options The options to use.
     */
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

    /**
     * Writes a string array to the given path. If the given type is categorical,
     * the array is converted on the fly.
     *
     * @param data The data to write.
     * @param writer The N5 writer to use.
     * @param path The path to write to.
     * @param options The options to use.
     * @param type The field type of the data.
     */
    public static void writeStringArray(final List<String> data, final N5Writer writer, final String path, final N5Options options, final AnnDataFieldType type) {
        writeStringArray(data, writer, AnnDataPath.fromString(path), options, type);
    }

    /**
     * Writes a string array to the given path. If the given type is categorical,
     * the array is converted on the fly.
     *
     * @param data The data to write.
     * @param writer The N5 writer to use.
     * @param path The path to write to.
     * @param options The options to use.
     * @param type The field type of the data.
     */
    public static void writeStringArray(final List<String> data, final N5Writer writer, final AnnDataPath path, final N5Options options, final AnnDataFieldType type) {
        checker.check(writer, path, type, new long[] {data.size()});
        AnnDataFieldType.ensureStringArray(type);

        switch (type) {
            case STRING_ARRAY:
                N5StringUtils.save(data, writer, path.toString(), options.blockSize(), options.compression());
                setFieldType(writer, path, type);
                break;
            case CATEGORICAL_ARRAY:
                writeCategoricalList(data, writer, path, options);
        }
        conditionallyAddToDataFrame(writer, path);
    }

    /**
     * Creates a mapping at the given path.
     *
     * @param writer The N5 writer to use.
     * @param path The path to create the mapping at.
     */
    public static void createMapping(final N5Writer writer, final String path) {
        createMapping(writer, AnnDataPath.fromString(path));
    }

    /**
     * Creates a mapping at the given path.
     *
     * @param writer The N5 writer to use.
     * @param path The path to create the mapping at.
     */
    public static void createMapping(final N5Writer writer, final AnnDataPath path) {
        writer.createGroup(path.toString());
        setFieldType(writer, path, AnnDataFieldType.MAPPING);
    }
}
