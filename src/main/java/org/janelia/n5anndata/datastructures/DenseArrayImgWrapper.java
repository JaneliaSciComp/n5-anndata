package org.janelia.n5anndata.datastructures;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.NumericType;

/**
 * A wrapper for ImgLib2 {@link Img} that implements the AnnData field type {@link DenseArray}.
 * @param <T> Numeric and native {@link Type} of the data
 */
public class DenseArrayImgWrapper<T extends NumericType<T> & NativeType<T>> implements DenseArray<T> {
	private final Img<T> img;

	public DenseArrayImgWrapper(final Img<T> img) {
		this.img = img;
	}


	@Override
	public ImgFactory<T> factory() {
		return img.factory();
	}

	@Override
	public Img<T> copy() {
		return img.copy();
	}

	@Override
	public Cursor<T> cursor() {
		return img.cursor();
	}

	@Override
	public Cursor<T> localizingCursor() {
		return img.localizingCursor();
	}

	@Override
	public long size() {
		return img.size();
	}

	@Override
	public Object iterationOrder() {
		return img.iterationOrder();
	}

	@Override
	public long min(final int i) {
		return img.min(i);
	}

	@Override
	public long max(final int i) {
		return img.max(i);
	}

	@Override
	public RandomAccess<T> randomAccess() {
		return img.randomAccess();
	}

	@Override
	public RandomAccess<T> randomAccess(final Interval interval) {
		return img.randomAccess(interval);
	}

	@Override
	public int numDimensions() {
		return img.numDimensions();
	}
}
