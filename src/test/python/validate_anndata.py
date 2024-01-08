import sys

import anndata as ad

from utils import create_test_anndata


expected = create_test_anndata()

# read from disk and validate
file_path = sys.argv[1]
extension = file_path.split(".")[-1]
match extension:
    case "h5ad":
        actual = ad.read_h5ad(file_path)
    case "zarr":
        actual = ad.read_zarr(file_path)
    case _:
        sys.exit(f"Unknown file extension: {extension}")

