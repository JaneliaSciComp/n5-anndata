import sys

from utils import create_test_anndata


adata = create_test_anndata()

# write to disk in different formats
file_path = sys.argv[1]
sys.stderr.write(f'Writing anndata file from python to: {file_path}\n')

extension = file_path.split(".")[-1]
match extension:
    case "h5ad":
        adata.write_h5ad(file_path, compression="gzip")
    case "zarr":
        adata.write_zarr(file_path)
    case _:
        sys.exit(f"Unknown file extension: {extension}")
