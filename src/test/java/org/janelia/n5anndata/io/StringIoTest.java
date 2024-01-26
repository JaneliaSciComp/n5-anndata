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
import static org.junit.jupiter.api.Assertions.fail;

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
		} catch (final Exception e) {
			fail("Could not write / read file: ", e);
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
		} catch (final Exception e) {
			fail("Could not write / read file: ", e);
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
		} catch (final Exception e) {
			fail("Could not write / read file: ", e);
		}
	}
}
