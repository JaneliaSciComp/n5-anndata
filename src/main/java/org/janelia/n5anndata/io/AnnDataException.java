package org.janelia.n5anndata.io;

public class AnnDataException
		extends RuntimeException {
	public AnnDataException(final String message) {
		super(message);
	}

	public AnnDataException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
