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

import net.imglib2.AbstractLocalizable;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;

/**
 * RandomAccess for a SparseArray.
 * Access for CSR and CSC matrices is almost the same, except for
 * the leading dimension (i.e., if the indices array represents row
 * or column indices).
 *
 * @param <T> the type of the data values in the SparseArray
 * @param <I> the type of the indices in the SparseArray
 *
 * @author Michael Innerberger
 */
public class SparseRandomAccess<
        T extends NumericType<T> & NativeType<T>,
        I extends IntegerType<I> & NativeType<I>> extends AbstractLocalizable implements RandomAccess<T> {

    protected final SparseArray<T, I> sparse;
    protected final RandomAccess<T> dataAccess;
    protected final RandomAccess<I> indicesAccess;
    protected final RandomAccess<I> indptrAccess;
    protected final int leadingDim;
    protected final int secondaryDim;
    protected final T fillValue;


    /**
     * Constructor for the SparseRandomAccess class.
     * This RandomAccess should not be used directly, but rather the
     * {@link SparseArray#randomAccess()} method should be used.
     *
     * @param sparse the SparseArray to be accessed
     * @param leadingDim the leading dimension of the SparseArray
     */
    public SparseRandomAccess(final SparseArray<T, I> sparse, final int leadingDim) {
        super(sparse.numDimensions());
        this.sparse = sparse;
        this.dataAccess = sparse.data.randomAccess();
        this.indicesAccess = sparse.indices.randomAccess();
        this.indptrAccess = sparse.indptr.randomAccess();
        this.leadingDim = leadingDim;
        this.secondaryDim = 1 - leadingDim;

        this.fillValue = dataAccess.get().createVariable();
        this.fillValue.setZero();
    }

    /**
     * Copy constructor for the SparseRandomAccess class.
     *
     * @param ra the SparseRandomAccess to be copied
     */
    public SparseRandomAccess(final SparseRandomAccess<T, I> ra) {
        super(ra.sparse.numDimensions());

        this.sparse = ra.sparse;
        this.leadingDim = ra.leadingDim;
        this.secondaryDim = ra.secondaryDim;
        this.setPosition( ra );

        // not implementing copy() methods here had no effect since only setPosition() is used
        this.indicesAccess = ra.indicesAccess.copy();
        this.indptrAccess = ra.indptrAccess.copy();
        this.dataAccess = ra.dataAccess.copy();
        this.fillValue = ra.fillValue.createVariable();
        this.fillValue.setZero();
    }

    @Override
    public void fwd(final int d) {
        ++position[d];
    }

    @Override
    public void bck(final int d) {
        --position[d];
    }

    @Override
    public void move(final int distance, final int d) {
        position[d] += distance;
    }

    @Override
    public void move(final long distance, final int d) {
        position[d] += distance;
    }

    @Override
    public void move(final Localizable localizable) {
        for (int d = 0; d < n; ++d) {
            position[d] += localizable.getLongPosition(d);
        }
    }

    @Override
    public void move(final int[] distance) {
        for (int d = 0; d < n; ++d) {
            position[d] += distance[d];
        }
    }

    @Override
    public void move(final long[] distance) {
        for (int d = 0; d < n; ++d) {
            position[d] += distance[d];
        }
    }

    @Override
    public void setPosition(final Localizable localizable) {
        for (int d = 0; d < n; ++d) {
            position[d]  = localizable.getLongPosition(d);
        }
    }

    @Override
    public void setPosition(final int[] position) {
        for (int d = 0; d < n; ++d) {
            this.position[d] = position[d];
        }
    }

    @Override
    public void setPosition(final long[] position) {
		if (n >= 0) {
            System.arraycopy(position, 0, this.position, 0, n);
        }
    }

    @Override
    public void setPosition(final int position, final int d) {
        this.position[d] = position;
    }

    @Override
    public void setPosition(final long position, final int d) {
        this.position[d] = position;
    }

    @Override
    public T get() {

        // determine range of indices to search
        indptrAccess.setPosition(position[secondaryDim], 0);
        long start = indptrAccess.get().getIntegerLong();
        indptrAccess.fwd(0);
        long end = indptrAccess.get().getIntegerLong();

        if (start == end)
            return fillValue;

        long current, currentInd;
        do {
            current = (start + end) / 2L;
            indicesAccess.setPosition(current, 0);
            currentInd = indicesAccess.get().getIntegerLong();

            if (currentInd == position[leadingDim]) {
                dataAccess.setPosition(indicesAccess);
                return dataAccess.get();
            }
            if (currentInd < position[leadingDim])
                start = current;
            if (currentInd > position[leadingDim])
                end = current;
        } while (current != start || (end - start) > 1L);

        return fillValue;
    }

    @Override
    public RandomAccess<T> copy() {
        return new SparseRandomAccess<>(this);
    }
}
