package io.datakernel.ot;

import io.datakernel.async.SettableStage;
import io.datakernel.async.Stages;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.eventloop.FatalErrorHandlers;
import io.datakernel.ot.exceptions.OTTransformException;
import io.datakernel.ot.utils.OTRemoteStub;
import io.datakernel.ot.utils.TestOp;
import io.datakernel.ot.utils.TestOpState;
import org.hamcrest.collection.IsEmptyCollection;
import org.junit.Before;
import org.junit.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

import static io.datakernel.ot.OTCommit.ofCommit;
import static io.datakernel.ot.OTCommit.ofRoot;
import static io.datakernel.ot.utils.OTRemoteStub.TestSequence.of;
import static io.datakernel.ot.utils.OTRemoteStub.create;
import static io.datakernel.ot.utils.Utils.*;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
public class OTStateManagerTest {
	private Eventloop eventloop;
	private OTSystem<TestOp> system;
	private Comparator<Integer> comparator;

	@Before
	public void before() {
		eventloop = Eventloop.create().withFatalErrorHandler(FatalErrorHandlers.rethrowOnAnyError());
		comparator = Integer::compareTo;
		system = createTestOp();
	}

	private <K, D> void createRootAndStartManager(OTRemote<K, D> otRemote, OTStateManager<K, D> stateManager) {
		otRemote.createId().thenCompose(id -> otRemote.push(asList(ofRoot(id))))
				.thenCompose(aVoid -> stateManager.start());
		eventloop.run();
	}

	@Test
	public void testCommitBeforePushFinished() throws ExecutionException, InterruptedException {
		OTRemote<Integer, TestOp> otSource = new OTRemoteDecorator<Integer, TestOp>(create(of(1, 2, 3), comparator)) {
			@Override
			public CompletionStage<Void> push(List<OTCommit<Integer, TestOp>> otCommits) {
				return super.push(otCommits).thenCompose(aVoid -> scheduledResult(eventloop, 100, aVoid));
			}
		};
		OTStateManager<Integer, TestOp> stateManager = new OTStateManager<>(eventloop, system, otSource,
				comparator, new TestOpState());

		createRootAndStartManager(otSource, stateManager);

		stateManager.add(add(1));
		stateManager.commit();
		eventloop.run();

		stateManager.push();
		stateManager.add(add(1));
		stateManager.commit();
		eventloop.run();

		stateManager.push();
		eventloop.run();

		final CompletableFuture<Set<Integer>> headsFuture = otSource.getHeads().toCompletableFuture();
		eventloop.run();

		final Set<Integer> heads = headsFuture.get();
		assertEquals(1, heads.size());
		assertEquals(3, heads.iterator().next().intValue());
	}

	@Test
	public void testPullFullHistory() throws ExecutionException, InterruptedException {
		final List<Integer> commitIdSequence = IntStream.rangeClosed(0, 5).boxed().collect(toList());
		final OTRemote<Integer, TestOp> otRemote = create(of(commitIdSequence), comparator);
		final TestOpState testOpState = new TestOpState();
		final OTStateManager<Integer, TestOp> stateManager = new OTStateManager<>(eventloop, system, otRemote,
				comparator, testOpState);

		createRootAndStartManager(otRemote, stateManager);

		commitIdSequence.subList(0, commitIdSequence.size() - 1).forEach(prevId -> {
			otRemote.createId().thenCompose(id -> otRemote.push(asList(ofCommit(id, prevId, asList(add(1))))));
			eventloop.run();
		});

		assertEquals(0, testOpState.getValue());

		stateManager.pull();
		eventloop.run();
		assertEquals(5, testOpState.getValue());
	}

	@Test
	public void testPullAfterFetch() throws ExecutionException, InterruptedException {
		final List<Integer> commitIdSequence = IntStream.rangeClosed(0, 10).boxed().collect(toList());
		final OTRemote<Integer, TestOp> otRemote = create(of(commitIdSequence), comparator);
		final TestOpState testOpState = new TestOpState();
		final OTStateManager<Integer, TestOp> stateManager = new OTStateManager<>(eventloop, system, otRemote,
				comparator, testOpState);

		createRootAndStartManager(otRemote, stateManager);

		commitIdSequence.subList(0, commitIdSequence.size() - 1).forEach(prevId -> {
			otRemote.createId()
					.thenCompose(id -> otRemote.push(asList(ofCommit(id, prevId, asList(add(1))))))
					.thenCompose($ -> asList(3, 5, 7).contains(prevId) ? stateManager.fetch() : Stages.of(null));
			eventloop.run();
		});

		assertEquals(0, testOpState.getValue());

		stateManager.pull();
		eventloop.run();
		assertEquals(10, testOpState.getValue());
	}

