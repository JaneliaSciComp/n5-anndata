package org.janelia.n5anndata.io;

import org.janelia.saalfeldlab.n5.Compression;

import java.util.concurrent.ExecutorService;


public class N5Options {
	int[] blockSize;
	Compression compression;
	ExecutorService exec;

	public N5Options(int[] blockSize, Compression compression, ExecutorService exec) {
		this.blockSize = blockSize;
		this.compression = compression;
		this.exec = exec;
	}
}
