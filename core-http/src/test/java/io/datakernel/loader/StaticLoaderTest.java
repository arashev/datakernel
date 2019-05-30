package io.datakernel.loader;

import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.exception.StacklessException;
import io.datakernel.test.rules.ByteBufRule;
import io.datakernel.test.rules.EventloopRule;
import org.junit.ClassRule;
import org.junit.Test;

import java.nio.file.Paths;

import static io.datakernel.async.TestUtils.await;
import static io.datakernel.async.TestUtils.awaitException;
import static io.datakernel.loader.StaticLoader.NOT_FOUND_EXCEPTION;
import static org.junit.Assert.*;

public class StaticLoaderTest {
	@ClassRule
	public static final EventloopRule eventloopRule = new EventloopRule();

	@ClassRule
	public static final ByteBufRule byteBufRule = new ByteBufRule();

	@Test
	public void testMap() {
		StaticLoader staticLoader = StaticLoader.ofClassPath("/")
				.map(file -> file + ".txt");
		ByteBuf file = await(staticLoader.load("testFile"));
		assertTrue(file.readRemaining() > 0);
	}

	@Test
	public void testFileNotFoundClassPath() {
		StaticLoader staticLoader = StaticLoader.ofClassPath("/");
		StacklessException exception = awaitException(staticLoader.load("unknownFile.txt"));
		assertEquals(NOT_FOUND_EXCEPTION, exception);
	}

	@Test
	public void testFileNotFoundPath() {
		StaticLoader staticLoader = StaticLoader.ofPath(Paths.get("/"));
		StacklessException exception = awaitException(staticLoader.load("unknownFile.txt"));
		assertEquals(NOT_FOUND_EXCEPTION, exception);
	}

	@Test
	public void testLoadClassPathFile() {
		StaticLoader staticLoader = StaticLoader.ofClassPath("/");
		ByteBuf file = await(staticLoader.load("testFile.txt"));
		assertNotNull(file);
		assertTrue(file.readRemaining() > 0);
	}

	@Test
	public void testFilterFileClassPath() {
		StaticLoader staticLoader = StaticLoader.ofClassPath("/")
				.filter(file -> !file.equals("testFile.txt"));
		StacklessException exception = awaitException(staticLoader.load("testFile.txt"));
		assertEquals(NOT_FOUND_EXCEPTION, exception);
	}

	@Test
	public void testFilterFilePath() {
		StaticLoader staticLoader = StaticLoader.ofPath(Paths.get("/"))
				.filter(file -> !file.equals("testFile.txt"));
		StacklessException exception = awaitException(staticLoader.load("testFile.txt"));
		assertEquals(NOT_FOUND_EXCEPTION, exception);
	}
}
