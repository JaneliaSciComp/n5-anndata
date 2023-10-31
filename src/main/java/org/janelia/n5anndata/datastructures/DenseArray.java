package org.janelia.n5anndata.datastructures;

import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;

public interface DenseArray<D extends NumericType<D> & NativeType<D>> extends Img<D> {
}
