package org.janelia.n5anndata.io;

import org.janelia.saalfeldlab.n5.DataBlock;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.StringDataBlock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class N5StringUtils {

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

	public static void save(final List<String> data, final N5Writer writer, final String path, final int[] blockSize) {
		if (blockSize.length != 1 && (blockSize.length == 2 && blockSize[1] == 1)) {
			throw new IllegalArgumentException("Block size '" + Arrays.toString(blockSize) + "' is not suitable for a 1D dataset");
		}

		final int size = data.size();
		final DatasetAttributes attributes = new DatasetAttributes(new long[] {size}, blockSize, DataType.STRING, new GzipCompression());
		writer.createDataset(path, attributes);

		final int chunkSize = blockSize[0];
		final int nChunks = (int) Math.ceil((double) size / chunkSize);

		final String[] chunkData = new String[chunkSize];
		for (int i = 0; i < nChunks - 1; ++i) {
			copy(data, i * chunkSize, (i + 1) * chunkSize, chunkData);
			final DataBlock<?> chunk = new StringDataBlock(new int[] {chunkSize}, new long[] {i}, chunkData);
			writer.writeBlock(path, attributes, chunk);
		}
		final int remainderSize = size - (nChunks - 1) * chunkSize;
		final String[] remainder = new String[remainderSize];
		copy(data, (nChunks - 1) * chunkSize, size, remainder);
		final DataBlock<?> chunk = new StringDataBlock(new int[] {remainderSize}, new long[] {nChunks - 1}, remainder);
		writer.writeBlock(path, attributes, chunk);
	}

	private static <T> void copy(final List<T> src, final int start, final int end, final T[] dest) {
		final int n = end - start;
		for (int i = 0; i < n; ++i) {
			dest[i] = src.get(start + i);
		}
	}
}
