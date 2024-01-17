package org.janelia.n5anndata.io;

import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.DataBlock;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.StringDataBlock;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrWriter;
import org.janelia.saalfeldlab.n5.zarr.ZarrStringDataBlock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * This class provides utility methods for working with string datasets in N5.
 * <p>
 * This is necessary, since N5 only supports reading and writing of individual
 * blocks of a dataset. For arrays of {@link net.imglib2.type.NativeType NativeTypes},
 * this is handled by {@link org.janelia.saalfeldlab.n5.imglib2.N5Utils N5Utils}.
 * However, strings don't fall into this category.
 * <p>
 * To emphasize that only 1D string datasets are supported and to make working with
 * those datasets easy, the methods in this return and expect {@link List Lists}.
 * Also, parallel writing by means of an {@link java.util.concurrent.ExecutorService ExecutorService}
 * is not supported.
 *
 * @author Michael Innerberger
 */
public class N5StringUtils {

	/**
	 * Opens an N5 string dataset and returns its data as a list of strings.
	 *
	 * @param reader The N5 reader to use.
	 * @param path The path to the dataset.
	 * @return The data of the dataset as a list of strings.
	 * @throws IllegalArgumentException If the dataset is not a 1D string dataset or if it is too large (more than {@link Integer#MAX_VALUE} elements).
	 */
	public static List<String> open(final N5Reader reader, final String path) {
		final DatasetAttributes attributes = reader.getDatasetAttributes(path);
		if (attributes.getNumDimensions() != 1 || attributes.getDataType() != DataType.STRING) {
			throw new IllegalArgumentException("Dataset '" + path + "' is not a 1D string dataset");
		}
		final long longSize = attributes.getDimensions()[0];
		if (longSize > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Dataset '" + path + "' is too large");
		}

		final int size = (int) longSize;
		final int chunkSize = attributes.getBlockSize()[0];
		final int nChunks = (int) Math.ceil((double) size / chunkSize);

		final List<String> data = new ArrayList<>(size);
		for (int i = 0; i < nChunks; ++i) {
			final DataBlock<?> chunk = reader.readBlock(path, attributes, i);
			data.addAll(Arrays.asList((String[]) chunk.getData()));
		}
		return data;
	}

	/**
	 * Saves a list of strings as an N5 string dataset and compresses it with gzip.
	 *
	 * @param data The data to save.
	 * @param writer The N5 writer to use.
	 * @param path The path to the dataset.
	 * @param blockSize The block size to use.
	 */
	public static void save(final List<String> data, final N5Writer writer, final String path, final int[] blockSize) {
		save(data, writer, path, blockSize, new GzipCompression());
	}

	/**
	 * Saves a list of strings as an N5 string dataset with a specific compression.
	 *
	 * @param data The data to save.
	 * @param writer The N5 writer to use.
	 * @param path The path to the dataset.
	 * @param blockSize The block size to use.
	 * @param compression The compression to use.
	 */
	public static void save(final List<String> data, final N5Writer writer, final String path, final int[] blockSize, final Compression compression) {
		if (blockSize.length != 1 && (blockSize.length == 2 && blockSize[1] == 1)) {
			throw new IllegalArgumentException("Block size '" + Arrays.toString(blockSize) + "' is not suitable for a 1D dataset");
		}

		final int size = data.size();
		final DatasetAttributes attributes = new DatasetAttributes(new long[] {size}, blockSize, DataType.STRING, compression);
		writer.createDataset(path, attributes);

		final int chunkSize = blockSize[0];
		final int nChunks = (int) Math.ceil((double) size / chunkSize);

		final boolean isZarr = writer instanceof N5ZarrWriter;
		final String[] chunkData = new String[chunkSize];
		for (int i = 0; i < nChunks - 1; ++i) {
			copy(data, i * chunkSize, (i + 1) * chunkSize, chunkData);
			final DataBlock<?> chunk = isZarr ? new ZarrStringDataBlock(new int[] {chunkSize}, new long[] {i}, chunkData)
					: new StringDataBlock(new int[] {chunkSize}, new long[] {i}, chunkData);
			writer.writeBlock(path, attributes, chunk);
		}
		final int remainderSize = size - (nChunks - 1) * chunkSize;
		final String[] remainder = new String[remainderSize];
		copy(data, (nChunks - 1) * chunkSize, size, remainder);
		final DataBlock<?> chunk = isZarr ? new ZarrStringDataBlock(new int[] {chunkSize}, new long[] {nChunks - 1}, remainder)
				: new StringDataBlock(new int[] {remainderSize}, new long[] {nChunks - 1}, remainder);
		writer.writeBlock(path, attributes, chunk);
	}

	private static <T> void copy(final List<T> src, final int start, final int end, final T[] dest) {
		final int n = end - start;
		for (int i = 0; i < n; ++i) {
			dest[i] = src.get(start + i);
		}
	}
}
