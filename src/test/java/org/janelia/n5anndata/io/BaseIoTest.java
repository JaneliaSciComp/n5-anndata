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

import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrWriter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Named;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Named.named;

public class BaseIoTest {
	protected static Path testDirectoryPath;
	protected static ExecutorService EXECUTOR;
	protected static final Compression COMPRESSION = new GzipCompression();
	protected static final N5Options ARRAY_OPTIONS = new N5Options(new int[]{2}, COMPRESSION, EXECUTOR);
	protected static final N5Options MATRIX_OPTIONS = new N5Options(new int[]{2, 2}, new GzipCompression(), EXECUTOR);


	@BeforeAll
	public static void setup() throws IOException {
		final Path currentDirectory = Paths.get("").toAbsolutePath();
		testDirectoryPath = Files.createTempDirectory(currentDirectory, "tmp_test_dir");
		EXECUTOR = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	}

	@AfterAll
	public static void cleanup() throws IOException {
		final File dir = new File(testDirectoryPath.toString());
		if (dir.exists()) {
			deleteRecursively(testDirectoryPath);
		}
		EXECUTOR.shutdown();
	}

	@AfterEach
	public void emptyTmpDirectory() throws IOException {
		final File dir = new File(testDirectoryPath.toString());
		if (dir.exists()) {
			final String[] contents = Objects.requireNonNull(dir.list());
			for (final String fileOrDirPath : contents) {
				final Path fileOrDir = Paths.get(testDirectoryPath.toString(), fileOrDirPath);
				deleteRecursively(fileOrDir);
			}
		}
	}

	protected static void deleteRecursively(final Path path) throws IOException {
		try (final Stream<Path> contents = Files.walk(path)) {
			final Boolean allSucceeded = contents
					.sorted(Comparator.reverseOrder())
					.map(Path::toFile)
					.map(File::delete)
					.reduce(Boolean::logicalAnd).orElse(true);
			if (!allSucceeded) {
				throw new IOException("Could not delete all contents of " + path);
			}
		} catch (final IOException e) {
			throw new IOException("Could not delete all contents of " + path, e);
		}
	}


	protected static <T> void assertImgEquals(final RandomAccessibleInterval<T> expected, final Img<T> actual) {
		assertArrayEquals(expected.dimensionsAsLongArray(), actual.dimensionsAsLongArray(), "Dimensions do not match.");
		final Cursor<T> cursor = actual.cursor();
		while (cursor.hasNext()) {
			cursor.fwd();
			final T actualValue = cursor.get();
			final T expectedValue = expected.getAt(cursor);
			Assertions.assertEquals(expectedValue, actualValue, "Values do not match at " + Arrays.toString(cursor.positionAsLongArray()));
		}

	}

	protected static List<Named<Supplier<N5Writer>>> datasetsWithDifferentBackends() {
		final String basePath = testDirectoryPath.toString();
		return Arrays.asList(
				named("HDF5", () -> new N5HDF5Writer(Paths.get(basePath, "data.h5ad").toString())),
				named("Zarr", () -> new N5ZarrWriter(Paths.get(basePath, "data.zarr").toString())),
				named("N5", () -> new N5FSWriter(Paths.get(basePath, "data.n5ad").toString())));
	}
}
