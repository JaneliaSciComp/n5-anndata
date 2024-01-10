package org.janelia.n5anndata.io;

import org.janelia.saalfeldlab.n5.N5Reader;

import static org.janelia.n5anndata.io.AnnDataFieldType.*;

public class Checker {

	public static final Checker NONE = new Checker(new NoTypeChecker(), new NoDimensionChecker());
	public static final Checker ONLY_TYPE = new Checker(new StrictTypeChecker(), new NoDimensionChecker());
	public static final Checker ONLY_DIMENSION = new Checker(new NoTypeChecker(), new StrictDimensionChecker());
	public static final Checker STRICT = new Checker(new StrictTypeChecker(), new StrictDimensionChecker());

	private final TypeChecker typeChecker;
	private final DimensionChecker dimensionChecker;


	private Checker(final TypeChecker typeChecker, final DimensionChecker dimensionChecker) {
		this.typeChecker = typeChecker;
		this.dimensionChecker = dimensionChecker;
	}

	public boolean check(final N5Reader reader, final String path, final AnnDataFieldType type, final long[] shape) {
		return check(reader, AnnDataPath.fromString(path), type, shape);
	}

	public boolean check(final N5Reader reader, final AnnDataPath path, final AnnDataFieldType type, final long[] shape) {
		final String parentPath = path.getParentPath();
		final AnnDataFieldType parentType = AnnDataUtils.getFieldType(reader, parentPath);
		final long nObs = AnnDataUtils.getNObs(reader);
		final long nVar = AnnDataUtils.getNVar(reader);

		boolean isValid = typeChecker.check(path.getField(), type, parentType);
		isValid = isValid && dimensionChecker.checkFieldConstraints(path.getField(), shape, nObs, nVar);
		if (parentType == DATA_FRAME) {
			final long indexSize = AnnDataUtils.getDataFrameIndexSize(reader, parentPath);
			isValid = isValid && dimensionChecker.checkDataFrameConstraints(shape, indexSize);
		}
		return isValid;
	}


	private interface TypeChecker {
		boolean check(AnnDataField field, AnnDataFieldType type, AnnDataFieldType parentType);
	}

	private static class NoTypeChecker implements TypeChecker {
		@Override
		public boolean check(final AnnDataField field, final AnnDataFieldType type, final AnnDataFieldType parentType) {
			return true;
		}
	}
	private static class StrictTypeChecker implements TypeChecker {
		@Override
		public boolean check(final AnnDataField field, final AnnDataFieldType type, final AnnDataFieldType parentType) {
			if (parentType == ANNDATA) {
				return field.canBeA(type);
			} else if (parentType == DATA_FRAME) {
				return true;
			} else {
				return field.canHaveAsChild(type);
			}
		}
	}


	private interface DimensionChecker {
		boolean checkFieldConstraints(AnnDataField field, long[] shape, long nObs, long nVar);
		boolean checkDataFrameConstraints(long[] shape, long indexSize);
	}

	private static class NoDimensionChecker implements DimensionChecker {
		@Override
		public boolean checkFieldConstraints(final AnnDataField field, final long[] shape, final long nObs, final long nVar) {
			return true;
		}

		@Override
		public boolean checkDataFrameConstraints(final long[] shape, final long indexSize) {
			return true;
		}
	}

	private static class StrictDimensionChecker implements DimensionChecker {
		@Override
		public boolean checkFieldConstraints(final AnnDataField field, final long[] shape, final long nObs, final long nVar) {
			switch (field) {
				case X: case LAYERS:
					return (shape[0] == nObs && shape[1] == nVar);
				case OBS: case VAR:
					return (shape[0] == nVar && shape[1] == 1);
				case OBSM: case VARM:
					return (shape[0] == nVar && shape[1] > 1);
				case OBSP:
					return (shape[0] == nObs && shape[1] == nObs);
				case VARP:
					return (shape[0] == nVar && shape[1] == nVar);
				case UNS:
					return true;
				default:
					return false;
			}
		}

		@Override
		public boolean checkDataFrameConstraints(final long[] shape, final long indexSize) {
			return (shape[0] == indexSize && shape[1] == 1);
		}
	}
}
