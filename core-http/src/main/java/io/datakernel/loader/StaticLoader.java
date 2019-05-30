package io.datakernel.loader;

import io.datakernel.async.Promise;
import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.exception.StacklessException;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

public interface StaticLoader {
	StacklessException NOT_FOUND_EXCEPTION = new StacklessException();

	Promise<ByteBuf> load(String path);

	default StaticLoader filter(Predicate<String> predicate) {
		return path -> predicate.test(path) ?
				load(path) :
				Promise.ofException(NOT_FOUND_EXCEPTION);
	}

	default StaticLoader map(Function<String, String> fn) {
		return path -> this.load(fn.apply(path));
	}

	default StaticLoader subfolder(String subfolder) {
		String folder = subfolder.endsWith("/") ? subfolder : subfolder + '/';
		return map(name -> folder + name);
	}

	default StaticLoader cached() {
		return cacheOf(this);
	}

	default StaticLoader cached(Map<String, byte[]> map) {
		return cacheOf(this, map);
	}

	static StaticLoader cacheOf(StaticLoader loader) {
		return cacheOf(loader, new HashMap<>());
	}

	static StaticLoader cacheOf(StaticLoader loader, Map<String, byte[]> map) {
		return cacheOf(loader, map::get, map::put);
	}

	static StaticLoader cacheOf(StaticLoader loader, Function<String, byte[]> get, BiConsumer<String, byte[]> put) {
		return new StaticLoaderCache(loader, get, put);
	}

	static StaticLoader ofClassPath(String root) {
		return StaticLoaderClassPath.create(root);
	}

	static StaticLoader ofClassPath(Executor executor, ClassLoader classLoader, String root) {
		return StaticLoaderClassPath.create(executor, classLoader, root);
	}

	static StaticLoader ofPath(Path dir) {
		return StaticLoaderFileReader.create(dir);
	}

	static StaticLoader ofFile(File dir) {
		return StaticLoaderFileReader.create(dir.toPath());
	}

}
