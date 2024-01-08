import sys
import os

from utils import create_test_anndata


adata = create_test_anndata()

# write to disk in different formats
test_path = sys.argv[1]
sys.stderr.write(f'Writing anndata files from python to: {test_path}\n')

adata.write_h5ad(os.path.join(test_path, 'data.h5ad'), compression="gzip")
adata.write_zarr(os.path.join(test_path, 'data.zarr'))
