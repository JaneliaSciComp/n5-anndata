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


def _check(condition, name):
    if not condition:
        raise ValueError(f"Validation failed for '{name}'")


def compare_anndatas(expected, actual):
    """
    Compare two AnnData objects.

    Args:
        expected: AnnData object
        actual: AnnData object

    Throws:
        ValueError: if validation fails
    """

    # compare sparse matrices
    _check(np.allclose(expected.X.toarray(), actual.X.toarray()), "X (scipy csr, float32)")
    _check(np.allclose(expected.obsp["rnd"].toarray(), actual.obsp["rnd"].toarray()), "obsp (scipy csr, float64)")
    _check(np.allclose(expected.varp["rnd"].toarray(), actual.varp["rnd"].toarray()), "varp (scipy csc, int16)")

    # compare string arrays
    _check(np.array_equal(expected.obs_names, actual.obs_names), "obs_names")
    _check(np.array_equal(expected.var_names, actual.var_names), "var_names")

    # compare categoricals
    _check(np.array_equal(expected.obs["cell_type"], actual.obs["cell_type"]), "obs['cell_type'] (categorical)")

    # compare dense arrays
    _check(np.allclose(expected.var["gene_stuff1"], actual.var["gene_stuff1"]), "var['gene_stuff1'] (int32)")
    _check(np.allclose(expected.var["gene_stuff2"], actual.var["gene_stuff2"]), "var['gene_stuff2'] (int64)")
    _check(np.allclose(expected.obsm["X_umap"], actual.obsm["X_umap"]), "obsm['X_umap'] (float64)")
    _check(np.allclose(expected.varm["X_umap"], actual.varm["X_umap"]), "varm['X_umap'] (float64)")

    # compare remaining fields
    _check(np.array_equal(expected.uns["random"], actual.uns["random"]), "uns['random'] (float64)")
    _check(np.allclose(expected.layers["log"].toarray(), actual.layers["log"].toarray()), "layers['log'] (scipy csr, float32)")
