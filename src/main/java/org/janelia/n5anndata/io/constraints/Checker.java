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
