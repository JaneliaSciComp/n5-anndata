package org.janelia.n5anndata.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * This class represents a path in an AnnData structure.
 * Since the possible paths in an AnnData structure are restricted,
 * this class ensures that only valid paths are used.
 * <p>
 * For example, using the path
 * <pre>
 *     final AnnDataPath path = new AnnDataPath(AnnDataField.OBS, "test1", "test2");
 * </pre>
 * in I/O operations is equivalent to accessing the following python field:
 * <pre>
 *     adata.obs["test1/test2"]
 * </pre>
 *
 * @author Michael Innerberger
 */
public class AnnDataPath {

	/**
	 * The root path "/" of an AnnData structure. This has no field and no keys and
	 * will throw an exception if any of the methods that require a field or keys are
	 * called.
	 * The purpose of this object is to consistently represent the root path as {@link AnnDataPath}.
	 */
	public static final AnnDataPath ROOT = new AnnDataRootPath();
	private static final String SEPARATOR = "/";

	private final AnnDataField field;
	private final List<String> keys;


	/**
	 * Constructor that takes a field (e.g, obs, varm, ...) and a number of keys.
	 *
	 * @param field The field of the path.
	 * @param keys The keys of the path.
	 */
	public AnnDataPath(final AnnDataField field, final String... keys) {
		this(field, Arrays.asList(keys));
	}

	public AnnDataPath(final AnnDataField field, final List<String> keys) {
		this.field = field;
		this.keys = keys;
	}

	/**
	 * Returns the field at the root of the path.
	 *
	 * @return The field of the path.
	 */
	public AnnDataField getField() {
		return field;
	}

	/**
	 * Returns the keys of the path as a string, separated by "/".
	 *
	 * @return The keys of the path as a string.
	 */
	public String keysAsString() {
		return String.join(SEPARATOR, keys);
	}

	@Override
	public String toString() {
		if (keys.isEmpty()) {
			return ROOT.toString() + field.toString();
		} else {
			return ROOT.toString() + field.toString() + SEPARATOR + keysAsString();
		}
	}

	/**
	 * Returns the parent path of the current path as a new AnnDataPath object.
	 * If the current path is a field, the root path is returned.
	 *
	 * @return The parent path of the current path.
	 */
	public AnnDataPath getParentPath() {
		if (keys.isEmpty()) {
			return ROOT;
		} else {
			return new AnnDataPath(field, keys.subList(0, keys.size() - 1));
		}
	}

	/**
	 * Returns the leaf of the path, i.e., the last key. If the path has no keys,
	 * the field is returned as a string.
	 *
	 * @return The leaf of the path.
	 */
	public String getLeaf() {
		if (keys.isEmpty()) {
			return field.toString();
		} else {
			return keys.get(keys.size() - 1);
		}
	}

	/**
	 * Appends additional keys to the path and returns a new AnnDataPath object.
	 *
	 * @param additionalKeys The keys to append.
	 * @return A new path with the additional keys appended.
	 */
	public AnnDataPath append(final String... additionalKeys) {
		return append(Arrays.asList(additionalKeys));
	}

	public AnnDataPath append(final List<String> additionalKeys) {
		final List<String> newKeys = new ArrayList<>(keys);
		newKeys.addAll(additionalKeys);
		return new AnnDataPath(field, newKeys);
	}

	/**
	 * Creates a new path from a string, separated by "/".
	 *
	 * @param path The string to create the path from.
	 * @return A new path created from the string.
	 * @throws IllegalArgumentException If the path is not a valid path within an AnnData object.
	 */
	public static AnnDataPath fromString(final String path) {
		if (path == null || path.isEmpty())
			throw new IllegalArgumentException("Invalid path: " + path);

		if (path.trim().equals(ROOT.toString())) {
			return ROOT;
		}

		final String normalizedPath = withoutLeadingRoot(path);
		final String[] parts = normalizedPath.split(SEPARATOR);
		if (parts.length < 1)
			throw new IllegalArgumentException("Invalid path: " + path);

		final AnnDataField field = AnnDataField.fromString(parts[0]);
		final String[] subKeys = (parts.length == 1) ? new String[0] : Arrays.copyOfRange(parts, 1, parts.length);
		return new AnnDataPath(field, subKeys);
	}

	private static String withoutLeadingRoot(final String path) {
		return path.startsWith(ROOT.toString()) ? path.substring(1) : path;
	}


	private static class AnnDataRootPath extends AnnDataPath {
		public static final String ROOT_CHAR = "/";

		public AnnDataRootPath() {
			super(null, new ArrayList<>());
		}

		@Override
		public AnnDataField getField() {
			throw new UnsupportedOperationException("Root path has no field");
		}

		@Override
		public String keysAsString() {
			throw new UnsupportedOperationException("Root path has no keys");
		}

		@Override
		public String toString() {
			return ROOT_CHAR;
		}

		@Override
		public AnnDataPath getParentPath() {
			throw new UnsupportedOperationException("Root path has no parent");
		}

		@Override
		public String getLeaf() {
			return ROOT_CHAR;
		}

		@Override
		public AnnDataPath append(final List<String> additionalKeys) {
			throw new UnsupportedOperationException("Cannot append keys to root path");
		}
	}
}
