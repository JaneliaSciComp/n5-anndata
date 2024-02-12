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
package org.janelia.n5anndata.io.constraints;

import org.janelia.n5anndata.io.AnnDataFieldType;
import org.janelia.n5anndata.io.AnnDataPath;
import org.janelia.saalfeldlab.n5.N5Reader;


/**
 * Interface for checking constraints when writing arrays to an AnnData file.
 * AnnData fields constrain the type of data that can be written to them: only
 * certain types of data with certain dimensions can be written to a given field.
 * <p>
 * The Checker may require to read additional data from the N5Reader to perform
 * the necessary checks.
 * <p>
 * Provides several default implementations for different types of checks.
 *
 * @author Michael Innerberger
 */
public interface Checker {

	/**
	 * A Checker that performs no checks (in particular, also doesn't read any data).
	 */
	Checker NONE = new NoChecker();

	/**
	 * A Checker that only checks the type constraints.
	 */
	Checker ONLY_TYPE = new TypeOnlyChecker();

	/**
	 * A Checker that only checks the dimension (including dataframe) constraints.
	 */
	Checker ONLY_DIMENSION = new DimensionOnlyChecker();

	/**
	 * A Checker that performs checks on both type and dimension constraints.
	 */
	Checker STRICT = new StrictChecker();


	/**
	 * Checks constraints on the given AnnDataPath with given field type and shape.
	 *
	 * @param reader the N5Reader to be checked
	 * @param path the AnnDataPath to be checked
	 * @param type the AnnDataFieldType to be checked
	 * @param shape the shape to be checked
	 */
	void check(N5Reader reader, AnnDataPath path, AnnDataFieldType type, long[] shape);

	/**
	 * Checks constraints on the given path (as String) with given field type and shape.
	 *
	 * @param reader the N5Reader to be checked
	 * @param path the AnnDataPath to be checked
	 * @param type the AnnDataFieldType to be checked
	 * @param shape the shape to be checked
	 */
	default void check(final N5Reader reader, final String path, final AnnDataFieldType type, final long[] shape) {
		check(reader, AnnDataPath.fromString(path), type, shape);
	}
}
