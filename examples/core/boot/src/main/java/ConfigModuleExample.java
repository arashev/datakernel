import io.datakernel.config.Config;
import io.datakernel.di.core.Injector;
import io.datakernel.di.module.Module;

import java.net.InetAddress;

import static io.datakernel.config.ConfigConverters.ofInetAddress;
import static io.datakernel.config.ConfigConverters.ofInteger;

//[START EXAMPLE]
public final class ConfigModuleExample {
	private static final String PROPERTIES_FILE = "example.properties";

	public static void main(String[] args) {
		Injector injector = Injector.of(Module.create()
				.bind(Config.class).to(() -> Config.ofClassPathProperties(PROPERTIES_FILE))
				.bind(String.class).to(c -> c.get("phrase"), Config.class)
				.bind(Integer.class).to(c -> c.get(ofInteger(), "number"), Config.class)
				.bind(InetAddress.class).to(c -> c.get(ofInetAddress(), "address"), Config.class));

		System.out.println(injector.getInstance(String.class));
		System.out.println(injector.getInstance(Integer.class));
		System.out.println(injector.getInstance(InetAddress.class));
	}
}
//[END EXAMPLE]
