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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Named.named;

public class BaseIoTest {
	protected static Path testDirectoryPath;
	protected static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
	protected static final Compression COMPRESSION = new GzipCompression();
	protected static final N5Options ARRAY_OPTIONS = new N5Options(new int[]{2}, COMPRESSION, EXECUTOR);
	protected static final N5Options MATRIX_OPTIONS = new N5Options(new int[]{2, 2}, new GzipCompression(), EXECUTOR);


	@BeforeAll
	public static void setup() throws IOException {
		final Path currentDirectory = Paths.get("").toAbsolutePath();
		testDirectoryPath = Files.createTempDirectory(currentDirectory, "tmp_test_dir");
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
		Files.walk(path)
				.sorted(Comparator.reverseOrder())
				.map(Path::toFile)
				.forEach(File::delete);
	}


	protected static <T> void assertEquals(final RandomAccessibleInterval<T> expected, final Img<T> actual) {
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
