package org.janelia.n5anndata.datastructures;

import java.util.HashMap;
import java.util.Map;

import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class SparseArrayTest {

	protected static Map<String, SparseArray<DoubleType, LongType>> sparseMatrices;

	@Test
	public void CsrSetupIsCorrect() {
		CsrArray<DoubleType, LongType> csr = setupCsr();
		assertEquals(2, csr.numDimensions());
		assertArrayEquals(new long[]{0, 0}, csr.minAsLongArray());
		assertArrayEquals(new long[]{9, 8}, csr.maxAsLongArray());
	}

	@Test
	public void iterationOrderEqualityTestIsCorrect() {
		CsrArray<DoubleType, LongType> csr = setupCsr();
		CsrArray<DoubleType, LongType> csr2 = csr.copy();
		CscArray<DoubleType, LongType> csc = setupCsc();

		assertEquals(csr.iterationOrder(), csr.iterationOrder());
		assertEquals(csr.iterationOrder(), csr2.iterationOrder());
		assertNotEquals(csr.iterationOrder(), csc.iterationOrder());
	}

	@Test
	public void CsrNonzeroEntriesAreCorrect() {
		int[] x = new int[]{2, 5, 0, 6, 9};
		int[] y = new int[]{0, 1, 2, 8, 8};

		for (int i = 0; i < x.length; i++) {
			RandomAccess<DoubleType> ra = setupCsr().randomAccess();
			assertEquals(1.0, ra.setPositionAndGet(x[i],y[i]).getRealDouble(), 1e-6, "Mismatch for x=" + x[i] + ", y=" + y[i]);
		}
	}

	@Test
	public void sparseHasCorrectNumberOfNonzeros() {
		for (Map.Entry<String, SparseArray<DoubleType, LongType>> entry : sparseMatrices.entrySet()) {
			assertEquals(5, SparseArray.getNumberOfNonzeros(entry.getValue()), "Mismatch for " + entry.getKey());
		}
	}

	@Test
	public void conversionToSparseIsCorrect() {
		for (SparseArray<DoubleType, LongType> sparse : sparseMatrices.values()) {
			assertEquals(5, SparseArray.getNumberOfNonzeros(sparse));
			SparseArray<DoubleType, LongType> newCsr = SparseArray.convertToSparse(sparse, 0);
			assertTrue(newCsr instanceof CsrArray);
			assert2DRaiEquals(sparse, newCsr);
			SparseArray<DoubleType, LongType> newCsc = SparseArray.convertToSparse(sparse, 1);
			assertTrue(newCsc instanceof CscArray);
			assert2DRaiEquals(sparse, newCsc);
		}
	}

	@Test
	public void CscIsCsrTransposed() {
		CsrArray<DoubleType, LongType> csr = setupCsr();
		CscArray<DoubleType, LongType> csc = setupCsc();
		assert2DRaiEquals(csr, Views.permute(csc, 0, 1));
	}

	protected CsrArray<DoubleType, LongType> setupCsr() {
		return (CsrArray<DoubleType, LongType>) sparseMatrices.get("CSR");
	}

	protected CscArray<DoubleType, LongType> setupCsc() {
		return (CscArray<DoubleType, LongType>) sparseMatrices.get("CSC");
	}

	@BeforeAll
	public static void setupSparseImages() {
		Img<DoubleType> data = ArrayImgs.doubles(new double[]{1.0, 1.0, 1.0, 1.0, 1.0}, 5);
		Img<LongType> indices = ArrayImgs.longs(new long[]{2L, 5L, 0L, 6L, 9L}, 5);
		Img<LongType> indptr = ArrayImgs.longs(new long[]{0L, 1L, 2L, 3L, 3L, 3L, 3L, 3L, 3L, 5L}, 10);

		sparseMatrices = new HashMap<>();
		sparseMatrices.put("CSR", new CsrArray<>(10, 9, data, indices, indptr));
		sparseMatrices.put("CSC", new CscArray<>(9, 10, data, indices, indptr));
	}

	protected static <T extends Type<T>> void assert2DRaiEquals(RandomAccessibleInterval<T> expected, RandomAccessibleInterval<T> actual) {
		assertEquals(expected.dimension(0), actual.dimension(0), "Number of columns not the same.");
		assertEquals(expected.dimension(1), actual.dimension(1), "Number of rows not the same.");

		RandomAccess<T> raExpected = expected.randomAccess();
		RandomAccess<T> raActual = actual.randomAccess();
		for (int i = 0; i < expected.dimension(0); ++i)
			for (int j = 0; j < expected.dimension(1); ++j)
				assertEquals(raExpected.setPositionAndGet(i, j), raActual.setPositionAndGet(i, j), "Rai's differ on entry (" + i + "," + j +")");
	}
}