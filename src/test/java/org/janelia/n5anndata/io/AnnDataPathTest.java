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
		assertEquals("test/foo/bar", actualPath.getSubPath());
		assertEquals("/obs/test/foo", actualPath.getParentPath());
	}

	@Test
	public void root_is_treated_correctly() {
		final String expectedParentPath = "/obs";
		final AnnDataPath actualPath = AnnDataPath.fromString(expectedParentPath);
		assertEquals(AnnDataField.OBS, actualPath.getField());
		assertEquals(AnnDataField.OBS.getPath(), actualPath.getLeaf());
		assertEquals("", actualPath.getSubPath());
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
		assertThrows(IllegalArgumentException.class, () -> AnnDataPath.fromString("/"), "Input: /");
		assertThrows(IllegalArgumentException.class, () -> AnnDataPath.fromString("//"), "Input: //");
	}
}
