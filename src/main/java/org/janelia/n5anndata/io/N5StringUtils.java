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
		int remaining = size;
		for (int i = 0; i < nChunks; ++i) {
			final DataBlock<?> chunk = reader.readBlock(path, attributes, i);
			data.addAll(relevantPart(chunk, Math.min(remaining, chunkSize)));
			remaining -= chunkSize;
		}
		return data;
	}


	private static List<String> relevantPart(final DataBlock<?> chunk, final int nElements) {
		final String[] array = Arrays.copyOfRange((String[]) chunk.getData(), 0, nElements);
		return Arrays.asList(array);
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
		final DatasetAttributes attributes = new DatasetAttributes(new long[] { size }, blockSize, DataType.STRING, compression);
		writer.createDataset(path, attributes);

		final int chunkSize = blockSize[0];
		final int nChunks = (int) Math.ceil((double) size / chunkSize);

		final String[] chunkData = new String[chunkSize];
		final boolean isZarr = writer instanceof N5ZarrWriter;
		for (int i = 0; i < nChunks - 1; ++i) {
			copy(data, i * chunkSize, (i + 1) * chunkSize, chunkData);
			final DataBlock<?> chunk;
			if (isZarr) {
				chunk = new ZarrStringDataBlock(new int[] { chunkSize }, new long[] { i }, chunkData);
			} else{
				chunk = new StringDataBlock(new int[] { chunkSize }, new long[] { i }, chunkData);
			}
			writer.writeBlock(path, attributes, chunk);
		}

		// take care of the last chunk (padded for zarr, truncated for hdf5, doesn't matter for n5)
		final DataBlock<?> chunk;
		final int start = (nChunks - 1) * chunkSize;
		copy(data, start, size, chunkData);
		if (isZarr) {
			for (int i = size - start; i < chunkSize; ++i) {
				chunkData[i] = "";
			}
			chunk = new ZarrStringDataBlock(new int[] { chunkSize }, new long[] { nChunks - 1 }, chunkData);
		} else {
			final String[] truncatedChunk = Arrays.copyOf(chunkData, size - start);
			chunk = new StringDataBlock(new int[] { size - start }, new long[] { nChunks - 1 }, truncatedChunk);
		}
		writer.writeBlock(path, attributes, chunk);
	}

	private static <T> void copy(final List<T> src, final int start, final int end, final T[] dest) {
		final int n = end - start;
		for (int i = 0; i < n; ++i) {
			dest[i] = src.get(start + i);
		}
	}
}
