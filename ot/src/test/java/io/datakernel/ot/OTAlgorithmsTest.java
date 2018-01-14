package io.datakernel.ot;

import io.datakernel.eventloop.Eventloop;
import io.datakernel.ot.utils.*;
import org.junit.Test;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.datakernel.eventloop.FatalErrorHandlers.rethrowOnAnyError;
import static io.datakernel.ot.utils.GraphBuilder.edge;
import static io.datakernel.ot.utils.OTRemoteStub.TestSequence.of;
import static io.datakernel.ot.utils.Utils.add;
import static io.datakernel.util.CollectionUtils.set;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
public class OTAlgorithmsTest {
	private static final OTSystem<TestOp> TEST_OP = Utils.createTestOp();

	@Test
	public void testLoadAllChangesFromRootWithSnapshot() throws ExecutionException, InterruptedException {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();
		Comparator<Integer> keyComparator = Integer::compareTo;
		TestOpState opState = new TestOpState();
		List<Integer> commitIds = IntStream.rangeClosed(0, 5).boxed().collect(Collectors.toList());
		OTRemote<Integer, TestOp> otRemote = OTRemoteStub.create(of(commitIds), keyComparator);
		OTAlgorithms<Integer, TestOp> otAlgorithms = new OTAlgorithms<>(TEST_OP, otRemote, keyComparator);

		otRemote.createCommitId().thenCompose(id -> otRemote.push(asList(OTCommit.ofRoot(id))));
		eventloop.run();
		otRemote.saveSnapshot(0, asList(add(10)));
		eventloop.run();

		commitIds.subList(0, commitIds.size() - 1).forEach(prevId -> {
			otRemote.createCommitId().thenCompose(id -> otRemote.push(asList(OTCommit.ofCommit(id, prevId, asList(add(1))))));
			eventloop.run();
		});

		CompletableFuture<List<TestOp>> changes = otRemote.getHeads().thenCompose(heads ->
				otAlgorithms.loadAllChanges(getLast(heads)))
				.toCompletableFuture();
		eventloop.run();
		changes.get().forEach(opState::apply);

		assertEquals(15, opState.getValue());
	}

	@Test
	public void testReduceEdges() throws ExecutionException, InterruptedException {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();
		Comparator<Integer> keyComparator = Integer::compareTo;
		List<Integer> commitIds = IntStream.rangeClosed(0, 8).boxed().collect(Collectors.toList());
		OTRemote<Integer, TestOp> otRemote = OTRemoteStub.create(of(commitIds), keyComparator);
		OTAlgorithms<Integer, TestOp> otAlgorithms = new OTAlgorithms<>(TEST_OP, otRemote, keyComparator);

		GraphBuilder<Integer, TestOp> graphBuilder = new GraphBuilder<>(otRemote);
		CompletableFuture<Map<Integer, Integer>> graphFuture = graphBuilder.buildGraph(asList(
				edge(0, 1, add(1)),
				edge(1, 2, add(1)),
				edge(2, 3, add(1)),
				edge(3, 4, add(-1)),
				edge(4, 5, add(-1)),
				edge(3, 6, add(1)),
				edge(6, 7, add(1))))
				.toCompletableFuture();

		eventloop.run();
		graphFuture.get();

		CompletableFuture<Map<Integer, List<TestOp>>> future = otAlgorithms.reduceEdges(
				set(5, 7),
				0,
				DiffsReducer.toList())
				.toCompletableFuture();

		eventloop.run();
		Map<Integer, List<TestOp>> result = future.get();

		assertEquals(1, applyToState(result.get(5)));
		assertEquals(5, applyToState(result.get(7)));
	}

	@Test
	public void testReduceEdges2() throws ExecutionException, InterruptedException {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();
		Comparator<Integer> keyComparator = Integer::compareTo;
		List<Integer> commitIds = IntStream.rangeClosed(0, 8).boxed().collect(Collectors.toList());
		OTRemote<Integer, TestOp> otRemote = OTRemoteStub.create(of(commitIds), keyComparator);
		OTAlgorithms<Integer, TestOp> otAlgorithms = new OTAlgorithms<>(TEST_OP, otRemote, keyComparator);

		GraphBuilder<Integer, TestOp> graphBuilder = new GraphBuilder<>(otRemote);
		CompletableFuture<Map<Integer, Integer>> graphFuture = graphBuilder.buildGraph(asList(
				edge(0, 1, add(1)),
				edge(0, 2, add(-1)),
				edge(1, 3, add(1)),
				edge(1, 4, add(-1)),
				edge(2, 4, add(1)),
				edge(2, 5, add(-1))))
				.toCompletableFuture();

		eventloop.run();
		graphFuture.get();

		CompletableFuture<Map<Integer, List<TestOp>>> future = otAlgorithms.reduceEdges(
				set(3, 4, 5),
				0,
				DiffsReducer.toList())
				.toCompletableFuture();

		eventloop.run();
		Map<Integer, List<TestOp>> result = future.get();

		assertEquals(2, applyToState(result.get(3)));
		assertEquals(0, applyToState(result.get(4)));
		assertEquals(-2, applyToState(result.get(5)));
	}

	private static int applyToState(List<TestOp> diffs) {
		TestOpState opState = new TestOpState();
		diffs.forEach(opState::apply);
		return opState.getValue();
	}

	private static <T> T getLast(Iterable<T> iterable) {
		Iterator<T> iterator = iterable.iterator();
		while (iterator.hasNext()) {
			T next = iterator.next();
			if (!iterator.hasNext()) return next;
		}
		throw new IllegalArgumentException("Empty iterable");
	}

}