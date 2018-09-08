package io.datakernel.remotefs;

import ch.qos.logback.classic.Level;
import io.datakernel.async.EventloopTaskScheduler;
import io.datakernel.async.Stages;
import io.datakernel.eventloop.AbstractServer;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.stream.processor.ByteBufRule;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.datakernel.eventloop.FatalErrorHandlers.rethrowOnAnyError;
import static io.datakernel.remotefs.ServerSelector.RENDEZVOUS_HASH_SHARDER;
import static io.datakernel.test.TestUtils.assertComplete;
import static io.datakernel.test.TestUtils.enableLogging;

public class TestRepartitionController {
	public static final int CLIENT_SERVER_PAIRS = 10;

	private final Path[] serverStorages = new Path[CLIENT_SERVER_PAIRS];
	private List<RemoteFsServer> servers;

	@Rule
	public final TemporaryFolder tmpFolder = new TemporaryFolder();

	@Rule
	public final ByteBufRule byteBufs = new ByteBufRule();

	private Eventloop eventloop;
	private Path localStorage;
	private RemoteFsClusterClient cluster;
	private RemoteFsRepartitionController controller;
	private EventloopTaskScheduler scheduler;

	@Before
	public void setup() throws IOException, InterruptedException {

		Runtime.getRuntime().exec("rm -r /tmp/TESTS").waitFor();

		ExecutorService executor = Executors.newCachedThreadPool();
		eventloop = Eventloop.create().withCurrentThread().withFatalErrorHandler(rethrowOnAnyError());

		servers = new ArrayList<>(CLIENT_SERVER_PAIRS);

		Map<Object, FsClient> clients = new HashMap<>(CLIENT_SERVER_PAIRS);

//		localStorage = tmpFolder.getRoot().toPath().resolve("local");
		localStorage = Paths.get("/tmp/TESTS/local");
		Files.createDirectories(localStorage);
		LocalFsClient localFsClient = LocalFsClient.create(eventloop, executor, localStorage);

		Object localPartitionId = "local";
		clients.put(localPartitionId, localFsClient);

		for (int i = 0; i < CLIENT_SERVER_PAIRS; i++) {
			InetSocketAddress address = new InetSocketAddress("localhost", 5560 + i);

//			serverStorages[i] = tmpFolder.getRoot().toPath().resolve("storage_" + i);
			serverStorages[i] = Paths.get("/tmp/TESTS/storage_" + i);

			Files.createDirectories(serverStorages[i]);

			clients.put("server_" + i, RemoteFsClient.create(eventloop, address));

			RemoteFsServer server = RemoteFsServer.create(eventloop, executor, serverStorages[i]).withListenAddress(address);
			server.listen();
			servers.add(server);
		}

		cluster = RemoteFsClusterClient.create(eventloop, clients)
				.withReplicationCount(3)
				.withServerSelector(RENDEZVOUS_HASH_SHARDER);

		controller = RemoteFsRepartitionController.create(localPartitionId, cluster);

		scheduler = EventloopTaskScheduler.create(eventloop, () -> cluster.checkDeadPartitions())
				.withInterval(Duration.ofMillis(1000));

		scheduler.start();

		eventloop.delay(200, () -> {
			System.out.println("Closing server_2");
			servers.get(2).close().thenRun(() -> System.out.println("server_2 closed indeed"));
			System.out.println("Closing server_4");
			servers.get(4).close().thenRun(() -> System.out.println("server_4 closed indeed"));
			System.out.println("Closing server_7");
			servers.get(7).close().thenRun(() -> System.out.println("server_7 closed indeed"));
			System.out.println("Closing server_8");
			servers.get(8).close().thenRun(() -> System.out.println("server_8 closed indeed"));
			System.out.println("Closing server_9");
			servers.get(9).close().thenRun(() -> System.out.println("server_9 closed indeed"));
			eventloop.delay(200, () -> {
				try {
					System.out.println("Starting server_7 again");
					servers.get(7).listen();
					System.out.println("Starting server_2 again");
					servers.get(2).listen();
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
		});
	}

	@After
	public void tearDown() {
		scheduler.stop();
		servers.forEach(AbstractServer::close);
	}

	private void testN(int n, int minSize, int maxSize) throws IOException {
		enableLogging(Logger.ROOT_LOGGER_NAME, Level.WARN);
		enableLogging("io.datakernel.remotefs", Level.TRACE);
		enableLogging("io.datakernel.remotefs", Level.TRACE);

		long start = System.nanoTime();

		int delta = maxSize - minSize;
		Random rng = new Random(7L);
		for (int i = 0; i < n; i++) {
			byte[] data = new byte[minSize + (delta <= 0 ? 0 : rng.nextInt(delta))];
			rng.nextBytes(data);
			Files.write(localStorage.resolve("file_" + i + ".txt"), data);
		}

		System.out.println("Created local files in " + ((System.nanoTime() - start) / 1e6) + " ms");

		long start2 = System.nanoTime();

		controller.repartition()
				.whenComplete(assertComplete($ -> {
					scheduler.stop();
					double ms = (System.nanoTime() - start2) / 1e6;
					System.out.println(String.format("Done repartitioning in %.2f ms", ms));
					Stages.toList(cluster.getAliveClients().values().stream().map(fsClient -> fsClient.list().toTry()))
							.thenApply(lss -> lss.stream().mapToLong(ls -> {
								List<FileMetadata> mss = ls.getOrNull();
								return mss == null ? 0 : mss.stream().mapToLong(FileMetadata::getSize).sum();
							}).sum())
							.whenComplete(assertComplete(bytes -> {
								System.out.println(String.format("%d overall bytes", bytes));
								System.out.println(String.format("Average speed was %.2f mbit/second", bytes / (1 << 17) * (1000 / ms)));
								servers.forEach(AbstractServer::close);
							}));
				}));

		eventloop.run();
	}

	@Test
	@Ignore
	public void testTest() throws IOException {
		testN(1, 10 * 1024 * 1024, 50 * 1024 * 1024);
	}

	@Test
	@Ignore
	public void testBig50() throws IOException {
		testN(50, 10 * 1024 * 1024, 50 * 1024 * 1024);
	}

	@Test
	@Ignore
	public void testMid100() throws IOException {
		testN(100, 10 * 1024, 100 * 1024);
	}

	@Test
	@Ignore
	public void testMid1000() throws IOException {
		testN(1000, 10 * 1024, 100 * 1024);
	}

	@Test
	@Ignore
	public void test1000() throws IOException {
		testN(1000, 512, 1024);
	}

	@Test
	@Ignore
	public void test10000() throws IOException {
		testN(10000, 512, 1024);
	}

	@Test
	@Ignore
	public void test100000() throws IOException {
		testN(100000, 512, 1024);
	}

	@Test
	@Ignore
	public void test1000000() throws IOException {
		testN(1000000, 512, 1024);
	}
}