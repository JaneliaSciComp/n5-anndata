package org.janelia.n5anndata.io;

import org.janelia.saalfeldlab.n5.N5Reader;

import java.util.Arrays;

import static org.janelia.n5anndata.io.AnnDataFieldType.*;

public class Checker {

	public static final Checker NONE = new NoChecker();
	public static final Checker ONLY_TYPE = new Checker(new StrictTypeChecker(), new NoDimensionChecker());
	public static final Checker ONLY_DIMENSION = new Checker(new NoTypeChecker(), new StrictDimensionChecker());
	public static final Checker STRICT = new Checker(new StrictTypeChecker(), new StrictDimensionChecker());

	private final TypeChecker typeChecker;
	private final DimensionChecker dimensionChecker;


	private Checker(final TypeChecker typeChecker, final DimensionChecker dimensionChecker) {
		this.typeChecker = typeChecker;
		this.dimensionChecker = dimensionChecker;
	}

	public void check(final N5Reader reader, final String path, final AnnDataFieldType type, final long[] shape) {
		check(reader, AnnDataPath.fromString(path), type, shape);
	}

	public void check(final N5Reader reader, final AnnDataPath path, final AnnDataFieldType type, final long[] shape) {
		final String parentPath = path.getParentPath();
		final AnnDataFieldType parentType = AnnDataUtils.getFieldType(reader, parentPath);
		final long nObs = AnnDataUtils.getNObs(reader);
		final long nVar = AnnDataUtils.getNVar(reader);

		if (! typeChecker.check(path.getField(), type, parentType)) {
			final String msg = String.format("Cannot put '%s' at '%s' because it is not allowed by '%s' or the parent type ('%s')",
											 type, path, path.getField(), parentType);
			throw new AnnDataException(msg);
		}
		if (! dimensionChecker.checkFieldConstraints(path.getField(), shape, nObs, nVar)) {
			final String msg = String.format("Dimensions %s not compatible with nObs=%d and nVar=%d because of the constraints enforced by '%s'",
											 Arrays.toString(shape), nObs, nVar, path.getField());
			throw new AnnDataException(msg);
		}
		if (parentType == DATA_FRAME) {
			final long indexSize = AnnDataUtils.getDataFrameIndexSize(reader, parentPath);
			if (! dimensionChecker.checkDataFrameConstraints(shape, indexSize)) {
				final String msg = String.format("Dimensions %s not compatible with data frame constraints of '%s' (index size=%d)",
												 Arrays.toString(shape), parentPath, indexSize);
				throw new AnnDataException(msg);
			}
		}
	}


	private static class NoChecker extends Checker {
		private NoChecker() {
			super(null, null);
		}

		@Override
		public void check(final N5Reader reader, final String path, final AnnDataFieldType type, final long[] shape) {}

		@Override
		public void check(final N5Reader reader, final AnnDataPath path, final AnnDataFieldType type, final long[] shape) {}
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
					return (is2D(shape) && shape[0] == nObs && shape[1] == nVar);
				case OBS:
					return (is1D(shape) && shape[0] == nObs);
				case VAR:
					return (is1D(shape) && shape[0] == nVar);
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

		@Override
		public boolean checkDataFrameConstraints(final long[] shape, final long indexSize) {
			return (is1D(shape) && shape[0] == indexSize);
		}

		private static boolean is1D(final long[] shape) {
			return (shape.length == 1 || (shape.length == 2 && shape[1] == 1));
		}

		private static boolean is2D(final long[] shape) {
			return (shape.length == 2);
		}
	}
}
