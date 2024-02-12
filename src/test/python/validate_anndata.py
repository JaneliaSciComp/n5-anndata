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
