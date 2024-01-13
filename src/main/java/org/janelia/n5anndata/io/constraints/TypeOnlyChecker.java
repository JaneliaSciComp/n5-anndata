package org.janelia.n5anndata.io.constraints;

import org.janelia.n5anndata.io.AnnDataField;

class TypeOnlyChecker extends StrictChecker {
	@Override
	protected boolean satisfiesDimensionConstraints(final AnnDataField field, final long[] shape, final long nObs, final long nVar) {
		return true;
	}

	@Override
	protected boolean satisfiesDataFrameConstraints(final long[] shape, final long indexSize) {
		return true;
	}
}
