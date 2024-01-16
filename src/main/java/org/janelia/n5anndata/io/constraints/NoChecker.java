package org.janelia.n5anndata.io.constraints;

import org.janelia.n5anndata.io.AnnDataField;
import org.janelia.n5anndata.io.AnnDataFieldType;
import org.janelia.n5anndata.io.AnnDataPath;
import org.janelia.saalfeldlab.n5.N5Reader;


/**
 * A Checker that performs no checks (in particular, also doesn't read any data).
 *
 * @author Michael Innerberger
 */
class NoChecker extends StrictChecker {
	@Override
	public void check(final N5Reader reader, final AnnDataPath path, final AnnDataFieldType type, final long[] shape) {}

	@Override
	protected boolean satisfiesTypeConstraints(final AnnDataField field, final AnnDataFieldType type, final AnnDataFieldType parentType) {
		return true;
	}

	@Override
	protected boolean satisfiesDimensionConstraints(final AnnDataField field, final long[] shape, final long nObs, final long nVar) {
		return true;
	}

	@Override
	protected boolean satisfiesDataFrameConstraints(final long[] shape, final long indexSize) {
		return true;
	}
}
