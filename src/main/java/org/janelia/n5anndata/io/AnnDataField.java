package org.janelia.n5anndata.io;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.janelia.n5anndata.io.AnnDataFieldType.*;

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

	public String getPath() {
		return getPath("");
	}

	public String getPath(final String subPath) {
		return Paths.get(basePath, subPath).toString();
	}

	public boolean canBeA(final AnnDataFieldType type) {
		return allowedTypes.contains(type);
	}

	public boolean canHaveAsChild(final AnnDataFieldType type) {
		return allowedChildTypes.contains(type);
	}

	public String toString() {
		return basePath;
	}

	public static AnnDataField fromString(final String field) {
		for (final AnnDataField f : values())
			if (f.basePath.equals(field))
				return f;
		throw new IllegalArgumentException("No known anndata field with name '" + field + "'");
	}
}
