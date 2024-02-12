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
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.view.Views;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract class representing a sparse array in the compressed storage format.
 * A sparse array is a data structure that efficiently stores only non-zero values.
 * This implementation is a generalization of the Compressed Sparse Row (CSR) and
 * Compressed Sparse Column (CSC) formats. The CSR and CSC only differ in how the
 * elements are accessed, which is handled by the respective subclasses.
 * This class implements {@link Img} for use with ImgLib2.
 *
 * @param <T> the type of the data values in the sparse array
 * @param <I> the type of the indices in the sparse array
 *
 * @author Michael Innerberger
 */
abstract public class SparseArray<
        T extends NumericType<T> & NativeType<T>,
        I extends IntegerType<I> & NativeType<I>> implements Img<T> {

    protected final long[] max;
    protected final Img<T> data;
    protected final Img<I> indices;
    protected final Img<I> indptr;

    /**
     * Constructor for the SparseArray class.
     *
     * @param numCols the number of columns
     * @param numRows the number of rows
     * @param data the data values
     * @param indices the indices of the non-zero data values of the leading dimension
     * @param indptr the index pointers for where a new slice of the leading dimension starts in indices
     */
    public SparseArray(
            final long numCols,
            final long numRows,
            final Img<T> data,
            final Img<I> indices,
            final Img<I> indptr
    ) {
        this.data = data;
        this.indices = indices;
        this.indptr = indptr;
        this.max = new long[]{numCols-1, numRows-1};

        if (data.numDimensions() != 1 || indices.numDimensions() != 1 || indptr.numDimensions() != 1)
            throw new IllegalArgumentException("Data, index, and indptr Img must be one dimensional.");
        if (data.min(0) != 0 || indices.min(0) != 0 || indptr.min(0) != 0)
            throw new IllegalArgumentException("Data, index, and indptr arrays must start from 0.");
        if (data.max(0) != indices.max(0))
            throw new IllegalArgumentException("Data and index array must be of the same size.");
        if (indptr.max(0) != max[0]+1 && indptr.max(0) != max[1]+1)
            throw new IllegalArgumentException("Indptr array does not fit number of slices.");
    }

    /**
     * Converts a given RandomAccessibleInterval into a SparseArray by only storing non-zero values.
     * The leading dimension determines whether the sparse array is in CSR (0) or CSC (1) format.
     *
     * @param rai the RandomAccessibleInterval to be converted
     * @param leadingDimension the leading dimension (0 for CSR, 1 for CSC)
     * @return a SparseArray representing the given RandomAccessibleInterval
     * @throws IllegalArgumentException if the leading dimension is not 0 or 1
     */
    protected static <T extends NumericType<T> & NativeType<T>> SparseArray<T, LongType>
    convertToSparse(final RandomAccessibleInterval<T> rai, final int leadingDimension) {
        if (leadingDimension != 0 && leadingDimension != 1)
            throw new IllegalArgumentException("Leading dimension in sparse array must be 0 or 1.");

        final T zeroValue = rai.getAt(0, 0).copy();
        zeroValue.setZero();

        final int nnz = getNumberOfNonzeros(rai);
        final int ptrDimension = 1 - leadingDimension;
        final Img<T> data = new ArrayImgFactory<>(zeroValue).create(nnz);
        final Img<LongType> indices = new ArrayImgFactory<>(new LongType()).create(nnz);
        final Img<LongType> indptr = new ArrayImgFactory<>(new LongType()).create(rai.dimension(ptrDimension) + 1);

        long count = 0;
        T actualValue;
        final RandomAccess<T> ra = rai.randomAccess();
        final RandomAccess<T> dataAccess = data.randomAccess();
        final RandomAccess<LongType> indicesAccess = indices.randomAccess();
        final RandomAccess<LongType> indptrAccess = indptr.randomAccess();
        indptrAccess.setPosition(0,0);
        indptrAccess.get().setLong(0L);

        for (long j = 0; j < rai.dimension(ptrDimension); j++) {
            ra.setPosition(j, ptrDimension);
            for (long i = 0; i < rai.dimension(leadingDimension); i++) {
                ra.setPosition(i, leadingDimension);
                actualValue = ra.get();
                if (!actualValue.valueEquals(zeroValue)) {
                    dataAccess.setPosition(count, 0);
                    dataAccess.get().set(actualValue);
                    indicesAccess.setPosition(count, 0);
                    indicesAccess.get().setLong(i);
                    count++;
                }
            }
            indptrAccess.fwd(0);
            indptrAccess.get().setLong(count);
        }

        return (leadingDimension == 0) ? new CsrMatrix<>(rai.dimension(0), rai.dimension(1), data, indices, indptr)
            : new CscMatrix<>(rai.dimension(0), rai.dimension(1), data, indices, indptr);
    }

    /**
     * Returns the number of non-zero values in a given RandomAccessibleInterval
     * by iterating over all entries.
     *
     * @param rai the RandomAccessibleInterval to be checked
     * @return the number of non-zero values
     */
    public static <T extends NumericType<T>> int getNumberOfNonzeros(final RandomAccessibleInterval<T> rai) {
        final T zeroValue = rai.getAt(0, 0).copy();
        zeroValue.setZero();

        int nnz = 0;
        for (final T pixel : Views.iterable(rai))
            if (!pixel.valueEquals(zeroValue))
                ++nnz;
        return nnz;
    }

    @Override
    public long min(final int d) {
        return 0L;
    }

    @Override
    public long max(final int d) {
        return max[d];
    }

    @Override
    public int numDimensions() {
        return 2;
    }

    @Override
    public RandomAccess<T> randomAccess(final Interval interval) {
        return randomAccess();
    }

    /**
     * Returns the data array of the sparse array.
     *
     * @return the data array
     */
    public Img<T> getDataArray() {
        return data;
    }


    /**
     * Returns the indices array of the sparse array.
     *
     * @return the indices array
     */
    public Img<I> getIndicesArray() {
        return indices;
    }


    /**
     * Returns the index pointer array of the sparse array.
     *
     * @return the index pointer array
     */
    public Img<I> getIndexPointerArray() {
        return indptr;
    }

    @Override
    public Cursor<T> cursor() {
        return localizingCursor();
    }

    @Override
    public long size() {
        return max[0] * max[1];
    }

    /**
     * Checks if two intervals have the same iteration space, i.e., if the matrix
     * indices are the same.
     *
     * @param a One interval
     * @param b Other interval
     * @return true if both intervals have compatible non-singleton dimensions, false otherwise
     */
    protected static boolean haveSameIterationSpace(final Interval a, final Interval b) {
        final List<Integer> nonSingletonDimA = nonSingletonDimensions(a);
        final List<Integer> nonSingletonDimB = nonSingletonDimensions(b);

        if (nonSingletonDimA.size() != nonSingletonDimB.size())
            return false;

        for (int i = 0; i < nonSingletonDimA.size(); i++) {
            final Integer dimA = nonSingletonDimA.get(i);
            final Integer dimB = nonSingletonDimB.get(i);
            if (a.min(dimA) != b.min(dimB) || a.max(dimA) != b.max(dimB))
                return false;
        }

        return true;
    }

    /**
     * Returns a list of non-singleton dimensions for a given interval.
     *
     * @param interval the interval to be checked
     * @return a list of non-singleton dimensions for the given interval
     */
    protected static List<Integer> nonSingletonDimensions(final Interval interval) {
        final List<Integer> nonSingletonDim = new ArrayList<>();
        for (int i = 0; i < interval.numDimensions(); i++)
            if (interval.dimension(i) > 1)
                nonSingletonDim.add(i);
        return nonSingletonDim;
    }
}
