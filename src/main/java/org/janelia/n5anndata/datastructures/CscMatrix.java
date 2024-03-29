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
 * Class representing a Compressed Sparse Column (CSC) matrix.
 * This is a {@link SparseArray} where the leading dimension is the column dimension.
 *
 * @param <T> the type of the data values
 * @param <I> the type of the indices
 *
 * @author Michael Innerberger
 */
public class CscMatrix<
		T extends NumericType<T> & NativeType<T>,
		I extends IntegerType<I> & NativeType<I>> extends SparseArray<T, I> {

	/**
	 * Constructor for the CsrMatrix class.
	 *
	 * @param numCols the number of columns
	 * @param numRows the number of rows
	 * @param data the data values
	 * @param indices the row indices of the non-zero data values
	 * @param indptr the column-index pointers
	 */
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

	/**
	 * Converts a given RandomAccessibleInterval into a CscMatrix.
	 *
	 * @param rai the RandomAccessibleInterval to be converted
	 * @return a CscMatrix representing the given RandomAccessibleInterval
	 */
	public static <T extends NumericType<T> & NativeType<T>>
	SparseArray<T, LongType> from(final RandomAccessibleInterval<T> rai) {
		return convertToSparse(rai, 1);
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
