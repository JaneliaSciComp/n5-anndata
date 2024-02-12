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

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class AnnDataPathTest {
	@Test
	public void basic_constructor_is_correct() {
		final AnnDataPath actualPath = new AnnDataPath(AnnDataField.OBS, "test", "foo");
		final String expectedPath = "/obs/test/foo";
		assertEquals(expectedPath, actualPath.toString());
	}

	@Test
	public void correct_path_from_string() {
		final String expectedPath = "/obs/test/foo";
		final AnnDataPath actualPath = AnnDataPath.fromString(expectedPath);
		assertEquals(expectedPath, actualPath.toString());
	}

	@Test
	public void correct_path_parts() {
		final String expectedParentPath = "/obs/test/foo/bar";
		final AnnDataPath actualPath = AnnDataPath.fromString(expectedParentPath);
		assertEquals(AnnDataField.OBS, actualPath.getField());
		assertEquals("bar", actualPath.getLeaf());
		assertEquals("test/foo/bar", actualPath.keysAsString());
		assertEquals("/obs/test/foo", actualPath.getParentPath().toString());
	}

	@Test
	public void root_is_treated_correctly() {
		final String expectedParentPath = "/obs";
		final AnnDataPath actualPath = AnnDataPath.fromString(expectedParentPath);
		assertEquals(AnnDataField.OBS, actualPath.getField());
		assertEquals(AnnDataField.OBS.getPath(), actualPath.getLeaf());
		assertEquals("", actualPath.keysAsString());
		assertEquals(AnnDataPath.ROOT, actualPath.getParentPath());
	}

	@Test
	public void correct_append() {
		final String expectedPath = "/obs/test/foo/bar";
		final AnnDataPath actualPath = AnnDataPath.fromString("/obs/test").append("foo", "bar");
		assertEquals(expectedPath, actualPath.toString());
	}

	@Test
	public void wrong_path_throws_error() {
		final String wrongPath = "/obsx/test/foo";
		assertThrows(IllegalArgumentException.class, () -> AnnDataPath.fromString(wrongPath), "Input: " + wrongPath);
		assertThrows(IllegalArgumentException.class, () -> AnnDataPath.fromString(""), "Input: empty string");
		assertThrows(IllegalArgumentException.class, () -> AnnDataPath.fromString("//"), "Input: //");
	}
}
