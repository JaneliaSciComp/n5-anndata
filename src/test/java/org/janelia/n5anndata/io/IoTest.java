package org.janelia.n5anndata.io;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import org.janelia.n5anndata.datastructures.CscMatrix;
import org.janelia.n5anndata.datastructures.CsrMatrix;
import org.janelia.n5anndata.datastructures.SparseArray;
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
import org.junit.jupiter.api.Assertions;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Named.named;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class IoTest {

	private static Path testDirectoryPath;
	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
	private static final Compression COMPRESSION = new GzipCompression();
	public static final N5Options ARRAY_OPTIONS = new N5Options(new int[]{2}, COMPRESSION, EXECUTOR);
	public static final N5Options MATRIX_OPTIONS = new N5Options(new int[]{2, 2}, new GzipCompression(), EXECUTOR);

	private static final List<String> OBS_NAMES = Arrays.asList("a", "b", "cd", "efg");
	private static final List<String> VAR_NAMES = Arrays.asList("cell1", "cell2", "cell3");
	private static final Img<DoubleType> MATRIX = ArrayImgs.doubles(new double[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}, 3, 4);

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

	@ParameterizedTest
	@MethodSource("datasetsWithDifferentBackends")
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
	@MethodSource("datasetsWithDifferentBackends")
	public void created_anndata_is_valid(final Supplier<N5Writer> writerSupplier) {
		try (final N5Writer writer = writerSupplier.get()) {
			AnnDataUtils.initializeAnnData(OBS_NAMES, VAR_NAMES, writer, ARRAY_OPTIONS);
			assertTrue(AnnDataUtils.isValidAnnData(writer));
		} catch (final Exception e) {
			fail("Could not write / read file: ", e);
		}
	}

	@ParameterizedTest
	@MethodSource("datasetsWithDifferentBackends")
	public void read_and_write_dense_matrix_in_X(final Supplier<N5Writer> writerSupplier) {
		final AnnDataPath path = new AnnDataPath(AnnDataField.X);
		try (final N5Writer writer = writerSupplier.get()) {
			AnnDataUtils.initializeAnnData(OBS_NAMES, VAR_NAMES, writer, ARRAY_OPTIONS);
			AnnDataUtils.writeNumericalArray(MATRIX, writer, path, MATRIX_OPTIONS, AnnDataFieldType.DENSE_ARRAY);
			final Img<DoubleType> actual = AnnDataUtils.readNumericalArray(writer, path);
			assertEquals(MATRIX, actual);
		} catch (final Exception e) {
			fail("Could not write / read file: ", e);
		}
	}

	@ParameterizedTest
	@MethodSource("datasetsWithDifferentBackends")
	public void write_dense_as_csr_in_varm(final Supplier<N5Writer> writerSupplier) {
		final AnnDataPath path = new AnnDataPath(AnnDataField.VARM, "test");
		final RandomAccessibleInterval<DoubleType> transposed = Views.permute(MATRIX, 1, 0);
		try (final N5Writer writer = writerSupplier.get()) {
			AnnDataUtils.initializeAnnData(OBS_NAMES, VAR_NAMES, writer, ARRAY_OPTIONS);
			AnnDataUtils.writeNumericalArray(transposed, writer, path, MATRIX_OPTIONS, AnnDataFieldType.CSR_MATRIX);
			final Img<DoubleType> actual = AnnDataUtils.readNumericalArray(writer, path);
			assertInstanceOf(CsrMatrix.class, actual);
			assertEquals(transposed, actual);
		} catch (final Exception e) {
			fail("Could not write / read file: ", e);
		}
	}

	@ParameterizedTest
	@MethodSource("datasetsWithDifferentBackends")
	public void write_csr_as_csc_in_obsm(final Supplier<N5Writer> writerSupplier) {
		final AnnDataPath path = new AnnDataPath(AnnDataField.OBSM, "test");
		final Img<DoubleType> csr = SparseArray.convertToSparse(MATRIX, 0);
		try (final N5Writer writer = writerSupplier.get()) {
			AnnDataUtils.initializeAnnData(OBS_NAMES, VAR_NAMES, writer, ARRAY_OPTIONS);
			AnnDataUtils.writeNumericalArray(csr, writer, path, MATRIX_OPTIONS, AnnDataFieldType.CSC_MATRIX);
			final Img<DoubleType> actual = AnnDataUtils.readNumericalArray(writer, path);
			assertInstanceOf(CscMatrix.class, actual);
			assertEquals(MATRIX, actual);
		} catch (final Exception e) {
			fail("Could not write / read file: ", e);
		}
	}

	@ParameterizedTest
	@MethodSource("datasetsWithDifferentBackends")
	public void write_csc_as_dense_in_layers(final Supplier<N5Writer> writerSupplier) {
		final AnnDataPath path = new AnnDataPath(AnnDataField.LAYERS, "test");
		final Img<DoubleType> csc = SparseArray.convertToSparse(MATRIX, 1);
		try (final N5Writer writer = writerSupplier.get()) {
			AnnDataUtils.initializeAnnData(OBS_NAMES, VAR_NAMES, writer, ARRAY_OPTIONS);
			AnnDataUtils.writeNumericalArray(csc, writer, path, MATRIX_OPTIONS, AnnDataFieldType.DENSE_ARRAY);
			final Img<DoubleType> actual = AnnDataUtils.readNumericalArray(writer, path);
			Assertions.assertEquals(AnnDataUtils.getFieldType(writer, path), AnnDataFieldType.DENSE_ARRAY);
			assertEquals(MATRIX, actual);
		} catch (final Exception e) {
			fail("Could not write / read file: ", e);
		}
	}

	@ParameterizedTest
	@MethodSource("datasetsWithDifferentBackends")
	public void write_floats_in_obsp(final Supplier<N5Writer> writerSupplier) {
		final AnnDataPath path = new AnnDataPath(AnnDataField.OBSP, "test");
		final Img<FloatType> expected = ArrayImgs.floats(new float[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}, 4, 4);
		try (final N5Writer writer = writerSupplier.get()) {
			AnnDataUtils.initializeAnnData(OBS_NAMES, VAR_NAMES, writer, ARRAY_OPTIONS);
			AnnDataUtils.writeNumericalArray(expected, writer, path, MATRIX_OPTIONS, AnnDataFieldType.DENSE_ARRAY);
			final Img<FloatType> actual = AnnDataUtils.readNumericalArray(writer, path);
			assertEquals(expected, actual);
		} catch (final Exception e) {
			fail("Could not write / read file: ", e);
		}
	}

	@ParameterizedTest
	@MethodSource("datasetsWithDifferentBackends")
	public void write_shorts_in_varp(final Supplier<N5Writer> writerSupplier) {
		final AnnDataPath path = new AnnDataPath(AnnDataField.VARP, "test");
		final Img<ShortType> expected = ArrayImgs.shorts(new short[] {1, 2, 3, 4, 5, 6, 7, 8, 9}, 3, 3);
		try (final N5Writer writer = writerSupplier.get()) {
			AnnDataUtils.initializeAnnData(OBS_NAMES, VAR_NAMES, writer, ARRAY_OPTIONS);
			AnnDataUtils.writeNumericalArray(expected, writer, path, MATRIX_OPTIONS, AnnDataFieldType.DENSE_ARRAY);
			final Img<ShortType> actual = AnnDataUtils.readNumericalArray(writer, path);
			assertEquals(expected, actual);
		} catch (final Exception e) {
			fail("Could not write / read file: ", e);
		}
	}

	@ParameterizedTest
	@MethodSource("datasetsWithDifferentBackends")
	public void write_strings_in_obs(final Supplier<N5Writer> writerSupplier) {
		final AnnDataPath path = new AnnDataPath(AnnDataField.OBS, "test");
		try (final N5Writer writer = writerSupplier.get()) {
			AnnDataUtils.initializeAnnData(OBS_NAMES, VAR_NAMES, writer, ARRAY_OPTIONS);
			AnnDataUtils.writeStringArray(OBS_NAMES, writer, path, ARRAY_OPTIONS, AnnDataFieldType.STRING_ARRAY);
			final List<String> actual = AnnDataUtils.readStringArray(writer, path);
			assertIterableEquals(OBS_NAMES, actual);
		} catch (final Exception e) {
			fail("Could not write / read file: ", e);
		}
	}

	@ParameterizedTest
	@MethodSource("datasetsWithDifferentBackends")
	public void write_categoricals_in_var(final Supplier<N5Writer> writerSupplier) {
		final AnnDataPath path = new AnnDataPath(AnnDataField.VAR, "test");
		try (final N5Writer writer = writerSupplier.get()) {
			AnnDataUtils.initializeAnnData(OBS_NAMES, VAR_NAMES, writer, ARRAY_OPTIONS);
			AnnDataUtils.writeStringArray(VAR_NAMES, writer, path, ARRAY_OPTIONS, AnnDataFieldType.CATEGORICAL_ARRAY);
			final List<String> actual = AnnDataUtils.readStringArray(writer, path);
			assertIterableEquals(VAR_NAMES, actual);
		} catch (final Exception e) {
			fail("Could not write / read file: ", e);
		}
	}

	@ParameterizedTest
	@MethodSource("datasetsWithDifferentBackends")
	public void nested_mappings_in_uns(final Supplier<N5Writer> writerSupplier) {
		AnnDataPath path = new AnnDataPath(AnnDataField.UNS);
		try (final N5Writer writer = writerSupplier.get()) {
			AnnDataUtils.initializeAnnData(OBS_NAMES, VAR_NAMES, writer, ARRAY_OPTIONS);
			for (int i = 0; i < 3; i++) {
				path = path.append("test" + i);
				AnnDataUtils.createMapping(writer, path);
			}
			path = path.append("test3");
			AnnDataUtils.writeNumericalArray(MATRIX, writer, path, MATRIX_OPTIONS, AnnDataFieldType.DENSE_ARRAY);
			final Img<DoubleType> actual = AnnDataUtils.readNumericalArray(writer, path);
			assertEquals(MATRIX, actual);
		} catch (final Exception e) {
			fail("Could not write / read file: ", e);
		}
	}

	@ParameterizedTest
	@MethodSource("datasetsWithDifferentBackends")
	public void create_and_populate_dataframe(final Supplier<N5Writer> writerSupplier) {
		final AnnDataPath path = new AnnDataPath(AnnDataField.OBSM, "test");
		final AnnDataPath datasetPath = path.append("data");
		try (final N5Writer writer = writerSupplier.get()) {
			AnnDataUtils.initializeAnnData(OBS_NAMES, VAR_NAMES, writer, ARRAY_OPTIONS);
			AnnDataUtils.createDataFrame(OBS_NAMES, writer, path, ARRAY_OPTIONS);
			AnnDataUtils.writeStringArray(OBS_NAMES, writer, datasetPath, ARRAY_OPTIONS, AnnDataFieldType.STRING_ARRAY);
			final List<String> actualIndex = AnnDataUtils.readDataFrameIndex(writer, path);
			assertIterableEquals(OBS_NAMES, actualIndex);
			final List<String> actualData = AnnDataUtils.readStringArray(writer, datasetPath);
			assertIterableEquals(OBS_NAMES, actualData);
			final Set<String> datasets = AnnDataUtils.getDataFrameDatasetNames(writer, path);
			assertIterableEquals(Collections.singletonList("data"), datasets);
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
			throws IOException {
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
			throws IOException {
		final N5Reader reader = getReaderFor(pythonDataset);
		final N5Writer writer = getWriterFor(javaDataset);

		AnnDataPath path = new AnnDataPath(AnnDataField.X);
		final Img<DoubleType> X = AnnDataUtils.readNumericalArray(reader, path);
		N5Options options = getOptionsFor(reader, path);
		AnnDataUtils.writeNumericalArray(X, writer, path, options, AnnDataFieldType.CSR_MATRIX);

		path = new AnnDataPath(AnnDataField.OBSP, "rnd");
		final Img<DoubleType> csr = AnnDataUtils.readNumericalArray(reader, path);
		options = getOptionsFor(reader, path);
		AnnDataUtils.writeNumericalArray(csr, writer, path, options, AnnDataFieldType.CSR_MATRIX);

		path = new AnnDataPath(AnnDataField.VARP, "rnd");
		final Img<ShortType> csc = AnnDataUtils.readNumericalArray(reader, path);
		options = getOptionsFor(reader, path);
		AnnDataUtils.writeNumericalArray(csc, writer, path, options, AnnDataFieldType.CSC_MATRIX);

		path = new AnnDataPath(AnnDataField.OBS, "_index");
		final List<String> obs_names = AnnDataUtils.readStringArray(reader, path);
		options = getOptionsFor(reader, path);
		AnnDataUtils.writeStringArray(obs_names, writer, path, options, AnnDataFieldType.STRING_ARRAY);

		path = new AnnDataPath(AnnDataField.VAR, "_index");
		final List<String> var_names = AnnDataUtils.readStringArray(reader, path);
		options = getOptionsFor(reader, path);
		AnnDataUtils.writeStringArray(var_names, writer, path, options, AnnDataFieldType.STRING_ARRAY);

		path = new AnnDataPath(AnnDataField.OBS, "cell_type");
		final List<String> cell_type = AnnDataUtils.readStringArray(reader, path);
		options = getOptionsFor(reader, path);
		AnnDataUtils.writeStringArray(cell_type, writer, path, options, AnnDataFieldType.CATEGORICAL_ARRAY);

		path = new AnnDataPath(AnnDataField.VAR, "gene_stuff1");
		final Img<IntType> genes1 = AnnDataUtils.readNumericalArray(reader, path);
		options = getOptionsFor(reader, path);
		AnnDataUtils.writeNumericalArray(genes1, writer, path, options, AnnDataFieldType.DENSE_ARRAY);

		path = new AnnDataPath(AnnDataField.VAR, "gene_stuff2");
		final Img<LongType> genes2 = AnnDataUtils.readNumericalArray(reader, path);
		options = getOptionsFor(reader, path);
		AnnDataUtils.writeNumericalArray(genes2, writer, path, options, AnnDataFieldType.DENSE_ARRAY);

		path = new AnnDataPath(AnnDataField.OBS, "X_umap");
		final Img<DoubleType> umap1 = AnnDataUtils.readNumericalArray(reader, path);
		options = getOptionsFor(reader, path);
		AnnDataUtils.writeNumericalArray(umap1, writer, path, options, AnnDataFieldType.DENSE_ARRAY);

		path = new AnnDataPath(AnnDataField.VARM, "X_umap");
		final Img<DoubleType> umap2 = AnnDataUtils.readNumericalArray(reader, path);
		options = getOptionsFor(reader, path);
		AnnDataUtils.writeNumericalArray(umap2, writer, path, options, AnnDataFieldType.DENSE_ARRAY);

		path = new AnnDataPath(AnnDataField.UNS, "random");
		final Img<DoubleType> uns = AnnDataUtils.readNumericalArray(reader, path);
		options = getOptionsFor(reader, path);
		AnnDataUtils.writeNumericalArray(uns, writer, path, options, AnnDataFieldType.DENSE_ARRAY);

		path = new AnnDataPath(AnnDataField.LAYERS, "log");
		final Img<FloatType> log = AnnDataUtils.readNumericalArray(reader, path);
		options = getOptionsFor(reader, path);
		AnnDataUtils.writeNumericalArray(log, writer, path, options, AnnDataFieldType.CSR_MATRIX);
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

	private static N5Options getOptionsFor(final N5Reader reader, final AnnDataPath path) {
		final DatasetAttributes attributes = reader.getDatasetAttributes(path.toString());
		final int[] blockSize = attributes.getBlockSize();
		final Compression compression = attributes.getCompression();
		return new N5Options(blockSize, compression, EXECUTOR);
	}

	private static <T> void assertEquals(final RandomAccessibleInterval<T> expected, final Img<T> actual) {
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

	private static void deleteRecursively(final Path path) throws IOException {
		Files.walk(path)
				.sorted(Comparator.reverseOrder())
				.map(Path::toFile)
				.forEach(File::delete);
	}
}
