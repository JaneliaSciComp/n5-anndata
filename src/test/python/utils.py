###
# #%L
# N5 AnnData Utilities
# %%
# Copyright (C) 2023 - 2024 Howard Hughes Medical Institute
# %%
# Redistribution and use in source and binary forms, with or without modification,
# are permitted provided that the following conditions are met:
# 
# 1. Redistributions of source code must retain the above copyright notice, this
#    list of conditions and the following disclaimer.
# 
# 2. Redistributions in binary form must reproduce the above copyright notice,
#    this list of conditions and the following disclaimer in the documentation
#    and/or other materials provided with the distribution.
# 
# 3. Neither the name of the HHMI nor the names of its contributors
#    may be used to endorse or promote products derived from this software without
#    specific prior written permission.
# 
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
# ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
# WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
# IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
# INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
# BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
# DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
# LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
# OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.
# #L%
###
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


def compare_anndatas(expected, actual, string_decoder=lambda x: x, string_encoder=lambda x: x):
    """
    Compare two AnnData objects.

    Args:
        expected: AnnData object
        actual: AnnData object
        string_decoder: function to decode strings (e.g. UTF-8)
        string_encoder: function to encode strings (e.g. UTF-8)

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
    _check(np.array_equal(expected.obs["cell_type"], [string_decoder(s) for s in actual.obs[string_encoder("cell_type")]]), "obs['cell_type'] (categorical)")

    # compare dense arrays
    _check(np.allclose(expected.var["gene_stuff1"], actual.var[string_encoder("gene_stuff1")]), "var['gene_stuff1'] (int32)")
    _check(np.allclose(expected.var["gene_stuff2"], actual.var[string_encoder("gene_stuff2")]), "var['gene_stuff2'] (int64)")
    _check(np.allclose(expected.obsm["X_umap"], actual.obsm["X_umap"]), "obsm['X_umap'] (float64)")
    _check(np.allclose(expected.varm["X_umap"], actual.varm["X_umap"]), "varm['X_umap'] (float64)")

    # compare remaining fields
    _check(np.array_equal(expected.uns["random"], actual.uns["random"]), "uns['random'] (float64)")
    _check(np.allclose(expected.layers["log"].toarray(), actual.layers["log"].toarray()), "layers['log'] (scipy csr, float32)")
