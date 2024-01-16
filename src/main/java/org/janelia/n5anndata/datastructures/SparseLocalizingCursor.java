package org.janelia.n5anndata.datastructures;

import net.imglib2.AbstractLocalizingCursor;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.NumericType;

/**
 * Cursor for a SparseArray.
 * Access for CSR and CSC matrices is almost the same, except for
 * the leading dimension (i.e., if the indices array represents row
 * or column indices).
 *
 * @param <T> the type of the data values in the SparseArray
 *
 * @author Michael Innerberger
 */
public class SparseLocalizingCursor<T extends NumericType<T> & NativeType<T>> extends AbstractLocalizingCursor<T> {

	private final long[] max;
	private boolean isHit = false;
	private boolean isInitialized = false;
	private final int leadingDim;
	private final int secondaryDim;
	private final T fillValue;

	private final SparseArray<T,?> img;
	private final Cursor<T> dataCursor;
	private final Cursor<? extends IntegerType<?>> indicesCursor;
	private final RandomAccess<? extends IntegerType<?>> indptrAccess;


	/**
	 * Constructor for the SparseLocalizingCursor class.
	 * This cursor should not be used directly, but rather the
	 * {@link SparseArray::cursor()} method should be used.
	 *
	 * @param n the number of dimensions in the SparseArray
	 * @throws IllegalArgumentException if the number of dimensions is not 2
	 */
	public SparseLocalizingCursor(final int n) {
		super(n);
		if (n != 2)
			throw new IllegalArgumentException("Only 2D sparse arrays are supported");

		max = new long[]{0L, 0L};
		img = null;
		dataCursor = null;
		indicesCursor = null;
		indptrAccess = null;
		fillValue = null;
		leadingDim = 0;
		secondaryDim = 0;
	}

	/**
	 * Constructor for the SparseLocalizingCursor class.
	 * This cursor should not be used directly, but rather the
	 * {@link SparseArray::cursor()} method should be used.
	 *
	 * @param img the SparseArray to be iterated over
	 * @param leadingDimension the leading dimension of the SparseArray
	 * @param fillValue the value to be returned when the cursor is not at a non-zero element
	 * @throws IllegalArgumentException if the number of dimensions is not 2
	 */
	public SparseLocalizingCursor(final SparseArray<T,?> img, final int leadingDimension, final T fillValue) {
		super(img.numDimensions());
		if (n != 2)
			throw new IllegalArgumentException("Only 2D images are supported");

		this.img = img;
		max = new long[]{img.dimension(0)-1, img.dimension(1)-1};
		leadingDim = leadingDimension;
		secondaryDim = 1 - leadingDimension;

		dataCursor = img.data.cursor();
		indicesCursor = img.indices.localizingCursor();
		indptrAccess = img.indptr.randomAccess();
		reset();

		this.fillValue = fillValue.copy();
		this.fillValue.setZero();

	}

	@Override
	public T get() {
		return isHit ? dataCursor.get() : fillValue;
	}

	@Override
	public SparseLocalizingCursor<T> copy() {
		return new SparseLocalizingCursor<>(img, leadingDim, fillValue);
	}

	@Override
	public void fwd() {
		if (! isInitialized) {
			isInitialized = true;
			advanceToNextNonzeroElement();
		} else if (isHit) {
			advanceToNextNonzeroElement();
		}

		// always: advance to next element in picture ...
		if (position[leadingDim] < max[leadingDim]) {
			++position[leadingDim];
		} else {
			position[leadingDim] = 0;
			++position[secondaryDim];
		}

		// ... and check if it is a hit
		isHit = (indicesCursor.get().getIntegerLong() == position[leadingDim]
				&& indptrAccess.getLongPosition(0) == position[secondaryDim]);
	}

	/**
	 * Advance to next non-zero element of the SparseArray.
	 */
	protected void advanceToNextNonzeroElement() {
		if (indicesCursor.hasNext()) {
			dataCursor.fwd();
			indicesCursor.fwd();
		}
		final long currentIndexPosition = indicesCursor.getLongPosition(0);
		indptrAccess.fwd(0);
		while (indptrAccess.get().getIntegerLong() <= currentIndexPosition)
			indptrAccess.fwd(0);
		indptrAccess.bck(0);
	}

	@Override
	public void reset() {
		position[leadingDim] = -1;
		position[secondaryDim] = 0;
		dataCursor.reset();
		indicesCursor.reset();
		indptrAccess.setPosition(0,0);
	}

	@Override
	public boolean hasNext() {
		return (position[0] < max[0] || position[1] < max[1]);
	}
}
