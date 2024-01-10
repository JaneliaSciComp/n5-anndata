package org.janelia.n5anndata.io;

import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrReader;
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
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Named.named;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class IoTest {

	private static Path testDirectoryPath;
	private static ExecutorService executorService;

	private static final List<String> OBS_NAMES = Arrays.asList("a", "b", "cd", "efg");
	private static final List<String> VAR_NAMES = Arrays.asList("cell1", "cell2", "cell3");

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
	public void reading_and_writing_strings_from_list(final Supplier<N5Writer> writerSupplier) {
		final List<String> expected = Arrays.asList("", "a", "b", "cd", "efg", ":-þ");
		try (final N5Writer writer = writerSupplier.get()) {
			N5StringUtils.save(expected, writer, "test", new int[] {4});
			final List<String> actual = N5StringUtils.open(writer, "/test");
			assertArrayEquals(expected.toArray(), actual.toArray());
		} catch (final Exception e) {
			fail("Could not write / read file: ", e);
		}
	}

	@ParameterizedTest
	@MethodSource("provideDatasetNames")
	public void created_anndata_is_valid(final Supplier<N5Writer> writerSupplier) {
		final N5Options options = new N5Options(new int[] {2}, new GzipCompression(), executorService);
		try (final N5Writer writer = writerSupplier.get()) {
			AnnDataUtils.initializeAnnData(OBS_NAMES, VAR_NAMES, writer, options);
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
	public void consistency_with_python()
			throws ExecutionException, InterruptedException, IOException {
		final List<String> extensions = Arrays.asList(".h5ad", ".zarr");
		for (final String ext : extensions) {
			final String pythonDataset = Paths.get(testDirectoryPath.toString(), "data_python") + ext;
			final String javaDataset = Paths.get(testDirectoryPath.toString(), "data_java") + ext;

			final boolean canExecutePython = executePythonScript("generate_test_dataset.py", pythonDataset);
			Assumptions.assumeTrue(canExecutePython, "Could not execute python script, consistency test skipped.");

			resaveDataset(pythonDataset, javaDataset);

			executePythonScript("validate_anndata.py", javaDataset);
			deleteRecursively(Paths.get(pythonDataset));
			deleteRecursively(Paths.get(javaDataset));
		}
	}

	private static boolean executePythonScript(final String script, final String args) {
		final String scriptBasePath = Paths.get("src", "test", "python").toString();
		final String scriptPath = Paths.get(scriptBasePath, script).toString();
		final String cmd = "python " + scriptPath + " " + args;
		try {
			final Process process = Runtime.getRuntime().exec(cmd);
			new BufferedReader(new InputStreamReader(process.getErrorStream())).lines().forEach(System.out::println);
			if (process.waitFor() != 0) {
				throw new RuntimeException("Python script failed: " + cmd);
			}
			process.destroy();
			return true;
		} catch (final IOException | InterruptedException e) {
			return false;
		}
	}

	private static void resaveDataset(final String pythonDataset, final String javaDataset)
			throws ExecutionException, InterruptedException, IOException {
		final N5Reader reader = getReaderFor(pythonDataset);
		final N5Writer writer = getWriterFor(javaDataset);

		final Img<DoubleType> X = AnnDataUtils.readNumericalArray(reader, AnnDataField.X, "");
		N5Options options = getOptionsFor(reader, AnnDataField.X.getPath(""));
		AnnDataUtils.writeArray(X, writer, AnnDataField.X, "", options, AnnDataFieldType.CSR_MATRIX);

		String path = "rnd";
		final Img<DoubleType> csr = AnnDataUtils.readNumericalArray(reader, AnnDataField.OBSP, path);
		options = getOptionsFor(reader, AnnDataField.OBSP.getPath(path));
		AnnDataUtils.writeArray(csr, writer, AnnDataField.OBS, path, options, AnnDataFieldType.CSR_MATRIX);

		path = "rnd";
		final Img<ShortType> csc = AnnDataUtils.readNumericalArray(reader, AnnDataField.VARP, path);
		options = getOptionsFor(reader, AnnDataField.VARP.getPath(path));
		AnnDataUtils.writeArray(csc, writer, AnnDataField.VARP, path, options, AnnDataFieldType.CSC_MATRIX);

		path = "_index";
		final List<String> obs_names = AnnDataUtils.readStringArray(reader, AnnDataField.OBS, path);
		options = getOptionsFor(reader, AnnDataField.OBS.getPath(path));
		AnnDataUtils.writeStringArray(obs_names, writer, AnnDataField.OBS, path, options, AnnDataFieldType.STRING_ARRAY);

		path = "_index";
		final List<String> var_names = AnnDataUtils.readStringArray(reader, AnnDataField.VAR, path);
		options = getOptionsFor(reader, AnnDataField.VAR.getPath(path));
		AnnDataUtils.writeStringArray(var_names, writer, AnnDataField.VAR, path, options, AnnDataFieldType.STRING_ARRAY);

		path = "cell_type";
		final List<String> cell_type = AnnDataUtils.readStringArray(reader, AnnDataField.OBS, path);
		options = getOptionsFor(reader, AnnDataField.OBS.getPath(path));
		AnnDataUtils.writeStringArray(cell_type, writer, AnnDataField.OBS, path, options, AnnDataFieldType.CATEGORICAL_ARRAY);

		path = "gene_stuff1";
		final Img<IntType> genes1 = AnnDataUtils.readNumericalArray(reader, AnnDataField.VAR, path);
		options = getOptionsFor(reader, AnnDataField.VAR.getPath(path));
		AnnDataUtils.writeArray(genes1, writer, AnnDataField.VAR, path, options, AnnDataFieldType.DENSE_ARRAY);

		path = "gene_stuff2";
		final Img<LongType> genes2 = AnnDataUtils.readNumericalArray(reader, AnnDataField.VAR, path);
		options = getOptionsFor(reader, AnnDataField.VAR.getPath(path));
		AnnDataUtils.writeArray(genes2, writer, AnnDataField.VAR, path, options, AnnDataFieldType.DENSE_ARRAY);

		path = "X_umap";
		final Img<DoubleType> umap1 = AnnDataUtils.readNumericalArray(reader, AnnDataField.OBS, path);
		options = getOptionsFor(reader, AnnDataField.OBS.getPath(path));
		AnnDataUtils.writeArray(umap1, writer, AnnDataField.OBS, path, options, AnnDataFieldType.DENSE_ARRAY);

		path = "X_umap";
		final Img<DoubleType> umap2 = AnnDataUtils.readNumericalArray(reader, AnnDataField.VARM, path);
		options = getOptionsFor(reader, AnnDataField.VARM.getPath(path));
		AnnDataUtils.writeArray(umap2, writer, AnnDataField.VARM, path, options, AnnDataFieldType.DENSE_ARRAY);

		path = "random";
		final Img<DoubleType> uns = AnnDataUtils.readNumericalArray(reader, AnnDataField.UNS, path);
		options = getOptionsFor(reader, AnnDataField.UNS.getPath(path));
		AnnDataUtils.writeArray(uns, writer, AnnDataField.UNS, path, options, AnnDataFieldType.DENSE_ARRAY);

		path = "log";
		final Img<FloatType> log = AnnDataUtils.readNumericalArray(reader, AnnDataField.LAYERS, path);
		options = getOptionsFor(reader, AnnDataField.LAYERS.getPath(path));
		AnnDataUtils.writeArray(log, writer, AnnDataField.LAYERS, path, options, AnnDataFieldType.CSR_MATRIX);
	}

	private static N5Reader getReaderFor(final String dataset) {
		if (dataset.endsWith(".h5ad")) {
			return new N5HDF5Reader(dataset);
		} else if (dataset.endsWith(".zarr")) {
			return new N5ZarrReader(dataset);
		} else {
			throw new IllegalArgumentException("Unknown file extension: " + dataset);
		}
	}

	private static N5Writer getWriterFor(final String dataset) {
		if (dataset.endsWith(".h5ad")) {
			return new N5HDF5Writer(dataset);
		} else if (dataset.endsWith(".zarr")) {
			return new N5ZarrWriter(dataset);
		} else {
			throw new IllegalArgumentException("Unknown file extension: " + dataset);
		}
	}

	private static N5Options getOptionsFor(final N5Reader reader, final String path) {
		final DatasetAttributes attributes = reader.getDatasetAttributes(path);
		final int[] blockSize = attributes.getBlockSize();
		final Compression compression = attributes.getCompression();
		return new N5Options(blockSize, compression, executorService);
	}

	protected static List<Named<Supplier<N5Writer>>> provideDatasetNames() {
		final String basePath = testDirectoryPath.toString();
		return Arrays.asList(
				named("HDF5", () -> new N5HDF5Writer(Paths.get(basePath, "data.h5ad").toString())),
				named("Zarr", () -> new N5ZarrWriter(Paths.get(basePath, "data.zarr").toString())),
				named("N5", () -> new N5FSWriter(Paths.get(basePath, "data.n5ad").toString())));
	}

	private static void deleteRecursively(final Path path) throws IOException {
		Files.walk(path)
				.sorted(Comparator.reverseOrder())
				.map(Path::toFile)
				.forEach(File::delete);
	}
}
