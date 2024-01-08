import numpy as np
import pandas as pd
import anndata as ad
from scipy.sparse import csr_matrix, csc_matrix


def create_test_anndata():
    """
    Generate AnnData object (adapted from the tutorial) for testing purposes.

    Returns:
        adata: AnnData object
    """

    # use custom seed for reproducibility
    rng = np.random.RandomState(42)

    # X is a sparse matrix
    counts = csr_matrix(rng.poisson(1, size=(100, 2000)), dtype=np.float32)
    adata = ad.AnnData(counts)

    # sparse matrices
    adata.obsp["rnd"] = csr_matrix(rng.poisson(1, size=(adata.n_obs, adata.n_obs)), dtype=np.float64)
    adata.varp["rnd"] = csc_matrix(rng.poisson(1, size=(adata.n_vars, adata.n_vars)), dtype=np.int16)

    # string arrays
    adata.obs_names = [f"Cell_{i:d}" for i in range(adata.n_obs)]
    adata.var_names = [f"Gene_{i:d}" for i in range(adata.n_vars)]

    # categoricals
    ct = rng.choice(["B", "T", "Monocyte"], size=(adata.n_obs,))
    adata.obs["cell_type"] = pd.Categorical(ct)

    # dense arrays
    adata.var["gene_stuff1"] = np.arange(adata.n_vars, dtype=np.int32)
    adata.var["gene_stuff2"] = np.arange(adata.n_vars, dtype=np.int64)
    adata.obsm["X_umap"] = rng.normal(0, 1, size=(adata.n_obs, 2))
    adata.varm["X_umap"] = rng.normal(0, 1, size=(adata.n_vars, 3))

    # also store something in all remaining fields
    adata.uns["random"] = [1, 2, 3]
    adata.layers["log"] = np.log1p(adata.X)

    return adata
