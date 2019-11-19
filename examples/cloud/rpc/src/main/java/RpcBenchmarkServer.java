import io.datakernel.common.MemSize;
import io.datakernel.config.Config;
import io.datakernel.config.ConfigModule;
import io.datakernel.datastream.csp.ChannelSerializer;
import io.datakernel.di.annotation.Inject;
import io.datakernel.di.annotation.Provides;
import io.datakernel.di.annotation.Qualifier;
import io.datakernel.di.core.Key;
import io.datakernel.di.module.Module;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.launcher.Launcher;
import io.datakernel.launcher.OnStart;
import io.datakernel.promise.Promise;
import io.datakernel.rpc.server.RpcServer;
import io.datakernel.service.ServiceGraphModule;

import java.util.concurrent.CompletionStage;

import static io.datakernel.config.ConfigConverters.*;
import static io.datakernel.di.module.Modules.combine;
import static io.datakernel.eventloop.FatalErrorHandlers.rethrowOnAnyError;

public class RpcBenchmarkServer extends Launcher {
	private final static int SERVICE_PORT = 25565;

	@Inject
	RpcServer rpcServer;

	@Provides
	@Qualifier("server")
	Eventloop eventloopServer() {
		return Eventloop.create()
				.withFatalErrorHandler(rethrowOnAnyError());
	}

	@Provides
	public RpcServer rpcServer(@Qualifier("server") Eventloop eventloop, Config config) {
		return RpcServer.create(eventloop)
				.withStreamProtocol(
						config.get(ofMemSize(), "rpc.defaultPacketSize", MemSize.kilobytes(256)),
						ChannelSerializer.MAX_SIZE_1,
						config.get(ofBoolean(), "rpc.compression", false))
				.withListenPort(config.get(ofInteger(), "rpc.server.port"))
				.withMessageTypes(Integer.class)
				.withHandler(Integer.class, Integer.class, req -> Promise.of(req * 2));

	}

	@Provides
	Config config() {
		return Config.create()
				.with("rpc.server.port", "" + SERVICE_PORT)
				.overrideWith(Config.ofProperties(System.getProperties()).getChild("config"));
	}

	@Override
	protected Module getModule() {
		return combine(
				ServiceGraphModule.create(),
				ConfigModule.create()
						.printEffectiveConfig()
						.rebindImport(new Key<CompletionStage<Void>>() {}, new Key<CompletionStage<Void>>(OnStart.class) {})
		);
	}

	@Override
	protected void run() throws Exception {
		awaitShutdown();
	}

	public static void main(String[] args) throws Exception {
		RpcBenchmarkServer benchmark = new RpcBenchmarkServer();
		benchmark.launch(args);
	}
}
