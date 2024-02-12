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

import net.imglib2.Dimensions;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;

/**
 * Factory for {@link SparseArray}s.
 *
 * @param <T> type of data
 * @param <I> type of indices
 *
 * @author Michael Innerberger
 */
public class SparseArrayFactory<
		T extends NumericType<T> & NativeType<T>,
		I extends IntegerType<I> & NativeType<I>> extends ImgFactory<T> {

	protected final int leadingDimension;
	protected final I indexType;


	/**
	 * Constructor for the SparseArrayFactory class.
	 *
	 * @param type an example of the type of data
	 * @param indexType an example of the type of indices
	 * @param leadingDimension the leading dimension
	 */
	protected SparseArrayFactory(final T type, final I indexType, final int leadingDimension) {
		super(type);
		this.leadingDimension = leadingDimension;
		this.indexType = indexType;
	}

	@Override
	@SuppressWarnings({"ResultOfMethodCallIgnored"})
	public SparseArray<T, I> create(final long... dimensions) {
		if (dimensions.length != 2)
			throw new IllegalArgumentException("Only 2D images are supported");

		// this will throw an exception if the dimensions are not valid, so the output is not needed
		Dimensions.verify(dimensions);
		final ArrayImg<T, ?> data = new ArrayImgFactory<>(type()).create(1);
		final ArrayImg<I, ?> indices = new ArrayImgFactory<>(indexType).create(1);
		final int secondaryDimension = 1 - leadingDimension;
		final ArrayImg<I, ?> indptr = new ArrayImgFactory<>(indexType).create(dimensions[secondaryDimension] + 1);

		return (leadingDimension == 0) ? new CsrMatrix<>(dimensions[0], dimensions[1], data, indices, indptr)
			: new CscMatrix<>(dimensions[0], dimensions[1], data, indices, indptr);
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public <S> ImgFactory<S> imgFactory(final S type) throws IncompatibleTypeException {
		if (type instanceof NumericType && type instanceof NativeType)
			return new SparseArrayFactory((NumericType & NativeType) type, indexType, leadingDimension);
		else
			throw new IncompatibleTypeException(this, type.getClass().getCanonicalName() + " does not implement NumericType & NativeType.");
	}

	@Override
	@Deprecated
	public Img<T> create(final long[] dim, final T type) {
		return create(dim);
	}
}
