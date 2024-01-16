package org.janelia.n5anndata.datastructures;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.LongType;

/**
 * Class representing a Compressed Sparse Row (CSR) matrix.
 * This is a {@link SparseArray} where the leading dimension is the row dimension.
 *
 * @param <T> the type of the data values
 * @param <I> the type of the indices
 *
 * @author Michael Innerberger
 */
public class CsrMatrix<
	T extends NumericType<T> & NativeType<T>,
	I extends IntegerType<I> & NativeType<I>> extends SparseArray<T,I> {

	/**
	 * Constructor for the CsrMatrix class.
	 *
	 * @param numCols the number of columns
	 * @param numRows the number of rows
	 * @param data the data values
	 * @param indices the column indices of the non-zero data values
	 * @param indptr the row-index pointers
	 */
    public CsrMatrix(final long numCols, final long numRows, final Img<T> data, final Img<I> indices, final Img<I> indptr) {
        super(numCols, numRows, data, indices, indptr);
    }

	@Override
	public RandomAccess<T> randomAccess() {
		return new SparseRandomAccess<>(this, 0);
	}

	@Override
	public Cursor<T> localizingCursor() {
		return new SparseLocalizingCursor<>(this, 0, data.firstElement());
	}

	@Override
	public CsrMatrix<T,I> copy() {
		final Img<T> dataCopy = data.copy();
		final Img<I> indicesCopy = indices.copy();
		final Img<I> indptrCopy = indptr.copy();
		return new CsrMatrix<>(dimension(0), dimension(1), dataCopy, indicesCopy, indptrCopy);
	}

	@Override
	public ImgFactory<T> factory() {
		return new SparseArrayFactory<>(data.getAt(0), indices.getAt(0), 0);
	}

	/**
	 * Converts a given RandomAccessibleInterval into a CsrMatrix.
	 *
	 * @param rai the RandomAccessibleInterval to be converted
	 * @return a CsrMatrix representing the given RandomAccessibleInterval
	 */
	public static <T extends NumericType<T> & NativeType<T>>
	SparseArray<T, LongType> from(final RandomAccessibleInterval<T> rai) {
		return convertToSparse(rai, 0);
	}

	@Override
	public RowMajorIterationOrder2D iterationOrder() {
		return new RowMajorIterationOrder2D(this);
	}


	/**
	 * An iteration order that scans a 2D image in row-major order.
	 * I.e., cursors iterate row by row and column by column. For instance a
	 * sparse img ranging from <em>(0,0)</em> to <em>(1,1)</em> is iterated like
	 * <em>(0,0), (1,0), (0,1), (1,1)</em>
	 */
	public static class RowMajorIterationOrder2D {

		private final Interval interval;
		public RowMajorIterationOrder2D(final Interval interval) {
			this.interval = interval;
		}

		@Override
		public boolean equals(final Object obj) {

			if (!(obj instanceof RowMajorIterationOrder2D))
				return false;

			return SparseArray.haveSameIterationSpace(interval, ((RowMajorIterationOrder2D) obj).interval);
		}
	}
}
