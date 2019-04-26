package io.global.common.ot;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.datakernel.async.Promise;
import io.datakernel.codec.StructuredCodec;
import io.datakernel.config.Config;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.exception.ParseException;
import io.datakernel.http.AsyncHttpServer;
import io.datakernel.http.MiddlewareServlet;
import io.datakernel.http.StaticServlet;
import io.datakernel.loader.StaticLoader;
import io.datakernel.loader.StaticLoaders;
import io.datakernel.ot.*;
import io.global.common.SimKey;
import io.global.ot.api.CommitId;
import io.global.ot.api.GlobalOTNode;
import io.global.ot.api.RepoID;
import io.global.ot.client.MyRepositoryId;
import io.global.ot.client.OTDriver;
import io.global.ot.client.OTRepositoryAdapter;
import io.global.ot.graph.OTGraphServlet;
import io.global.ot.http.OTNodeServlet;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.function.Function;

import static io.datakernel.codec.json.JsonUtils.fromJson;
import static io.datakernel.config.ConfigConverters.*;
import static io.datakernel.http.HttpMethod.GET;
import static io.datakernel.launchers.initializers.Initializers.ofHttpServer;
import static io.global.common.ExampleCommonModule.*;
import static io.global.launchers.GlobalConfigConverters.ofSimKey;
import static io.global.launchers.ot.GlobalOTConfigConverters.ofMyRepositoryId;
import static io.global.ot.graph.OTGraphServlet.COMMIT_ID_TO_STRING;
import static io.global.ot.util.BinaryDataFormats.REGISTRY;
import static java.util.Collections.emptySet;

public class OTCommonModule<D> extends AbstractModule {
	public static final StructuredCodec<CommitId> COMMIT_ID_CODEC = REGISTRY.get(CommitId.class);
	public static final RepoID DEMO_REPO_ID = RepoID.of(DEMO_PRIVATE_KEY.computePubKey(), "Chat Example");
	public static final Duration DEFAULT_PUSH_DELAY_DURATION = Duration.ofSeconds(1);

	@Provides
	@Singleton
	@Named("Example")
	AsyncHttpServer provideServer(Eventloop eventloop, MiddlewareServlet servlet,
			OTNodeServlet<CommitId, D, OTCommit<CommitId, D>> nodeServlet, Config config) {

		AsyncHttpServer asyncHttpServer = AsyncHttpServer.create(eventloop, servlet)
				.initialize(ofHttpServer(config.getChild("http")));

		nodeServlet.setCloseNotification(asyncHttpServer.getCloseNotification());

		return asyncHttpServer;
	}

	@Provides
	@Singleton
	MiddlewareServlet provideMiddlewareServlet(StaticServlet staticServlet, OTGraphServlet<CommitId, D> graphServlet,
			OTNodeServlet<CommitId, D, OTCommit<CommitId, D>> nodeServlet) {
		return MiddlewareServlet.create()
				.with(GET, "/graph", graphServlet)
				.with("/node", nodeServlet)
				.withFallback(staticServlet);
	}

	@Provides
	@Singleton
	OTGraphServlet<CommitId, D> provideGraphServlet(OTRepository<CommitId, D> repository, OTSystem<D> otSystem,
			Function<D, String> diffToString) {
		return OTGraphServlet.create(repository, otSystem, COMMIT_ID_TO_STRING, diffToString)
				.withCurrentCommit(request -> {
					try {
						return Promise.of(fromJson(COMMIT_ID_CODEC, request.getQueryParameter("id")));
					} catch (ParseException e) {
						return Promise.ofException(e);
					}
				});
	}

	@Provides
	@Singleton
	StaticServlet provideStaticServlet(Eventloop eventloop, Executor executor, Config config) {
		Path staticDir = config.get(ofPath(), "resources.path", DEFAULT_RESOURCES_PATH);
		StaticLoader resourceLoader = StaticLoaders.ofPath(executor, staticDir);
		return StaticServlet.create(eventloop, resourceLoader);
	}

	@Provides
	@Singleton
	OTNodeServlet<CommitId, D, OTCommit<CommitId, D>> provideNodeServlet(OTNode<CommitId, D, OTCommit<CommitId, D>> node,
			StructuredCodec<D> diffCodec, OTRepository<CommitId, D> repository, Config config) {
		Duration delay = config.get(ofDuration(), "push.delay", DEFAULT_PUSH_DELAY_DURATION);
		return OTNodeServlet.forGlobalNode(DelayedPushNode.create(node, delay), diffCodec, (OTRepositoryAdapter<D>) repository);
	}

	@Provides
	@Singleton
	OTNode<CommitId, D, OTCommit<CommitId, D>> provideNode(OTRepository<CommitId, D> repository, OTSystem<D> otSystem) {
		return OTNodeImpl.create(repository, otSystem);
	}

	@Provides
	@Singleton
	OTRepository<CommitId, D> provideRepository(Eventloop eventloop, OTDriver driver, MyRepositoryId<D> myRepositoryId) {
		return new OTRepositoryAdapter<>(driver, myRepositoryId, emptySet());
	}

	@Provides
	@Singleton
	OTDriver provideDriver(Eventloop eventloop, GlobalOTNode node, Config config) {
		SimKey simKey = config.get(ofSimKey(), "credentials.simKey", DEMO_SIM_KEY);
		return new OTDriver(node, simKey);
	}

	@Provides
	@Singleton
	MyRepositoryId<D> provideMyRepositoryId(Config config, StructuredCodec<D> diffCodec) {
		MyRepositoryId<D> DEMO_MY_REPOSITORY_ID = new MyRepositoryId<>(DEMO_REPO_ID, DEMO_PRIVATE_KEY, diffCodec);
		return config.get(ofMyRepositoryId(diffCodec), "credentials", DEMO_MY_REPOSITORY_ID);
	}

	@Provides
	@Singleton
	Executor provideExecutor(Config config) {
		return getExecutor(config.getChild("executor"));
	}
}