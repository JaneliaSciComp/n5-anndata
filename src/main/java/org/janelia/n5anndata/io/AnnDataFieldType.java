package org.janelia.n5anndata.io;

public enum AnnDataFieldType {

	ANNDATA("anndata", "0.1.0"),
	DENSE_ARRAY("array", "0.2.0"),
	CSR_MATRIX("csr_matrix", "0.1.0"),
	CSC_MATRIX("csc_matrix", "0.1.0"),
	DATA_FRAME("dataframe", "0.2.0"),
	MAPPING("dict", "0.1.0"),
	NUMERIC_SCALAR("numeric-scalar", "0.2.0"),
	STRING_SCALAR("string", "0.2.0"),
	CATEGORICAL_ARRAY("categorical", "0.2.0"),
	STRING_ARRAY("string-array", "0.2.0"),
	NULLABLE_INTEGER("nullable-integer", "0.1.0"),
	NULLABLE_BOOL("nullable-bool", "0.1.0"),
	MISSING("missing", "missing");

	private final String encoding;
	private final String version;

	AnnDataFieldType(final String encoding, final String version) {
		this.encoding = encoding;
		this.version = version;
	}

	public String getEncoding() {
		return encoding;
	}

	public String getVersion() {
		return version;
	}

	public String toString() {
		return "encoding: " + encoding + ", version: " + version;
	}

	public static void checkIfNumericalArray(final AnnDataFieldType type) {
		if (type != DENSE_ARRAY && type != CSR_MATRIX && type != CSC_MATRIX) {
			throw new AnnDataException("Numerical array type expected, but got " + type);
		}
	}

	public static void checkIfStringArray(final AnnDataFieldType type) {
		if (type != STRING_ARRAY && type != CATEGORICAL_ARRAY) {
			throw new AnnDataException("String array type expected, but got " + type);
		}
	}

	public static AnnDataFieldType fromString(final String encoding, final String version) {
		if (encoding == null || version == null)
			return MISSING;

		for (final AnnDataFieldType type : values())
			if (type.encoding.equals(encoding) && type.version.equals(version))
				return type;

		throw new IllegalArgumentException("No known anndata field with encoding \"" + encoding + "\" and version \"" + version + "\"");
	}
}
