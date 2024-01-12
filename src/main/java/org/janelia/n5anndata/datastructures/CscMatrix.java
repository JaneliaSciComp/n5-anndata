package org.janelia.n5anndata.datastructures;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;

public class CscMatrix<
		T extends NumericType<T> & NativeType<T>,
		I extends IntegerType<I> & NativeType<I>> extends SparseArray<T, I> {

    public CscMatrix(final long numCols, final long numRows, final Img<T> data, final Img<I> indices, final Img<I> indptr) {
        super(numCols, numRows, data, indices, indptr);
    }

	@Override
	public RandomAccess<T> randomAccess() {
		return new SparseRandomAccess<>(this, 1);
	}

	@Override
	public Cursor<T> localizingCursor() {
		return new SparseLocalizingCursor<>(this, 1, data.firstElement());
	}

	@Override
	public CscMatrix<T,I> copy() {
		final Img<T> dataCopy = data.copy();
		final Img<I> indicesCopy = indices.copy();
		final Img<I> indptrCopy = indptr.copy();
		return new CscMatrix<>(dimension(0), dimension(1), dataCopy, indicesCopy, indptrCopy);
	}

	@Override
	public ImgFactory<T> factory() {
		return new SparseArrayFactory<>(data.getAt(0), indices.getAt(0), 1);
	}

	@Override
	public ColumnMajorIterationOrder2D iterationOrder() {
		return new ColumnMajorIterationOrder2D(this);
	}

	/**
	 * An iteration order that scans a 2D image in column-major order.
	 * I.e., cursors iterate column by column and row by row. For instance a
	 * sparse img ranging from <em>(0,0)</em> to <em>(1,1)</em> is iterated like
	 * <em>(0,0), (0,1), (1,0), (1,1)</em>
	 */
	public static class ColumnMajorIterationOrder2D {

		private final Interval interval;
		public ColumnMajorIterationOrder2D(final Interval interval) {
			this.interval = interval;
		}

		@Override
		public boolean equals(final Object obj) {

			if (!(obj instanceof CscMatrix.ColumnMajorIterationOrder2D))
				return false;

			return SparseArray.haveSameIterationSpace(interval, ((ColumnMajorIterationOrder2D) obj).interval);
		}
	}
}
