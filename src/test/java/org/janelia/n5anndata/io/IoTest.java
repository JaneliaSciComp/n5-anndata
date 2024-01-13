package org.janelia.n5anndata.io;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import org.janelia.n5anndata.datastructures.CscMatrix;
import org.janelia.n5anndata.datastructures.CsrMatrix;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class IoTest extends BaseIoTest {

	private static final List<String> OBS_NAMES = Arrays.asList("a", "b", "cd", "efg");
	private static final List<String> VAR_NAMES = Arrays.asList("cell1", "cell2", "cell3");
	private static final Img<DoubleType> MATRIX = ArrayImgs.doubles(new double[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}, 3, 4);


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
		final Img<DoubleType> csr = CsrMatrix.from(MATRIX);
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
		final Img<DoubleType> csc = CscMatrix.from(MATRIX);
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
}
