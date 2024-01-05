package org.janelia.n5anndata.datastructures;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.LongType;

import java.util.ArrayList;
import java.util.List;

abstract public class SparseArray<
        D extends NumericType<D> & NativeType<D>,
        I extends IntegerType<I> & NativeType<I>> implements Img<D> {

    protected final long[] max;
    protected final Img<D> data;
    protected final Img<I> indices;
    protected final Img<I> indptr;

    public SparseArray(
            final long numCols,
            final long numRows,
            final Img<D> data,
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

    public static <T extends NumericType<T> & NativeType<T>> SparseArray<T, LongType>
    convertToSparse(final Img<T> img) {
        return convertToSparse(img, 0); // CSR per default
    }

    public static <T extends NumericType<T> & NativeType<T>> SparseArray<T, LongType>
    convertToSparse(final Img<T> img, final int leadingDimension) {
        if (leadingDimension != 0 && leadingDimension != 1)
            throw new IllegalArgumentException("Leading dimension in sparse array must be 0 or 1.");

        final T zeroValue = img.getAt(0, 0).copy();
        zeroValue.setZero();

        final int nnz = getNumberOfNonzeros(img);
        final int ptrDimension = 1 - leadingDimension;
        final Img<T> data = new ArrayImgFactory<>(zeroValue).create(nnz);
        final Img<LongType> indices = new ArrayImgFactory<>(new LongType()).create(nnz);
        final Img<LongType> indptr = new ArrayImgFactory<>(new LongType()).create(img.dimension(ptrDimension) + 1);

        long count = 0;
        T actualValue;
        final RandomAccess<T> ra = img.randomAccess();
        final RandomAccess<T> dataAccess = data.randomAccess();
        final RandomAccess<LongType> indicesAccess = indices.randomAccess();
        final RandomAccess<LongType> indptrAccess = indptr.randomAccess();
        indptrAccess.setPosition(0,0);
        indptrAccess.get().setLong(0L);

        for (long j = 0; j < img.dimension(ptrDimension); j++) {
            ra.setPosition(j, ptrDimension);
            for (long i = 0; i < img.dimension(leadingDimension); i++) {
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

        return (leadingDimension == 0) ? new CsrArray<>(img.dimension(0), img.dimension(1), data, indices, indptr)
            : new CscArray<>(img.dimension(0), img.dimension(1), data, indices, indptr);
    }

    public static <T extends NumericType<T>> int getNumberOfNonzeros(final Img<T> img) {
        final T zeroValue = img.getAt(0, 0).copy();
        zeroValue.setZero();

        int nnz = 0;
        for (final T pixel : img)
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
    public RandomAccess<D> randomAccess(final Interval interval) {
        return randomAccess();
    }

    public Img<D> getDataArray() {
        return data;
    }

    public Img<I> getIndicesArray() {
        return indices;
    }

    public Img<I> getIndexPointerArray() {
        return indptr;
    }

    @Override
    public Cursor<D> cursor() {
        return localizingCursor();
    }

    @Override
    public long size() {
        return max[0] * max[1];
    }

    /**
     * Checks if two intervals have the same iteration space.
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

    protected static List<Integer> nonSingletonDimensions(final Interval interval) {
        final List<Integer> nonSingletonDim = new ArrayList<>();
        for (int i = 0; i < interval.numDimensions(); i++)
            if (interval.dimension(i) > 1)
                nonSingletonDim.add(i);
        return nonSingletonDim;
    }
}
