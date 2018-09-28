/*
 * Copyright (C) 2015 SoftIndex LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.datakernel.stream.processor;

import io.datakernel.eventloop.Eventloop;
import io.datakernel.exception.ExpectedException;
import io.datakernel.stream.StreamConsumerToList;
import io.datakernel.stream.StreamSupplier;
import io.datakernel.stream.TestStreamConsumers;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static io.datakernel.eventloop.FatalErrorHandlers.rethrowOnAnyError;
import static io.datakernel.stream.TestStreamConsumers.randomlySuspending;
import static io.datakernel.stream.TestUtils.assertClosedWithError;
import static io.datakernel.stream.TestUtils.assertEndOfStream;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

public class StreamSupplierConcatTest {

	@Test
	public void testSequence() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();

		StreamConsumerToList<Integer> consumer = StreamConsumerToList.create();

		StreamSupplier.concat(
				StreamSupplier.of(1, 2, 3),
				StreamSupplier.of(4, 5, 6))
				.streamTo(
						consumer.apply(randomlySuspending()));

		eventloop.run();

		assertEquals(asList(1, 2, 3, 4, 5, 6), consumer.getList());
		assertEndOfStream(consumer);
	}

	@Test
	public void testSequenceException() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();
		List<Integer> list = new ArrayList<>();

		StreamConsumerToList<Integer> consumer = StreamConsumerToList.create(list);

		StreamSupplier.concat(
				StreamSupplier.of(1, 2, 3),
				StreamSupplier.of(4, 5, 6),
				StreamSupplier.closingWithError(new ExpectedException("Test Exception")),
				StreamSupplier.of(1, 2, 3))
				.streamTo(
						consumer.apply(randomlySuspending()));

		eventloop.run();

		assertEquals(asList(1, 2, 3, 4, 5, 6), list);
		assertClosedWithError(consumer);
	}

	@Test
	public void testConcat() throws Exception {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();

		CompletableFuture<List<Integer>> future = StreamSupplier.concat(
				StreamSupplier.of(1, 2, 3),
				StreamSupplier.of(4, 5, 6),
				StreamSupplier.of())
				.toList()
				.toCompletableFuture();

		eventloop.run();

		assertEquals(asList(1, 2, 3, 4, 5, 6), future.get());
	}

	@Test
	public void testConcatException() {
		Eventloop eventloop = Eventloop.create().withFatalErrorHandler(rethrowOnAnyError()).withCurrentThread();
		List<Integer> list = new ArrayList<>();

		StreamConsumerToList<Integer> consumer = StreamConsumerToList.create(list);

		StreamSupplier.concat(
				StreamSupplier.of(1, 2, 3),
				StreamSupplier.of(4, 5, 6),
				StreamSupplier.closingWithError(new ExpectedException("Test Exception")))
				.streamTo(
						consumer.apply(TestStreamConsumers.oneByOne()));

		eventloop.run();

		assertEquals(asList(1, 2, 3, 4, 5, 6), list);

	}

}