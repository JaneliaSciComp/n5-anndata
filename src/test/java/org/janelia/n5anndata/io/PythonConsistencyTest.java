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

import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrReader;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrWriter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Named.named;

/**
 To run this test, you need to have python installed and the following packages
 available: anndata, numpy, scipy, pandas, zarr, h5py.
 - If run from an IDE, you need to add the python path to the environment variables.
 - If run from the command line, you need to have python in your path (e.g. the
 correct conda environment is activated).
 If the test is not able to run, it will be skipped.
 */
public class PythonConsistencyTest extends BaseIoTest {
	private static final N5Options options1D = new N5Options(new int[] { 100 }, new GzipCompression(), null);
	private static final N5Options options2D = new N5Options(new int[] { 100, 100 }, new GzipCompression(), null);

	@ParameterizedTest
	@MethodSource("extensions")
	public void consistency_with_python(final String extension)
			throws IOException {

		boolean canExecutePython;
		try {
			// by executing this no-op script, we make sure python and all dependencies are available
			executePythonScript("utils.py", "");
			canExecutePython = true;
		} catch (final Exception e) {
			canExecutePython = false;
		}
		Assumptions.assumeTrue(canExecutePython, "Could not find python and necessary dependencies, consistency test skipped.");

		final String pythonDataset = Paths.get(testDirectoryPath.toString(), "data_python") + extension;
		final String javaDataset = Paths.get(testDirectoryPath.toString(), "data_java") + extension;

		executePythonScript("generate_test_dataset.py", pythonDataset);
		resaveDataset(pythonDataset, javaDataset);
		executePythonScript("validate_anndata.py", javaDataset);

		deleteRecursively(Paths.get(pythonDataset));
		deleteRecursively(Paths.get(javaDataset));
	}

	private static void executePythonScript(final String script, final String args) {
		final String scriptBasePath = Paths.get("src", "test", "python").toString();
		final String scriptPath = Paths.get(scriptBasePath, script).toString();
		final String cmd = "python " + scriptPath + " " + args;
		try {
			final Process process = Runtime.getRuntime().exec(cmd);
			new BufferedReader(new InputStreamReader(process.getErrorStream())).lines().forEach(System.out::println);
			if (process.waitFor() != 0) {
				throw new RuntimeException("Python script failed: " + cmd);
			}
			process.destroy();
		} catch (final IOException | InterruptedException e) {
			throw new RuntimeException("Python script failed: " + cmd, e);
		}
	}

	private static void resaveDataset(final String pythonDataset, final String javaDataset)
			throws IOException {
		final N5Reader reader = getReaderFor(pythonDataset);
		final N5Writer writer = getWriterFor(javaDataset);

		if (! AnnDataUtils.isValidAnnData(reader)) {
			throw new IllegalArgumentException("Invalid AnnData dataset: " + pythonDataset);
		}

		AnnDataPath path = new AnnDataPath(AnnDataField.OBS, "_index");
		final List<String> obs_names = AnnDataUtils.readStringArray(reader, path);

		path = new AnnDataPath(AnnDataField.VAR, "_index");
		final List<String> var_names = AnnDataUtils.readStringArray(reader, path);

		AnnDataUtils.initializeAnnData(obs_names, var_names, writer, options1D);

		path = new AnnDataPath(AnnDataField.X);
		final Img<DoubleType> X = AnnDataUtils.readNumericalArray(reader, path);
		AnnDataUtils.writeNumericalArray(X, writer, path, options1D, AnnDataFieldType.CSR_MATRIX);

		path = new AnnDataPath(AnnDataField.OBSP, "rnd");
		final Img<DoubleType> csr = AnnDataUtils.readNumericalArray(reader, path);
		AnnDataUtils.writeNumericalArray(csr, writer, path, options2D, AnnDataFieldType.CSR_MATRIX);

		path = new AnnDataPath(AnnDataField.VARP, "rnd");
		final Img<ShortType> csc = AnnDataUtils.readNumericalArray(reader, path);
		AnnDataUtils.writeNumericalArray(csc, writer, path, options2D, AnnDataFieldType.CSC_MATRIX);

		path = new AnnDataPath(AnnDataField.OBS, "cell_type");
		final List<String> cell_type = AnnDataUtils.readStringArray(reader, path);
		AnnDataUtils.writeStringArray(cell_type, writer, path, options1D, AnnDataFieldType.CATEGORICAL_ARRAY);

		path = new AnnDataPath(AnnDataField.VAR, "gene_stuff1");
		final Img<IntType> genes1 = AnnDataUtils.readNumericalArray(reader, path);
		AnnDataUtils.writeNumericalArray(genes1, writer, path, options1D, AnnDataFieldType.DENSE_ARRAY);

		path = new AnnDataPath(AnnDataField.VAR, "gene_stuff2");
		final Img<LongType> genes2 = AnnDataUtils.readNumericalArray(reader, path);
		AnnDataUtils.writeNumericalArray(genes2, writer, path, options1D, AnnDataFieldType.DENSE_ARRAY);

		path = new AnnDataPath(AnnDataField.OBSM, "X_umap");
		final Img<DoubleType> umap1 = AnnDataUtils.readNumericalArray(reader, path);
		AnnDataUtils.writeNumericalArray(umap1, writer, path, options2D, AnnDataFieldType.DENSE_ARRAY);

		path = new AnnDataPath(AnnDataField.VARM, "X_umap");
		final Img<DoubleType> umap2 = AnnDataUtils.readNumericalArray(reader, path);
		AnnDataUtils.writeNumericalArray(umap2, writer, path, options2D, AnnDataFieldType.DENSE_ARRAY);

		path = new AnnDataPath(AnnDataField.UNS, "random");
		final Img<DoubleType> uns = AnnDataUtils.readNumericalArray(reader, path);
		AnnDataUtils.writeNumericalArray(uns, writer, path, options1D, AnnDataFieldType.DENSE_ARRAY);

		path = new AnnDataPath(AnnDataField.LAYERS, "log");
		final Img<FloatType> log = AnnDataUtils.readNumericalArray(reader, path);
		AnnDataUtils.writeNumericalArray(log, writer, path, options2D, AnnDataFieldType.CSR_MATRIX);

		AnnDataUtils.finalizeAnnData(writer);
		reader.close();
		writer.close();
	}

	protected static N5Reader getReaderFor(final String dataset) {
		if (dataset.endsWith(".h5ad")) {
			return new N5HDF5Reader(dataset);
		} else if (dataset.endsWith(".zarr")) {
			return new N5ZarrReader(dataset);
		} else {
			throw new IllegalArgumentException("Unknown file extension: " + dataset);
		}
	}

	protected static N5Writer getWriterFor(final String dataset) {
		if (dataset.endsWith(".h5ad")) {
			return new N5HDF5Writer(dataset);
		} else if (dataset.endsWith(".zarr")) {
			return new N5ZarrWriter(dataset);
		} else {
			throw new IllegalArgumentException("Unknown file extension: " + dataset);
		}
	}

	protected static List<Named<String>> extensions() {
		return Arrays.asList(
				named("HDF5", ".h5ad"),
				named("Zarr", ".zarr"));
	}
}
