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

import org.janelia.n5anndata.io.AnnDataException;
import org.janelia.n5anndata.io.AnnDataField;
import org.janelia.n5anndata.io.AnnDataFieldType;
import org.janelia.n5anndata.io.AnnDataPath;
import org.janelia.n5anndata.io.AnnDataUtils;
import org.janelia.saalfeldlab.n5.N5Reader;

import java.util.Arrays;
import java.util.List;

import static org.janelia.n5anndata.io.AnnDataFieldType.*;


/**
 * A Checker that performs checks on both type and dimension constraints.
 *
 * @author Michael Innerberger
 */
class StrictChecker implements Checker {
	private static final List<AnnDataFieldType> ALLOWED_DATA_FRAME_TYPES = Arrays.asList(STRING_ARRAY, CATEGORICAL_ARRAY, DENSE_ARRAY);

	@Override
	public void check(final N5Reader reader, final AnnDataPath path, final AnnDataFieldType type, final long[] shape) {
		final AnnDataPath parentPath = path.getParentPath();
		final AnnDataFieldType parentType = AnnDataUtils.getFieldType(reader, parentPath);

		if (! satisfiesTypeConstraints(path.getField(), type, parentType)) {
			final String msg = String.format("Cannot put '%s' at '%s' because it is not allowed by '%s' or the parent type ('%s')",
											 type, path, path.getField(), parentType);
			throw new AnnDataException(msg);
		}

		final long nObs = AnnDataUtils.getNObs(reader);
		final long nVar = AnnDataUtils.getNVar(reader);

		if (parentType == DATA_FRAME) {
			final long indexSize = AnnDataUtils.getDataFrameIndexSize(reader, parentPath);
			if (! satisfiesDataFrameConstraints(shape, indexSize)) {
				final String msg = String.format("Dimensions %s not compatible with data frame constraints of '%s' (index size=%d)",
												 Arrays.toString(shape), parentPath, indexSize);
				throw new AnnDataException(msg);
			}
		} else {
			if (! satisfiesDimensionConstraints(path.getField(), shape, nObs, nVar)) {
				final String msg = String.format("Dimensions %s not compatible with nObs=%d and nVar=%d because of the constraints enforced by '%s'",
												 Arrays.toString(shape), nObs, nVar, path.getField());
				throw new AnnDataException(msg);
			}
		}
	}

	/**
	 * Checks type constraints for an array to be written.
	 *
	 * @param field the AnnDataField in which the array should be placed
	 * @param type the AnnDataFieldType of the array to be written
	 * @param parentType the type of the immediate parent of the target path
	 */
	protected boolean satisfiesTypeConstraints(final AnnDataField field, final AnnDataFieldType type, final AnnDataFieldType parentType) {
		if (parentType == ANNDATA) {
			return field.canBeA(type);
		} else if (parentType == DATA_FRAME) {
			return ALLOWED_DATA_FRAME_TYPES.contains(type);
		} else {
			return field.canHaveAsChild(type);
		}
	}

	/**
	 * Checks dimension constraints for an array to be written.
	 *
	 * @param field the AnnDataField in which the array should be placed
	 * @param shape the shape of the array to be written
	 * @param nObs the number of observations of the AnnData file
	 * @param nVar the number of variables of the AnnData file
	 */
	protected boolean satisfiesDimensionConstraints(final AnnDataField field, final long[] shape, final long nObs, final long nVar) {
		switch (field) {
			case X: case LAYERS:
				return (is2D(shape) && shape[0] == nObs && shape[1] == nVar);
			case OBS: case VAR:
				return true; // constraints are given by the data frame
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

	/**
	 * Checks constraints for an array to be written to a dataframe (1D and fits the dataframe size).
	 *
	 * @param shape the shape of the array to be written
	 * @param indexSize the number of variables of the dataframe
	 */
	protected boolean satisfiesDataFrameConstraints(final long[] shape, final long indexSize) {
		return (is1D(shape) && shape[0] == indexSize);
	}

	private static boolean is1D(final long[] shape) {
		return (shape.length == 1 || (shape.length == 2 && shape[1] == 1));
	}

	private static boolean is2D(final long[] shape) {
		return (shape.length == 2);
	}
}