	@Test
	public void testApplyDiffBeforePull() throws ExecutionException, InterruptedException {
		final List<Integer> commitIdSequence = IntStream.rangeClosed(0, 10).boxed().collect(toList());
		final OTRemote<Integer, TestOp> otRemote = create(of(commitIdSequence), comparator);
		final TestOpState testOpState = new TestOpState();
		final OTStateManager<Integer, TestOp> stateManager = new OTStateManager<>(eventloop, system, otRemote,
				comparator, testOpState);

		createRootAndStartManager(otRemote, stateManager);

		commitIdSequence.subList(0, commitIdSequence.size() - 1).forEach(prevId -> {
			otRemote.createId().thenCompose(id -> otRemote.push(asList(ofCommit(id, prevId, asList(add(1))))));
			eventloop.run();
		});

		assertEquals(0, testOpState.getValue());
		stateManager.add(add(1));
		eventloop.run();
		assertEquals(1, testOpState.getValue());

		stateManager.pull();
		eventloop.run();
		assertEquals(11, testOpState.getValue());
	}

	@Test
	public void testTwoFetchAndTwoPullOneAfterAnother() throws ExecutionException, InterruptedException {
		final List<Integer> commitIdSequence = IntStream.rangeClosed(0, 20).boxed().collect(toList());
		final OTRemote<Integer, TestOp> otRemote = create(of(commitIdSequence), comparator);
		final TestOpState testOpState = new TestOpState();
		final OTStateManager<Integer, TestOp> stateManager = new OTStateManager<>(eventloop, system, otRemote,
				comparator, testOpState);

		createRootAndStartManager(otRemote, stateManager);

		commitIdSequence.subList(0, commitIdSequence.size() - 1).forEach(prevId -> {
			otRemote.createId()
					.thenCompose(id -> otRemote.push(asList(ofCommit(id, prevId, asList(add(1))))))
					.thenCompose(aVoid -> asList(5, 15).contains(prevId) ? stateManager.fetch() : Stages.of(null))
					.thenCompose(aVoid -> asList(10, 19).contains(prevId) ? stateManager.pull() : Stages.of(null));
			eventloop.run();
		});

		assertEquals(20, testOpState.getValue());
	}

	@Test
	public void testRebaseConflictResolving() throws OTTransformException {
		final List<Integer> commitIdSequence = IntStream.rangeClosed(0, 2).boxed().collect(toList());
		final OTRemote<Integer, TestOp> otRemote = create(of(commitIdSequence), comparator);
		final TestOpState testOpState = new TestOpState();
		final OTStateManager<Integer, TestOp> stateManager = new OTStateManager<>(eventloop, system, otRemote,
				comparator, testOpState);

		createRootAndStartManager(otRemote, stateManager);

		otRemote.createId()
				.thenCompose(id -> otRemote.push(asList(ofCommit(id, 0, asList(set(0, 10))))))
				.thenCompose($ -> stateManager.fetch());
		eventloop.run();

		assertEquals(0, testOpState.getValue());

		stateManager.add(set(0, 15));
		stateManager.rebase();

		eventloop.run();
		assertEquals(10, testOpState.getValue());
		assertThat(stateManager.getWorkingDiffs(), IsEmptyCollection.empty());
	}

	@Test
	public void testRebaseConflictResolving2() throws OTTransformException {
		final List<Integer> commitIdSequence = IntStream.rangeClosed(0, 2).boxed().collect(toList());
		final OTRemote<Integer, TestOp> otRemote = create(of(commitIdSequence), comparator);
		final TestOpState testOpState = new TestOpState();
		final OTStateManager<Integer, TestOp> stateManager = new OTStateManager<>(eventloop, system, otRemote,
				comparator, testOpState);

		createRootAndStartManager(otRemote, stateManager);

		otRemote.createId()
				.thenCompose(id -> otRemote.push(asList(ofCommit(id, 0, asList(set(0, 15))))))
				.thenCompose($ -> stateManager.fetch());
		eventloop.run();

		assertEquals(0, testOpState.getValue());

		stateManager.add(set(0, 10));
		stateManager.rebase();

		eventloop.run();
		assertEquals(10, testOpState.getValue());
		assertEquals(asList(set(15, 10)), stateManager.getWorkingDiffs());
	}

