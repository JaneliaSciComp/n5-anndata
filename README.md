[![Build Status](https://github.com/JaneliaSciComp/n5-anndata/actions/workflows/build.yml/badge.svg)](https://github.com/JaneliaSciComp/n5-anndata/actions/workflows/build.yml)

# N5 AnnData Utilities
Convenience functionality for reading from and writing to [AnnData](https://anndata.readthedocs.io/en/latest/) files with Java.
By using the [N5 API](https://github.com/saalfeldlab/n5), the AnnData file can be stored as HDF5 (`.h5ad`), Zarr (`.zarr`), or in an N5 directory structure (`.n5ad`).
By loading numerical arrays into ImgLib2 data structures, even arrays that don't fit into memory can be processed easily.
Note that only HDF5 and Zarr are supported from the Python side, so far.


## Usage
Reading and writing are done via the [N5 API](https://github.com/saalfeldlab/n5).
The central functionality is provided by the `AnnDataUtils` class, which contains static methods for reading and writing AnnData files.


### Reading
The reading methods in this package generally require an `N5Reader` instance that points to an existing AnnData container and a path to the group within the container.
The data is read into an `Img<T>` in case of numerical data, or a `List<String>` in case of string data.

```java
// open an existing AnnData container, e.g. in HDF5 format
final N5Reader reader = new N5HDF5Reader("path/to/file.h5ad");

// read the central array 'X', the datatype has to be known (here, it's assumed to be float32)
final Img<FloatType> data = AnnDataUtils.readNumericalArray(reader, "X");

// read a string array from a sub-group of 'uns'
final List<String> data = AnnDataUtils.readStringArray(reader, "uns/mapping1/mapping2/some_string_array");

// get index of a dataset (e.g., 'obs')
final List<String> data = AnnDataUtils.readDataFrameIndex(reader, "obs");

// get number of variables
final long nVar = AnnDataUtils.getNVar(reader);
```


### Writing
In addition to an `N5Writer` instance and the path to the group within the container, the writing methods require a dataset (either `RandomAccessibleInterval<T>` or `List<String>`), some options for N5 (compression, block size, etc.) and sometimes an `AnnDataFieldType` that specifies how the data should be stored in the AnnData container.

```java
// create a new AnnData container in Zarr format
final N5Writer writer = new N5ZarrWriter("path/to/file.zarr");

// initialize N5 options: GZIP compression, block size 4096 or 512x512, no parallel writing
final N5Options options1D = new N5Options(new int[]{4096}, new GzipCompression());
final N5Options options2D = new N5Options(new int[]{512, 512}, new GzipCompression());

// initialize an AnnData container with given obs_names and var_names
AnnDataUtils.initializeAnnData(obs_names, var_names, writer, options1D);

// write a numerical array to a 'layer'
AnnDataUtils.writeNumericalArray(data, writer, "layer/log_scale", options2D, AnnDataFieldType.DENSE_ARRAY);

// create a dataframe in 'varm' with an index (List<String>) and write a categorical array to it
AnnDataUtils.createDataFrame(index, writer, "varm/metadata", options1D);
AnnDataUtils.writeStringArray(data, writer, "varm/metadata/barcodes", options1D, AnnDataFieldType.CATEGORICAL_ARRAY);

// finalize the AnnData container; this is needed for the container to be readable from Python
AnnDataUtils.finalizeAnnData(writer);
```


## Details
There are some details to be aware of when using this package more extensively.


### AnnData path
AnnData only permits a limited set of paths directly under the root group (`obs`, `var`, `obsm`, `varm`, `obsp`, `varp`, `uns`, `X`, `layers`).
This package uses the enum class `AnnDataPath` to define valid paths.
Such a path consists of a field (e.g., `varm`) and a sub-path (e.g., `metadata/barcodes`).
To create a valid path, use the constructor `new AnnDataPath(AnnDataField.VARM, "metadata/barcodes")`, or the static method `AnndataPath.fromString("varm/metadata/barcodes")`, which throws an exception if the path is invalid.


### Supported data types
This package only supports a subset of the data types written by AnnData.
In particular, scalars, nullable integers and booleans, and awkward arrays are not supported.
The following table shows the supported data types and how they are represented in Java.
Note that the classes for sparse arrays derive from `Img<T>` and have a generic parameter `I` for the type of the index.
Conversion for sparse matrices is not done automatically, but can be done using the `SparseArray.convertToSparse` static method.
For categorical arrays, conversion is done automatically.

| AnnData data type | Java data type             | AnnDataFieldType    |
|-------------------|----------------------------|---------------------|
| `array`           | `Img<T>`                   | `DENSE_ARRAY`       |
| `csr_matrix`      | `CsrMatrix<T,I>`           | `CSR_MATRIX`        |
| `csc_matrix`      | `CscMatrix<T,I>`           | `CSC_MATRIX`        |
| `dataframe`       | columns treated separately | `DATA_FRAME`        |
| `dict`            | group in a container       | `MAPPING`           |
| `categorical`     | `List<T>`                  | `CATEGORICAL_ARRAY` |
| `string-array`    | `List<T>`                  | `STRING_ARRAY`      |


### Enforcing constraints
AnnData imposes some constraints on the data that is stored in the container.
These can be based on the field type (e.g., only `dense`, `csr_matrix`, or `csc_matrix` can be stored in `X`) or on the dimension (e.g., only arrays of size `n_obs x m`, where $m > 1$ can be stored in `obsm`).
A special case are data frames, which don't permit sparse arrays and only allow for arrays of size `n x 1`, where `n` must be uniform over the whole dataframe.

The constraints are enforce while writing by a `Checker`, which can be set via `AnnDataUtils.setChecker`.
Possible checkers are `Checker.STRICT` (default), `Checker.ONLY_TYPE`, `Checker.ONLY_DIMENSION`, and `Checker.NONE`.
Please note that only data that is written using `Checker.STRICT` is guaranteed to be readable by Python.


### Python compatibility
While data is mostly compatible with the latest version of AnnData from Python, there are some caveats, mostly related to HDF5.

#### HDF5 housekeeping group
[JHDF5](https://sissource.ethz.ch/sispub/jhdf5) (which is used by N5-HDF5 under the hood) creates a housekeeping group `/__DATA_TYPES__` in the root group of the HDF5 file.
When this group is present, AnnData will throw an error when trying to read the file.
To avoid this, the group is deleted when finalizing the AnnData container.

#### HDF5 and strings
It seems that `h5py` reads most strings (i.e., attribute values that are strings as well as string datasets) written by JHDF5 as `bytes` instead of `str`.
This might need some manual conversion using `.decode("utf-8")` when reading the file from the Python side.
