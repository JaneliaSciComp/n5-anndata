package org.janelia.n5anndata.datastructures;

import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;


@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class SparseArrayTest {

	private static final double DELTA = 1e-6;
	protected static Map<String, SparseArray<DoubleType, LongType>> sparseMatrices;

	@Test
	public void csr_setup_is_correct() {
		final CsrArray<DoubleType, LongType> csr = setupCsr();
		assertEquals(2, csr.numDimensions());
		assertArrayEquals(new long[]{0, 0}, csr.minAsLongArray());
		assertArrayEquals(new long[]{9, 8}, csr.maxAsLongArray());
	}

	@Test
	public void iteration_order_equality_test_is_correct() {
		final CsrArray<DoubleType, LongType> csr = setupCsr();
		final CsrArray<DoubleType, LongType> csr2 = csr.copy();
		final CscArray<DoubleType, LongType> csc = setupCsc();

		assertEquals(csr.iterationOrder(), csr.iterationOrder());
		assertEquals(csr.iterationOrder(), csr2.iterationOrder());
		assertNotEquals(csr.iterationOrder(), csc.iterationOrder());
	}

	@Test
	public void csr_nonzero_entries_are_correct() {
		final int[] x = new int[]{2, 5, 0, 6, 9};
		final int[] y = new int[]{0, 1, 2, 8, 8};

		for (int i = 0; i < x.length; i++) {
			final RandomAccess<DoubleType> ra = setupCsr().randomAccess();
			assertEquals(1.0, ra.setPositionAndGet(x[i],y[i]).getRealDouble(), DELTA, "Mismatch for x=" + x[i] + ", y=" + y[i]);
		}
	}

	@Test
	public void sparse_has_correct_number_of_nonzeros() {
		for (final Map.Entry<String, SparseArray<DoubleType, LongType>> entry : sparseMatrices.entrySet()) {
			assertEquals(5, SparseArray.getNumberOfNonzeros(entry.getValue()), "Mismatch for " + entry.getKey());
		}
	}

	@Test
	public void conversion_to_sparse_is_correct() {
		for (final SparseArray<DoubleType, LongType> sparse : sparseMatrices.values()) {
			assertEquals(5, SparseArray.getNumberOfNonzeros(sparse));
			final SparseArray<DoubleType, LongType> newCsr = SparseArray.convertToSparse(sparse, 0);
			assertInstanceOf(CsrArray.class, newCsr);
			assert2DRaiEquals(sparse, newCsr);
			final SparseArray<DoubleType, LongType> newCsc = SparseArray.convertToSparse(sparse, 1);
			assertInstanceOf(CscArray.class, newCsc);
			assert2DRaiEquals(sparse, newCsc);
		}
	}

	@Test
	public void csc_is_csr_transposed() {
		final CsrArray<DoubleType, LongType> csr = setupCsr();
		final CscArray<DoubleType, LongType> csc = setupCsc();
		assert2DRaiEquals(csr, Views.permute(csc, 0, 1));
	}

	protected static CsrArray<DoubleType, LongType> setupCsr() {
		return (CsrArray<DoubleType, LongType>) sparseMatrices.get("CSR");
	}

	protected static CscArray<DoubleType, LongType> setupCsc() {
		return (CscArray<DoubleType, LongType>) sparseMatrices.get("CSC");
	}

	@BeforeAll
	public static void setupSparseImages() {
		final Img<DoubleType> data = ArrayImgs.doubles(new double[]{1.0, 1.0, 1.0, 1.0, 1.0}, 5);
		final Img<LongType> indices = ArrayImgs.longs(new long[]{2L, 5L, 0L, 6L, 9L}, 5);
		final Img<LongType> indptr = ArrayImgs.longs(new long[]{0L, 1L, 2L, 3L, 3L, 3L, 3L, 3L, 3L, 5L}, 10);

		sparseMatrices = new HashMap<>();
		sparseMatrices.put("CSR", new CsrArray<>(10, 9, data, indices, indptr));
		sparseMatrices.put("CSC", new CscArray<>(9, 10, data, indices, indptr));
	}

	protected static <T extends Type<T>> void assert2DRaiEquals(
			final RandomAccessibleInterval<T> expected,
			final RandomAccessibleInterval<T> actual
	) {
		assertEquals(expected.dimension(0), actual.dimension(0), "Number of columns not the same.");
		assertEquals(expected.dimension(1), actual.dimension(1), "Number of rows not the same.");

		final RandomAccess<T> raExpected = expected.randomAccess();
		final RandomAccess<T> raActual = actual.randomAccess();
		for (int i = 0; i < expected.dimension(0); ++i)
			for (int j = 0; j < expected.dimension(1); ++j)
				assertEquals(raExpected.setPositionAndGet(i, j), raActual.setPositionAndGet(i, j), "Rai's differ on entry (" + i + "," + j +")");
	}
}