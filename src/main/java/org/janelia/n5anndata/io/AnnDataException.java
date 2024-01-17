package org.janelia.n5anndata.io;

/**
 * Custom exception class that is thrown when AnnData logic is violated.
 *
 * @author Michael Innerberger
 */
public class AnnDataException
		extends RuntimeException {
	public AnnDataException(final String message) {
		super(message);
	}

	public AnnDataException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
