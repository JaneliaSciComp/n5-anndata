package org.janelia.n5anndata.io.constraints;

import org.janelia.n5anndata.io.AnnDataField;
import org.janelia.n5anndata.io.AnnDataFieldType;

class DimensionOnlyChecker extends StrictChecker {

	@Override
	protected boolean satisfiesTypeConstraints(final AnnDataField field, final AnnDataFieldType type, final AnnDataFieldType parentType) {
		return true;
	}
}
