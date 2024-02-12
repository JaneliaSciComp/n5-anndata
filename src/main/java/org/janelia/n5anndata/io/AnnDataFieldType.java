/*-
 * #%L
 * N5 AnnData Utilities
 * %%
 * Copyright (C) 2023 - 2024 Howard Hughes Medical Institute
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the HHMI nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.janelia.n5anndata.io;


/**
 * This enum represents the different types of fields in an AnnData structure.
 * Each field type has an encoding and a version that are written to the groups
 * and arrays stored on disk.
 * <p>
 * Note that, right now, scalars and nullable arrays are not supported.
 *
 * @author Michael Innerberger
 */
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

	/**
	 * Returns the encoding of the field type.
	 *
	 * @return The encoding of the field type.
	 */
	public String getEncoding() {
		return encoding;
	}

	/**
	 * Returns the version of the field type.
	 *
	 * @return The version of the field type.
	 */
	public String getVersion() {
		return version;
	}

	@Override
	public String toString() {
		return "encoding: " + encoding + ", version: " + version;
	}

	/**
	 * Ensures that the given type is a numerical array type (dense, csr, or csc).
	 *
	 * @param type The type to check.
	 * @throws AnnDataException If the type is not a numerical array type.
	 */
	public static void ensureNumericalArray(final AnnDataFieldType type) {
		if (type != DENSE_ARRAY && type != CSR_MATRIX && type != CSC_MATRIX) {
			throw new AnnDataException("Numerical array type expected, but got " + type);
		}
	}

	/**
	 * Ensures that the given type is a string array type (string or categorical).
	 *
	 * @param type The type to check.
	 * @throws AnnDataException If the type is not a string array type.
	 */
	public static void ensureStringArray(final AnnDataFieldType type) {
		if (type != STRING_ARRAY && type != CATEGORICAL_ARRAY) {
			throw new AnnDataException("String array type expected, but got " + type);
		}
	}

	/**
	 * Returns the field type for the given encoding and version.
	 *
	 * @param encoding The encoding of the field type.
	 * @param version The version of the field type.
	 * @return The field type for the given encoding and version.
	 * @throws IllegalArgumentException If no known field type matches the given encoding and version.
	 */
	public static AnnDataFieldType fromString(final String encoding, final String version) {
		if (encoding == null || version == null)
			return MISSING;

		for (final AnnDataFieldType type : values())
			if (type.encoding.equals(encoding) && type.version.equals(version))
				return type;

		throw new IllegalArgumentException("No known anndata field with encoding \"" + encoding + "\" and version \"" + version + "\"");
	}
}
