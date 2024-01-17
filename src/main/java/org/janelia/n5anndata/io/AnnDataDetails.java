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
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.janelia.n5anndata.io.AnnDataUtils.getDataFrameDatasetNames;
import static org.janelia.n5anndata.io.AnnDataUtils.getFieldType;


/**
 * Utilities for reading and writing AnnData datasets. In contrast to
 * {@link AnnDataUtils}, this class provides I/O methods for specific
 * AnnDataTypes and does not perform any type checking.
 * <p>
 * For internal use only.
 *
 * @author Michael Innerberger
 */
class AnnDataDetails {
	// encoding metadata
	static final String ENCODING_KEY = "encoding-type";
	static final String VERSION_KEY = "encoding-version";
	static final String SHAPE_KEY = "shape";

	// dataframe metadata
	static final String INDEX_KEY = "_index";
	static final String COLUMN_ORDER_KEY = "column-order";
	static final String DEFAULT_INDEX_DIR = "_index";

	// sparse metadata
	static final String DATA_DIR = "data";
	static final String INDICES_DIR = "indices";
	static final String INDPTR_DIR = "indptr";

	// categorical metadata
	static final String ORDERED_KEY = "ordered";
	static final String CATEGORIES_DIR = "categories";
	static final String CODES_DIR = "codes";


	/**
	 * Reads a sparse array from the given path. The sparse array is represented
	 * by a group that contains three datasets: "data", "indices", and "indptr".
	 *
	 * @param reader The N5 reader to use.
	 * @param path The path to the array.
	 * @param constructor The constructor to use for creating the sparse array (CSR or CSC).
	 * @return The sparse array.
	 */
	static <T extends NativeType<T> & RealType<T>, I extends NativeType<I> & IntegerType<I>>
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

	/**
	 * Reads a categorical list from the given path. The categorical list is
	 * represented by a group that contains two datasets: "categories" (a string
	 * array of unique category names) and "codes" (an integer array of category
	 * indices).
	 *
	 * @param reader The N5 reader to use.
	 * @param path The path to the list.
	 * @return The denormalized categorical list.
	 */
	static List<String> readCategoricalList(final N5Reader reader, final AnnDataPath path) {
		final List<String> categoryNames = N5StringUtils.open(reader, path.append(CATEGORIES_DIR).toString());
		final Img<? extends IntegerType<?>> categories = N5Utils.open(reader, path.append(CODES_DIR).toString());

		final int nElements = (int) categories.size();
		final List<String> denormalizedNames = new ArrayList<>(nElements);

		for (final IntegerType<?> category : categories) {
			denormalizedNames.add(categoryNames.get(category.getInteger()));
		}
		return denormalizedNames;
	}

	/**
	 * Sets the field type metadata of the data at the given path.
	 *
	 * @param writer The N5 writer to use.
	 * @param path The path to the data.
	 * @param type The field type to set.
	 */
	static void setFieldType(final N5Writer writer, final AnnDataPath path, final AnnDataFieldType type) {
		writer.setAttribute(path.toString(), ENCODING_KEY, type.getEncoding());
		writer.setAttribute(path.toString(), VERSION_KEY, type.getVersion());
	}

