import sys

import anndata as ad

from utils import create_test_anndata, compare_anndatas


expected = create_test_anndata()

# read from disk and validate
file_path = sys.argv[1]
sys.stderr.write(f'Reading anndata file for validation: {file_path}\n')

extension = file_path.split(".")[-1]
try:
    match extension:
        case "h5ad":
            actual = ad.read_h5ad(file_path)
            compare_anndatas(expected, actual, string_decoder=lambda x: x.decode("utf-8"), string_encoder=lambda x: x.encode("utf-8"))
        case "zarr":
            actual = ad.read_zarr(file_path)
            compare_anndatas(expected, actual)
        case _:
            sys.exit(f"Unknown file extension: {extension}")
except Exception as e:
    sys.exit(f"Validation failed: {e}")

sys.stderr.write("Validation successful\n")
