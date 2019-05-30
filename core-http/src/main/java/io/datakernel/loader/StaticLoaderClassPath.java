package io.datakernel.loader;

import io.datakernel.async.Promise;
import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.http.HttpException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.NoSuchFileException;
import java.util.concurrent.Executor;

import static java.lang.ClassLoader.getSystemClassLoader;

class StaticLoaderClassPath implements StaticLoader {
	private static final String ROOT = "/";
	private static final int ROOT_OFFSET = 1;
	@Nullable
	private final Executor executor;
	private final ClassLoader classLoader;
	private final String root;

	private StaticLoaderClassPath(@Nullable Executor executor, @NotNull ClassLoader classLoader, @NotNull String root) {
		this.root = root;
		this.executor = executor;
		this.classLoader = classLoader;
	}

	public static StaticLoaderClassPath create(String root) {
		return create(null, getSystemClassLoader(), root);
	}

	public static StaticLoaderClassPath create(@Nullable Executor executor, @NotNull ClassLoader classLoader, @NotNull String root) {
		if (root.startsWith(ROOT)) {
			root = root.substring(ROOT_OFFSET);
		}
		if (!root.endsWith(ROOT) && root.length() > 0) {
			root = root + ROOT;
		}

		return new StaticLoaderClassPath(executor, classLoader, root);
	}

	@Override
	public Promise<ByteBuf> load(String name) {
		String path = root;
		int begin = 0;
		if (name.startsWith(ROOT)) {
			begin++;
		}
		path += name.substring(begin);
		if (!name.endsWith(ROOT)) {
			path += ROOT;
		}

		URL file = classLoader.getResource(path);

		if (file == null) {
			return Promise.ofException(NOT_FOUND_EXCEPTION);
		}

		return Promise.ofBlockingCallable(executor, () -> ByteBuf.wrapForReading(loadResource(file)))
				.thenEx((buf, e) ->
						Promise.of(buf, e instanceof NoSuchFileException ? NOT_FOUND_EXCEPTION : e));
	}

	private byte[] loadResource(URL file) throws IOException {
		try (InputStream in = file.openStream()) {
			// reading file as resource
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[4096];
			int size;
			while ((size = in.read(buffer)) != -1) {
				out.write(buffer, 0, size);
			}
			return out.toByteArray();
		}
	}
}