	/**
	 * If the given path is part of a dataframe, adds the column name to the
	 * column-order metadata.
	 *
	 * @param writer The N5 writer to use.
	 * @param path The path to the dataset.
	 */
	static void conditionallyAddToDataFrame(final N5Writer writer, final AnnDataPath path) {
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

	/**
	 * Writes a sparse array to the given path and converts between CSR and CSC
	 * if necessary.
	 *
	 * @param writer The N5 writer to use.
	 * @param path The path to write to.
	 * @param data The data to write.
	 * @param options The options to use.
	 * @param type The field type of the data.
	 * @throws ExecutionException If an error occurs during execution.
	 * @throws InterruptedException If the thread is interrupted.
	 */
	static <T extends NativeType<T> & RealType<T>> void writeSparseArray(
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
			if (type == AnnDataFieldType.CSR_MATRIX) {
				sparse = CsrMatrix.from(data);
			} else {
				sparse = CscMatrix.from(data);
			}
		}

		writer.createGroup(path.toString());
		final int[] blockSize = options.blockSizeTo1D();
		if (options.hasExecutorService()) {
			N5Utils.save(sparse.getDataArray(), writer, path.append(DATA_DIR).toString(), blockSize, options.compression());
			N5Utils.save(sparse.getIndicesArray(), writer, path.append(INDICES_DIR).toString(), blockSize, options.compression());
			N5Utils.save(sparse.getIndexPointerArray(), writer, path.append(INDPTR_DIR).toString(), blockSize, options.compression());
		} else {
			N5Utils.save(sparse.getDataArray(), writer, path.append(DATA_DIR).toString(), blockSize, options.compression(), options.executorService());
			N5Utils.save(sparse.getIndicesArray(), writer, path.append(INDICES_DIR).toString(), blockSize, options.compression(), options.executorService());
			N5Utils.save(sparse.getIndexPointerArray(), writer, path.append(INDPTR_DIR).toString(), blockSize, options.compression(), options.executorService());
		}

		final long[] shape = flip(data.dimensionsAsLongArray());
		writer.setAttribute(path.toString(), SHAPE_KEY, shape);
	}

	/**
	 * Writes a categorical list to the given path. The categorical list is
	 * represented by a group that contains two datasets: "categories" (a string
	 * array of unique category names) and "codes" (an integer array of category
	 * indices).
	 *
	 * @param data The data to write.
	 * @param writer The N5 writer to use.
	 * @param path The path to write to.
	 * @param options The options to use.
	 */
	static void writeCategoricalList(final List<String> data, final N5Writer writer, final AnnDataPath path, final N5Options options) {
		final List<String> uniqueElements = data.stream().distinct().collect(Collectors.toList());
		final Map<String, Integer> elementToCode = IntStream.range(0, uniqueElements.size()).boxed().collect(Collectors.toMap(uniqueElements::get, i -> i));
		final Img<IntType> categories = ArrayImgs.ints(data.stream().mapToInt(elementToCode::get).toArray(), data.size());

		writer.createGroup(path.toString());
		setFieldType(writer, path, AnnDataFieldType.CATEGORICAL_ARRAY);
		writer.setAttribute(path.toString(), ORDERED_KEY, false);

		N5StringUtils.save(uniqueElements, writer, path.append(CATEGORIES_DIR).toString(), options.blockSize(), options.compression());
		N5Utils.save(categories, writer, path.append(CODES_DIR).toString(), options.blockSize(), options.compression());
	}

	/**
	 * Checks if the given path is part of a dataframe.
	 *
	 * @param reader The N5 reader to use.
	 * @param path The path to check.
	 * @return True if the path is part of a dataframe, false otherwise.
	 */
	static boolean isDataFrame(final N5Reader reader, final AnnDataPath path) {
		final AnnDataFieldType type = getFieldType(reader, path);
		final AnnDataPath indexPath = getDataFrameIndexPath(reader, path);
		if (indexPath == null) return false;
		return type == AnnDataFieldType.DATA_FRAME && reader.exists(indexPath.toString());
	}

	/**
	 * Returns the path to the index of the dataframe at the given path. If the
	 * "index" metadata is present, the path to the index is its value. If not,
	 * the path to the index is the default ("_index").
	 *
	 * @param reader The N5 reader to use.
	 * @param path The path to the dataframe.
	 * @return The path to the index of the dataframe.
	 */
	static AnnDataPath getDataFrameIndexPath(final N5Reader reader, final AnnDataPath path) {
		final String indexDir;
		indexDir = reader.getAttribute(path.toString(), INDEX_KEY, String.class);
		if (indexDir == null) {
			return path.append(DEFAULT_INDEX_DIR);
		} else {
			return path.append(indexDir);
		}
	}

	static long[] flip(final long[] array) {
		if (array.length == 1) {
			return array;
		} else if (array.length == 2) {
			return new long[]{array[1], array[0]};
		}
		throw new IllegalArgumentException("Array must have length 1 or 2.");
	}


	/**
	 * Nested interface used to make reading sparse arrays more generic.
 	 */
	@FunctionalInterface
	interface SparseArrayConstructor<
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
