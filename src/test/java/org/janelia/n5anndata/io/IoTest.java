package org.janelia.n5anndata.io;

import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrWriter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Named.named;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class IoTest {

	private static Path testDirectoryPath;
	private static ExecutorService executorService;

	@BeforeAll
	public static void setup() throws IOException {
		final Path currentDirectory = Paths.get("").toAbsolutePath();
		testDirectoryPath = Files.createTempDirectory(currentDirectory, "tmp_test_dir");
		executorService = Executors.newSingleThreadExecutor();
	}

	@AfterAll
	public static void cleanup() throws IOException {
		final File dir = new File(testDirectoryPath.toString());
		if (dir.exists()) {
			deleteRecursively(testDirectoryPath);
		}
		executorService.shutdown();
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

	@ParameterizedTest
	@MethodSource("provideDatasetNames")
	public void created_anndata_is_valid(final Supplier<N5Writer> writerSupplier) {
		try (final N5Writer writer = writerSupplier.get()) {
			AnnDataUtils.initializeAnnData(writer);
			assertTrue(AnnDataUtils.isValidAnnData(writer));
		} catch (final Exception e) {
			fail("Could not write / read file: ", e);
		}
	}

	/**
	 To run this test, you need to have python installed and the following packages
	 available: anndata, numpy, scipy, pandas, zarr, h5py.
	 - If run from an IDE, you need to add the python path to the environment variables.
	 - If run from the command line, you need to have python in your path (e.g. the
	   correct conda environment is activated).
	 If the test is not able to run, it will be skipped.
	 */
	@Test
	public void consistency_with_python() {
		boolean canExecutePython = true;
		try {
			final Process process = Runtime.getRuntime().exec("python src/test/python/generate_test_dataset.py " + testDirectoryPath.toString());
			process.waitFor();
			process.destroy();
		} catch (final IOException | InterruptedException e) {
			canExecutePython = false;
		}
		Assumptions.assumeTrue(canExecutePython, "Could not execute python script, consistency test skipped.");
	}

	protected static List<Named<Supplier<N5Writer>>> provideDatasetNames() {
		final String basePath = testDirectoryPath.toString();
		return Arrays.asList(
				named("HDF5", () -> new N5HDF5Writer(basePath + "data.h5ad")),
				named("Zarr", () -> new N5ZarrWriter(basePath + "data.zarr")),
				named("N5", () -> new N5FSWriter(basePath + "data.n5ad")));
	}

	private static void deleteRecursively(final Path path) throws IOException {
		Files.walk(path)
				.sorted(Comparator.reverseOrder())
				.map(Path::toFile)
				.forEach(File::delete);
	}
}
