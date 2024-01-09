package org.janelia.n5anndata.io;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.janelia.n5anndata.io.AnnDataFieldType.*;

public enum AnnDataField {
	X("X",
	  false,
	  Arrays.asList(DENSE_ARRAY, CSR_MATRIX, CSC_MATRIX)),
	LAYERS("layers",
		   true,
		   Arrays.asList(DENSE_ARRAY, CSR_MATRIX, CSC_MATRIX)),
	OBS("obs",
		true,
		Collections.singletonList(DATA_FRAME)),
	OBSM("obsm",
		 true,
		 Arrays.asList(DENSE_ARRAY, CSR_MATRIX, CSC_MATRIX, DATA_FRAME)),
	OBSP("obsp",
		 true,
		 Arrays.asList(DENSE_ARRAY, CSR_MATRIX, CSC_MATRIX)),
	VAR("var",
		true,
		Collections.singletonList(DATA_FRAME)),
	VARM("varm",
		 true,
		 Arrays.asList(DENSE_ARRAY, CSR_MATRIX, CSC_MATRIX, DATA_FRAME)),
	VARP("varp",
		 true,
		 Arrays.asList(DENSE_ARRAY, CSR_MATRIX, CSC_MATRIX)),
	UNS("uns",
		true,
		Arrays.asList(AnnDataFieldType.values()));

	private final String basePath;
	private final boolean allowsSubPaths;
	private final List<AnnDataFieldType> allowedTypes;

	AnnDataField(final String basePath, final boolean allowsSubPaths, final List<AnnDataFieldType> allowedTypes) {
		this.basePath = basePath;
		this.allowsSubPaths = allowsSubPaths;
		this.allowedTypes = allowedTypes;
	}

	public String getCompletePath(final String subPath) {
		if (!allowsSubPaths && !subPath.isEmpty())
			throw new IllegalArgumentException("Sub-paths are not allowed for field " + basePath);
		return Paths.get(basePath, subPath).toString();
	}

	public boolean allows(final AnnDataFieldType type) {
		return allowedTypes.contains(type);
	}

	public String toString() {
		return basePath;
	}
}