	@Test
	public void testRebaseConflictResolving3() throws OTTransformException {
		final List<Integer> commitIdSequence = IntStream.rangeClosed(0, 2).boxed().collect(toList());
		final OTRemote<Integer, TestOp> otRemote = create(of(commitIdSequence), comparator);
		final TestOpState testOpState = new TestOpState();
		final OTStateManager<Integer, TestOp> stateManager = new OTStateManager<>(eventloop, system, otRemote,
				comparator, testOpState);

		createRootAndStartManager(otRemote, stateManager);

		otRemote.createId()
				.thenCompose(id -> otRemote.push(asList(ofCommit(id, 0, asList(set(0, 10))))))
				.thenCompose($ -> stateManager.fetch());
		eventloop.run();

		assertEquals(0, testOpState.getValue());

		stateManager.add(add(5));
		stateManager.rebase();

		eventloop.run();
		assertEquals(10, testOpState.getValue());
		assertThat(stateManager.getWorkingDiffs(), IsEmptyCollection.empty());
	}

	@Test
	public void testRebaseConflictResolving4() throws OTTransformException {
		final List<Integer> commitIdSequence = IntStream.rangeClosed(0, 2).boxed().collect(toList());
		final OTRemote<Integer, TestOp> otRemote = create(of(commitIdSequence), comparator);
		final TestOpState testOpState = new TestOpState();
		final OTStateManager<Integer, TestOp> stateManager = new OTStateManager<>(eventloop, system, otRemote,
				comparator, testOpState);

		createRootAndStartManager(otRemote, stateManager);

		otRemote.createId()
				.thenCompose(id -> otRemote.push(asList(ofCommit(id, 0, asList(add(5))))))
				.thenCompose($ -> stateManager.fetch());
		eventloop.run();

		assertEquals(0, testOpState.getValue());

		stateManager.add(set(0, 10));
		stateManager.rebase();

		eventloop.run();
		assertEquals(10, testOpState.getValue());
		assertEquals(asList(set(5, 10)), stateManager.getWorkingDiffs());
	}

	@Test
	public void testRebaseConflictResolving5() throws OTTransformException {
		final List<Integer> commitIdSequence = IntStream.rangeClosed(0, 2).boxed().collect(toList());
		final OTRemote<Integer, TestOp> otRemote = create(of(commitIdSequence), comparator);
		final TestOpState testOpState = new TestOpState();
		final OTStateManager<Integer, TestOp> stateManager = new OTStateManager<>(eventloop, system, otRemote,
				comparator, testOpState);

		createRootAndStartManager(otRemote, stateManager);

		otRemote.createId()
				.thenCompose(id -> otRemote.push(asList(ofCommit(id, 0, asList(add(10))))))
				.thenCompose($ -> stateManager.fetch());
		eventloop.run();

		assertEquals(0, testOpState.getValue());

		stateManager.add(add(5));
		stateManager.rebase();

		eventloop.run();
		assertEquals(15, testOpState.getValue());
		assertEquals(asList(add(5)), stateManager.getWorkingDiffs());
	}

	private class OTRemoteDecorator<K, D> implements OTRemote<K, D> {
		private final OTRemoteStub<K, D> remote;

		private OTRemoteDecorator(OTRemoteStub<K, D> remote) {
			this.remote = remote;
		}

		@Override
		public CompletionStage<K> createId() {
			return remote.createId();
		}

		@Override
		public CompletionStage<Void> push(List<OTCommit<K, D>> otCommits) {
			return remote.push(otCommits);
		}

		@Override
		public CompletionStage<Set<K>> getHeads() {
			return remote.getHeads();
		}

		@Override
		public CompletionStage<List<D>> loadSnapshot(K revisionId) {
			return remote.loadSnapshot(revisionId);
		}

		@Override
		public CompletionStage<Boolean> isSnapshot(K revisionId) {
			return remote.isSnapshot(revisionId);
		}

		@Override
		public CompletionStage<OTCommit<K, D>> loadCommit(K revisionId) {
			return remote.loadCommit(revisionId);
		}

		@Override
		public CompletionStage<Void> saveSnapshot(K revisionId, List<D> diffs) {
			return remote.saveSnapshot(revisionId, diffs);
		}

		@Override
		public String toString() {
			return remote.toString();
		}
	}

	private static <T> CompletionStage<T> scheduledResult(Eventloop eventloop, long delta, T result) {
		final SettableStage<T> stage = SettableStage.create();
		eventloop.schedule(eventloop.currentTimeMillis() + delta, () -> stage.set(result));
		return stage;
	}

}