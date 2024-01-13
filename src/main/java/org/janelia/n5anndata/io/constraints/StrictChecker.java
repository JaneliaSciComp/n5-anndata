package org.janelia.n5anndata.io.constraints;

import org.janelia.n5anndata.io.AnnDataException;
import org.janelia.n5anndata.io.AnnDataField;
import org.janelia.n5anndata.io.AnnDataFieldType;
import org.janelia.n5anndata.io.AnnDataPath;
import org.janelia.n5anndata.io.AnnDataUtils;
import org.janelia.saalfeldlab.n5.N5Reader;

import java.util.Arrays;
import java.util.List;

import static org.janelia.n5anndata.io.AnnDataFieldType.*;

class StrictChecker implements Checker {
	private static final List<AnnDataFieldType> ALLOWED_DATA_FRAME_TYPES = Arrays.asList(STRING_ARRAY, CATEGORICAL_ARRAY, DENSE_ARRAY);

	public void check(final N5Reader reader, final AnnDataPath path, final AnnDataFieldType type, final long[] shape) {
		final AnnDataPath parentPath = path.getParentPath();
		final AnnDataFieldType parentType = AnnDataUtils.getFieldType(reader, parentPath);

		if (! satisfiesTypeConstraints(path.getField(), type, parentType)) {
			final String msg = String.format("Cannot put '%s' at '%s' because it is not allowed by '%s' or the parent type ('%s')",
											 type, path, path.getField(), parentType);
			throw new AnnDataException(msg);
		}

		final long nObs = AnnDataUtils.getNObs(reader);
		final long nVar = AnnDataUtils.getNVar(reader);

		if (parentType == DATA_FRAME) {
			final long indexSize = AnnDataUtils.getDataFrameIndexSize(reader, parentPath);
			if (! satisfiesDataFrameConstraints(shape, indexSize)) {
				final String msg = String.format("Dimensions %s not compatible with data frame constraints of '%s' (index size=%d)",
												 Arrays.toString(shape), parentPath, indexSize);
				throw new AnnDataException(msg);
			}
		} else {
			if (! satisfiesDimensionConstraints(path.getField(), shape, nObs, nVar)) {
				final String msg = String.format("Dimensions %s not compatible with nObs=%d and nVar=%d because of the constraints enforced by '%s'",
												 Arrays.toString(shape), nObs, nVar, path.getField());
				throw new AnnDataException(msg);
			}
		}
	}

	protected boolean satisfiesTypeConstraints(final AnnDataField field, final AnnDataFieldType type, final AnnDataFieldType parentType) {
		if (parentType == ANNDATA) {
			return field.canBeA(type);
		} else if (parentType == DATA_FRAME) {
			return ALLOWED_DATA_FRAME_TYPES.contains(type);
		} else {
			return field.canHaveAsChild(type);
		}
	}

	protected boolean satisfiesDimensionConstraints(final AnnDataField field, final long[] shape, final long nObs, final long nVar) {
		switch (field) {
			case X: case LAYERS:
				return (is2D(shape) && shape[0] == nObs && shape[1] == nVar);
			case OBS: case VAR:
				return true; // constraints are given by the data frame
			case OBSM:
				return (is2D(shape) && shape[0] == nObs && shape[1] > 1);
			case VARM:
				return (is2D(shape) && shape[0] == nVar && shape[1] > 1);
			case OBSP:
				return (is2D(shape) && shape[0] == nObs && shape[1] == nObs);
			case VARP:
				return (is2D(shape) && shape[0] == nVar && shape[1] == nVar);
			case UNS:
				return true;
			default:
				return false;
		}
	}

	protected boolean satisfiesDataFrameConstraints(final long[] shape, final long indexSize) {
		return (is1D(shape) && shape[0] == indexSize);
	}

	private static boolean is1D(final long[] shape) {
		return (shape.length == 1 || (shape.length == 2 && shape[1] == 1));
	}

	private static boolean is2D(final long[] shape) {
		return (shape.length == 2);
	}
}
