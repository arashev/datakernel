package io.datakernel.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import static io.datakernel.config.Config.concatPath;
import static io.datakernel.util.Preconditions.checkArgument;

public class ConfigWithFullPath implements Config {
	private final String path;
	private final Config config;
	private final Map<String, Config> children;

	private ConfigWithFullPath(String path, Config config) {
		this.path = path;
		this.config = config;
		this.children = new LinkedHashMap<>();
		for (Map.Entry<String, Config> entry : config.getChildren().entrySet()) {
			this.children.put(entry.getKey(),
					new ConfigWithFullPath(concatPath(this.path, entry.getKey()), entry.getValue()));
		}
	}

	public static ConfigWithFullPath wrap(Config config) {
		return new ConfigWithFullPath("", config);
	}

	@Override
	public String getValue(String defaultValue) {
		return config.getValue(defaultValue);
	}

	@Override
	public Map<String, Config> getChildren() {
		return children;
	}

	@Override
	public Config provideNoKeyChild(String key) {
		checkArgument(!children.keySet().contains(key));
		return new ConfigWithFullPath(concatPath(this.path, key), config.provideNoKeyChild(key));
	}

	@Override
	public String getValue() throws NoSuchElementException {
		try {
			return config.getValue();
		} catch (NoSuchElementException e) {
			throw new NoSuchElementException(this.path);
		}
	}

}