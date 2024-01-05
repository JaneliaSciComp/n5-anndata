package org.janelia.n5anndata.datastructures;

import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;

public interface DenseArray<T extends NumericType<T> & NativeType<T>> extends Img<T> {
}
