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

import org.janelia.saalfeldlab.n5.N5Writer;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class StringIoTest extends BaseIoTest {

	@ParameterizedTest
	@MethodSource("datasetsWithDifferentBackends")
	public void reading_and_writing_from_list(final Supplier<N5Writer> writerSupplier) {
		final List<String> expected = Arrays.asList("", "a", "b", "cd", "efg", ":-þ");
		try (final N5Writer writer = writerSupplier.get()) {
			N5StringUtils.save(expected, writer, "test", new int[] {6});
			final List<String> actual = N5StringUtils.open(writer, "/test");
			assertArrayEquals(expected.toArray(), actual.toArray());
		}
	}

	@ParameterizedTest
	@MethodSource("datasetsWithDifferentBackends")
	public void multiple_chunks(final Supplier<N5Writer> writerSupplier) {
		final List<String> expected = Arrays.asList("", "a", "b", "cd", "efg", ":-þ");
		try (final N5Writer writer = writerSupplier.get()) {
			N5StringUtils.save(expected, writer, "test", new int[] {4});
			final List<String> actual = N5StringUtils.open(writer, "/test");
			assertArrayEquals(expected.toArray(), actual.toArray());
		}
	}

	@ParameterizedTest
	@MethodSource("datasetsWithDifferentBackends")
	public void larger_chunk_size(final Supplier<N5Writer> writerSupplier) {
		final List<String> expected = Arrays.asList("", "a", "b", "cd", "efg", ":-þ");
		try (final N5Writer writer = writerSupplier.get()) {
			N5StringUtils.save(expected, writer, "test", new int[] { 100 });
			final List<String> actual = N5StringUtils.open(writer, "/test");
			assertArrayEquals(expected.toArray(), actual.toArray());
		}
	}
}
