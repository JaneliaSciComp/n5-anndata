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
 * @param <D> type of data
 * @param <I> type of indices
 */
public class SparseArrayFactory<
		D extends NumericType<D> & NativeType<D>,
		I extends IntegerType<I> & NativeType<I>> extends ImgFactory<D> {

	protected final int leadingDimension;
	protected final I indexType;


	protected SparseArrayFactory(final D type, final I indexType, final int leadingDimension) {
		super(type);
		this.leadingDimension = leadingDimension;
		this.indexType = indexType;
	}

	@Override
	public SparseArray<D, I> create(final long... dimensions) {
		if (dimensions.length != 2)
			throw new IllegalArgumentException("Only 2D images are supported");

		Dimensions.verify(dimensions);
		final ArrayImg<D, ?> data = new ArrayImgFactory<>(type()).create(1);
		final ArrayImg<I, ?> indices = new ArrayImgFactory<>(indexType).create(1);
		final int secondaryDimension = 1 - leadingDimension;
		final ArrayImg<I, ?> indptr = new ArrayImgFactory<>(indexType).create(dimensions[secondaryDimension] + 1);

		return (leadingDimension == 0) ? new CsrArray<>(dimensions[0], dimensions[1], data, indices, indptr)
			: new CscArray<>(dimensions[0], dimensions[1], data, indices, indptr);
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public <S> ImgFactory<S> imgFactory(final S type) throws IncompatibleTypeException {
		if (type instanceof NumericType && type instanceof NativeType)
			return new SparseArrayFactory<>((NumericType & NativeType) type, indexType, leadingDimension);
		else
			throw new IncompatibleTypeException(this, type.getClass().getCanonicalName() + " does not implement NumericType & NativeType.");
	}

	@Override
	public Img<D> create(final long[] dim, final D type) {
		return create(dim);
	}
}
