package org.janelia.n5anndata.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AnnDataPath {

	public static final String ROOT = "/";
	private static final String SEPARATOR = "/";

	private final AnnDataField field;
	private final List<String> keys;

	public AnnDataPath(final AnnDataField field, final String... keys) {
		this(field, Arrays.asList(keys));
	}

	public AnnDataPath(final AnnDataField field, final List<String> keys) {
		this.field = field;
		this.keys = keys;
	}

	public AnnDataField getField() {
		return field;
	}

	public String keysAsString() {
		return String.join(SEPARATOR, keys);
	}

	public String toString() {
		if (keys.isEmpty()) {
			return ROOT + field.toString();
		} else {
			return ROOT + field.toString() + SEPARATOR + keysAsString();
		}
	}

	public String getParentPath() {
		if (keys.isEmpty()) {
			return ROOT;
		} else {
			return new AnnDataPath(field, keys.subList(0, keys.size() - 1)).toString();
		}
	}

	public String getLeaf() {
		if (keys.isEmpty()) {
			return field.toString();
		} else {
			return keys.get(keys.size() - 1);
		}
	}

	public AnnDataPath append(final String... additionalKeys) {
		return append(Arrays.asList(additionalKeys));
	}

	public AnnDataPath append(final List<String> additionalKeys) {
		final List<String> newKeys = new ArrayList<>(keys);
		newKeys.addAll(additionalKeys);
		return new AnnDataPath(field, newKeys);
	}

	public static AnnDataPath fromString(final String path) {
		if (path == null || path.isEmpty())
			throw new IllegalArgumentException("Invalid path: " + path);

		final String normalizedPath = withoutLeadingRoot(path);
		final String[] parts = normalizedPath.split(SEPARATOR);
		if (parts.length < 1)
			throw new IllegalArgumentException("Invalid path: " + path);

		final AnnDataField field = AnnDataField.fromString(parts[0]);
		final String[] subKeys = (parts.length == 1) ? new String[0] : Arrays.copyOfRange(parts, 1, parts.length);
		return new AnnDataPath(field, subKeys);
	}

	private static String withoutLeadingRoot(final String path) {
		return path.startsWith(ROOT) ? path.substring(1) : path;
	}
}
