package org.janelia.n5anndata.io;

import org.janelia.saalfeldlab.n5.Compression;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * Class representing the options for N5: the block size, compression type, and executor service.
 * The executor service is optional and can be null; if given, it is used to parallelize writing
 * for numerical arrays.
 *
 * @author Michael Innerberger
 */
public class N5Options {
	private final int[] blockSize;
	private final Compression compression;
	private final ExecutorService exec;


	/**
	 * Constructor for N5Options class. The executor is set to null.
	 *
	 * @param blockSize Array representing the block size
	 * @param compression Compression type
	 * @throws IllegalArgumentException if block size is not 1D or 2D
	 */
	public N5Options(final int[] blockSize, final Compression compression) {
		this(blockSize, compression, null);
	}

	/**
	 * Constructor for N5Options class.
	 *
	 * @param blockSize Array representing the block size
	 * @param compression Compression type
	 * @param exec Executor service
	 * @throws IllegalArgumentException if block size is not 1D or 2D
	 */
	public N5Options(final int[] blockSize, final Compression compression, final ExecutorService exec) {
		this.blockSize = Objects.requireNonNull(blockSize);
		this.compression = Objects.requireNonNull(compression);
		this.exec = exec;

		if (blockSize.length != 1 && blockSize.length != 2) {
			throw new IllegalArgumentException("Block size must be 1D or 2D");
		}
	}

	/**
	 * Getter for block size.
	 *
	 * @return Array representing the block size
	 */
	public int[] blockSize() {
		return blockSize;
	}

	/**
	 * Getter for {@link Compression} type.
	 *
	 * @return Compression type
	 */
	public Compression compression() {
		return compression;
	}

	/**
	 * Getter for {@link ExecutorService}.
	 *
	 * @return Executor service
	 */
	public ExecutorService executorService() {
		return exec;
	}

	/**
	 * Checks if executor service is null.
	 *
	 * @return true if executor service is set, false otherwise
	 */
	public boolean hasExecutorService() {
		return (exec == null);
	}

	/**
	 * If the block size is not already 1D, converts block size to 1D
	 * by multiplying the elements of the 2D block size.
	 *
	 * @return 1D array representing the block size
	 */
	public int[] blockSizeTo1D() {
		if (blockSize.length == 1) {
			return blockSize;
		} else {
			return new int[] {blockSize[0] * blockSize[1]};
		}
	}

	@Override
	public String toString() {
		return "blockSize: " + Arrays.toString(blockSize) + ", compression: " + compression + ", exec: " + exec;
	}

	@Override
	public int hashCode() {
		return Objects.hash(Arrays.hashCode(blockSize), compression, exec);
	}

	@Override
	public boolean equals(final Object o) {
		if (o == this) {
			return true;
		} else if (!(o instanceof N5Options)) {
			return false;
		} else {
			final N5Options other = (N5Options) o;
			return Arrays.equals(blockSize, other.blockSize) && compression.equals(other.compression) && exec.equals(other.exec);
		}
	}
}
