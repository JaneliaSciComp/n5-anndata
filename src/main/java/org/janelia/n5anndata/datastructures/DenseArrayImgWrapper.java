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
 * @param <D> Numeric and native {@link Type} of the data
 */
public class DenseArrayImgWrapper<D extends NumericType<D> & NativeType<D>> implements DenseArray<D> {
	private final Img<D> img;

	public DenseArrayImgWrapper(Img<D> img) {
		this.img = img;
	}


	@Override
	public ImgFactory<D> factory() {
		return img.factory();
	}

	@Override
	public Img<D> copy() {
		return img.copy();
	}

	@Override
	public Cursor<D> cursor() {
		return img.cursor();
	}

	@Override
	public Cursor<D> localizingCursor() {
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
	public long min(int i) {
		return img.min(i);
	}

	@Override
	public long max(int i) {
		return img.max(i);
	}

	@Override
	public RandomAccess<D> randomAccess() {
		return img.randomAccess();
	}

	@Override
	public RandomAccess<D> randomAccess(Interval interval) {
		return img.randomAccess(interval);
	}

	@Override
	public int numDimensions() {
		return img.numDimensions();
	}
}
