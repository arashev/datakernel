package io.datakernel.ot;

import io.datakernel.ot.utils.OTRepositoryStub;
import io.datakernel.ot.utils.TestOp;
import io.datakernel.stream.processor.DatakernelRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

import static io.datakernel.async.TestUtils.await;
import static io.datakernel.eventloop.Eventloop.getCurrentEventloop;
import static io.datakernel.ot.OTCommit.ofRoot;
import static io.datakernel.ot.utils.Utils.add;
import static io.datakernel.ot.utils.Utils.createTestOp;
import static io.datakernel.util.CollectionUtils.set;
import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;

@RunWith(DatakernelRunner.class)
public class OTLoadedGraphTest {
	private OTAlgorithms<Integer, TestOp> algorithms;
	private OTRepositoryStub<Integer, TestOp> repository;


	@Before
	public void setUp() {
		repository = OTRepositoryStub.create();
		algorithms = OTAlgorithms.create(getCurrentEventloop(), createTestOp(), repository);
		await(repository.pushAndUpdateHead(ofRoot(0)), repository.saveSnapshot(0, emptyList()));
	}

	@Test
	public void testCleanUpLinearGraph() {
		repository.setGraph(g -> {
			g.add(0, 1, add(1));
			g.add(1, 2, add(2));
			g.add(2, 3, add(3));
			g.add(3, 4, add(4));
			g.add(4, 5, add(5));
			g.add(5, 6, add(6));
			g.add(6, 7, add(7));
		});

		Set<Integer> heads = await(repository.getHeads());
		OTLoadedGraph<Integer, TestOp> graph = await(algorithms.loadGraph(heads));

		assertEquals(set(7), graph.getTips());
		assertEquals(set(0), graph.getRoots());

		graph.cleanUp(3);

		assertEquals(set(7), graph.getTips());
		assertEquals(set(5), graph.getRoots());
	}

	@Test
	public void testCleanUpSplittingGraph() {
		repository.setGraph(g -> {
			g.add(0, 1, add(1));
			g.add(1, 2, add(2));
			g.add(2, 3, add(3));
			g.add(3, 4, add(4));

			g.add(0, 5, add(5));
			g.add(5, 6, add(6));
			g.add(6, 7, add(7));
		});

		Set<Integer> heads = await(repository.getHeads());
		OTLoadedGraph<Integer, TestOp> graph = await(algorithms.loadGraph(heads));

		assertEquals(set(4, 7), graph.getTips());
		assertEquals(set(0), graph.getRoots());

		graph.cleanUp(4);

		assertEquals(set(4, 7), graph.getTips());
		assertEquals(set(1, 5), graph.getRoots());
	}

	@Test
	public void testIncrementalLoading() {
		repository.setGraph(g -> {
			g.add(0, 1, add(1));
			g.add(1, 2, add(2));
			g.add(2, 3, add(3));
			g.add(3, 4, add(4));
			g.add(4, 5, add(5));
			g.add(5, 6, add(6));
			g.add(6, 7, add(7));
		});

		Set<Integer> heads = await(repository.getHeads());
		OTLoadedGraph<Integer, TestOp> graph = await(algorithms.loadGraph(heads));

		assertEquals(set(7), graph.getTips());

		repository.addGraph(g -> {
			g.add(7, 8, add(8));
		});

		heads = await(repository.getHeads());
		await(algorithms.loadGraph(heads, graph));

		assertEquals(set(8), graph.getTips());
	}
}