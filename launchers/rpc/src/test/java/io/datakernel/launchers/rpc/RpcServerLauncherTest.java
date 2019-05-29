package io.datakernel.launchers.rpc;

import io.datakernel.di.module.AbstractModule;
import io.datakernel.di.module.Module;
import io.datakernel.di.module.Provides;
import io.datakernel.rpc.server.RpcServer;
import io.datakernel.util.Initializer;
import org.junit.Test;

public class RpcServerLauncherTest {
	@Test
	public void testsInjector() {
		RpcServerLauncher launcher = new RpcServerLauncher() {
			@Override
			protected Module getBusinessLogicModule() {
				return new AbstractModule() {
					@Provides
					Initializer<RpcServer> rpcServerInitializer() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
		launcher.testInjector();
	}
}
