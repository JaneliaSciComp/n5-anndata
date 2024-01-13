package org.janelia.n5anndata.io.constraints;

import org.janelia.n5anndata.io.AnnDataFieldType;
import org.janelia.n5anndata.io.AnnDataPath;
import org.janelia.saalfeldlab.n5.N5Reader;

public interface Checker {

	Checker NONE = new NoChecker();
	Checker ONLY_TYPE = new TypeOnlyChecker();
	Checker ONLY_DIMENSION = new DimensionOnlyChecker();
	Checker STRICT = new StrictChecker();


	void check(N5Reader reader, AnnDataPath path, AnnDataFieldType type, long[] shape);

	default void check(final N5Reader reader, final String path, final AnnDataFieldType type, final long[] shape) {
		check(reader, AnnDataPath.fromString(path), type, shape);
	}
}
