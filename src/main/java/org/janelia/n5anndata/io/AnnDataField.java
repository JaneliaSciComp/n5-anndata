package org.janelia.n5anndata.io;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.janelia.n5anndata.io.AnnDataFieldType.CSC_MATRIX;
import static org.janelia.n5anndata.io.AnnDataFieldType.CSR_MATRIX;
import static org.janelia.n5anndata.io.AnnDataFieldType.DATA_FRAME;
import static org.janelia.n5anndata.io.AnnDataFieldType.DENSE_ARRAY;
import static org.janelia.n5anndata.io.AnnDataFieldType.MAPPING;


/**
 * Enum representing the different fields of an AnnData object.
 * Each field has a base path, a list of allowed types, and a list of allowed child types.
 *
 * @author Michael Innerberger
 */
public enum AnnDataField {
	X("X",
	  Arrays.asList(DENSE_ARRAY, CSR_MATRIX, CSC_MATRIX),
	  Collections.emptyList()),
	LAYERS("layers",
		   Collections.singletonList(MAPPING),
		   Arrays.asList(DENSE_ARRAY, CSR_MATRIX, CSC_MATRIX)),
	OBS("obs",
		Collections.singletonList(DATA_FRAME),
		Arrays.asList(AnnDataFieldType.values())),
	OBSM("obsm",
		 Collections.singletonList(MAPPING),
		 Arrays.asList(DENSE_ARRAY, CSR_MATRIX, CSC_MATRIX, DATA_FRAME)),
	OBSP("obsp",
		 Collections.singletonList(MAPPING),
		 Arrays.asList(DENSE_ARRAY, CSR_MATRIX, CSC_MATRIX)),
	VAR("var",
		Collections.singletonList(DATA_FRAME),
		Arrays.asList(AnnDataFieldType.values())),
	VARM("varm",
		Collections.singletonList(MAPPING),
		 Arrays.asList(DENSE_ARRAY, CSR_MATRIX, CSC_MATRIX, DATA_FRAME)),
	VARP("varp",
		 Collections.singletonList(MAPPING),
		 Arrays.asList(DENSE_ARRAY, CSR_MATRIX, CSC_MATRIX)),
	UNS("uns",
		Collections.singletonList(MAPPING),
		Arrays.asList(AnnDataFieldType.values()));

	private final String basePath;
	private final List<AnnDataFieldType> allowedTypes;
	private final List<AnnDataFieldType> allowedChildTypes;

	AnnDataField(
			final String basePath,
			final List<AnnDataFieldType> allowedTypes,
			final List<AnnDataFieldType> allowedChildTypes) {
		this.basePath = basePath;
		this.allowedTypes = allowedTypes;
		this.allowedChildTypes = allowedChildTypes;
	}

	/**
	 * Returns the base path of the field.
	 *
	 * @return The base path of the field.
	 */
	public String getPath() {
		return basePath;
	}

	/**
	 * Checks if a given type can be used for this field.
	 *
	 * @param type The type to check.
	 * @return True if the type can be used for this field, false otherwise.
	 */
	public boolean canBeA(final AnnDataFieldType type) {
		return allowedTypes.contains(type);
	}

	/**
	 * Checks if a given type can be placed somewhere within this field.
	 *
	 * @param type The type to check.
	 * @return True if the type can be placed inside this field, false otherwise.
	 */
	public boolean canHaveAsChild(final AnnDataFieldType type) {
		return allowedChildTypes.contains(type);
	}

	/**
	 * Returns a string representation of this field in the form of its path directly below the root.
	 *
	 * @return The base path of the field.
	 */
	public String toString() {
		return basePath;
	}

	/**
	 * Returns the AnnDataField corresponding to a given string.
	 *
	 * @param fieldName The string to convert
	 * @return The corresponding AnnDataField.
	 * @throws IllegalArgumentException If no AnnDataField corresponds to the given string.
	 */
	public static AnnDataField fromString(final String fieldName) {
		for (final AnnDataField field : values())
			if (field.basePath.equals(fieldName))
				return field;
		throw new IllegalArgumentException("No known anndata field with name '" + fieldName + "'");
	}
}
