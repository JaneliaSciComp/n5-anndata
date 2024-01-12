package org.janelia.n5anndata.io;

import java.util.Arrays;

public class AnnDataPath {

	public static final String ROOT = "/";
	private static final String SEPARATOR = "/";

	private final AnnDataField field;
	private final String[] subPaths;

	public AnnDataPath(final AnnDataField field, final String... subPaths) {
		this.field = field;
		this.subPaths = subPaths;
	}

	public AnnDataField getField() {
		return field;
	}

	public String toString() {
		if (subPaths.length == 0) {
			return ROOT + field.toString();
		} else {
			return ROOT + field.getPath(String.join(SEPARATOR, subPaths));
		}
	}

	public String getParentPath() {
		if (subPaths.length == 0) {
			return ROOT;
		} else if (subPaths.length == 1) {
			return ROOT + field.toString();
		} else {
			return ROOT + field.getPath(String.join(SEPARATOR, Arrays.copyOfRange(subPaths, 0, subPaths.length - 1)));
		}
	}

	public String getLeaf() {
		if (subPaths.length == 0) {
			return field.toString();
		} else {
			return subPaths[subPaths.length - 1];
		}
	}

	public AnnDataPath append(final String... additionalPaths) {
		final String[] newSubPaths = Arrays.copyOf(subPaths, subPaths.length + additionalPaths.length);
		System.arraycopy(additionalPaths, 0, newSubPaths, subPaths.length, additionalPaths.length);
		return new AnnDataPath(field, newSubPaths);
	}

	public static AnnDataPath fromString(final String path) {
		if (path == null || path.isEmpty())
			throw new IllegalArgumentException("Invalid path: " + path);

		final String normalizedPath = withoutLeadingRoot(path);
		final String[] parts = normalizedPath.split(SEPARATOR);
		if (parts.length < 1)
			throw new IllegalArgumentException("Invalid path: " + path);

		final AnnDataField field = AnnDataField.fromString(parts[0]);
		if (parts.length == 1) {
			return new AnnDataPath(field);
		} else {
			final String[] subPaths = Arrays.copyOfRange(parts, 1, parts.length);
			return new AnnDataPath(field, subPaths);
		}
	}

	private static String withoutLeadingRoot(final String path) {
		return path.startsWith(ROOT) ? path.substring(1) : path;
	}
}
