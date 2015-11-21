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

package io.datakernel.rpc.client.sender;

import io.datakernel.async.ResultCallbackFuture;
import io.datakernel.rpc.client.sender.helper.ResultCallbackStub;
import io.datakernel.rpc.client.sender.helper.RpcClientConnectionPoolStub;
import io.datakernel.rpc.client.sender.helper.RpcClientConnectionStub;
import io.datakernel.rpc.client.sender.helper.RpcMessageDataStub;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;

import static io.datakernel.rpc.client.sender.RpcRequestSendingStrategies.server;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RpcStrategyTypeDispatchingTest {

	private static final String HOST = "localhost";
	private static final int PORT_1 = 10001;
	private static final int PORT_2 = 10002;
	private static final int PORT_3 = 10003;
	private static final int PORT_4 = 10004;
	private static final InetSocketAddress ADDRESS_1 = new InetSocketAddress(HOST, PORT_1);
	private static final InetSocketAddress ADDRESS_2 = new InetSocketAddress(HOST, PORT_2);
	private static final InetSocketAddress ADDRESS_3 = new InetSocketAddress(HOST, PORT_3);
	private static final InetSocketAddress ADDRESS_4 = new InetSocketAddress(HOST, PORT_4);

	@Test
	public void itShouldChooseSubStrategyDependingOnRpcMessageDataType() {
		RpcClientConnectionPoolStub pool = new RpcClientConnectionPoolStub();
		RpcClientConnectionStub connection1 = new RpcClientConnectionStub();
		RpcClientConnectionStub connection2 = new RpcClientConnectionStub();
		RpcClientConnectionStub connection3 = new RpcClientConnectionStub();
		pool.put(ADDRESS_1, connection1);
		pool.put(ADDRESS_2, connection2);
		pool.put(ADDRESS_3, connection3);
		RpcStrategySingleServer server1 = server(ADDRESS_1);
		RpcStrategySingleServer server2 = server(ADDRESS_2);
		RpcStrategySingleServer server3 = server(ADDRESS_3);
		RpcRequestSendingStrategy typeDispatchingStrategy = new RpcStrategyTypeDispatching()
				.on(RpcMessageDataTypeOne.class, server1)
				.on(RpcMessageDataTypeTwo.class, server2)
				.on(RpcMessageDataTypeThree.class, server3);
		int dataTypeOneRequests = 1;
		int dataTypeTwoRequests = 2;
		int dataTypeThreeRequests = 5;

		RpcRequestSender sender = typeDispatchingStrategy.createSender(pool);
		for (int i = 0; i < dataTypeOneRequests; i++) {
			sender.sendRequest(new RpcMessageDataTypeOne(), 50, new ResultCallbackFuture<>());
		}
		for (int i = 0; i < dataTypeTwoRequests; i++) {
			sender.sendRequest(new RpcMessageDataTypeTwo(), 50, new ResultCallbackFuture<>());
		}
		for (int i = 0; i < dataTypeThreeRequests; i++) {
			sender.sendRequest(new RpcMessageDataTypeThree(), 50, new ResultCallbackFuture<>());
		}

		assertEquals(dataTypeOneRequests, connection1.getCallsAmount());
		assertEquals(dataTypeTwoRequests, connection2.getCallsAmount());
		assertEquals(dataTypeThreeRequests, connection3.getCallsAmount());

	}

	@Test
	public void itShouldChooseDefaultSubStrategyWhenThereIsNoSpecifiedSubSenderForCurrentDataType() {
		RpcClientConnectionPoolStub pool = new RpcClientConnectionPoolStub();
		RpcClientConnectionStub connection1 = new RpcClientConnectionStub();
		RpcClientConnectionStub connection2 = new RpcClientConnectionStub();
		RpcClientConnectionStub connection3 = new RpcClientConnectionStub();
		RpcClientConnectionStub connection4 = new RpcClientConnectionStub();
		pool.put(ADDRESS_1, connection1);
		pool.put(ADDRESS_2, connection2);
		pool.put(ADDRESS_3, connection3);
		pool.put(ADDRESS_4, connection4);
		RpcStrategySingleServer server1 = server(ADDRESS_1);
		RpcStrategySingleServer server2 = server(ADDRESS_2);
		RpcStrategySingleServer server3 = server(ADDRESS_3);
		RpcStrategySingleServer defaultServer = server(ADDRESS_4);
		RpcRequestSendingStrategy typeDispatchingStrategy = new RpcStrategyTypeDispatching()
				.on(RpcMessageDataTypeOne.class, server1)
				.on(RpcMessageDataTypeTwo.class, server2)
				.on(RpcMessageDataTypeThree.class, server3)
				.onDefault(defaultServer);
		ResultCallbackStub callback = new ResultCallbackStub();

		RpcRequestSender sender = typeDispatchingStrategy.createSender(pool);
		sender.sendRequest(new RpcMessageDataStub(), 50, callback);

		assertEquals(0, connection1.getCallsAmount());
		assertEquals(0, connection2.getCallsAmount());
		assertEquals(0, connection3.getCallsAmount());
		assertEquals(1, connection4.getCallsAmount());  // connection of default server

	}

	@Test(expected = ExecutionException.class)
	public void itShouldRaiseExceptionWhenStrategyForDataIsNotSpecifiedAndDefaultSenderIsNull() throws ExecutionException, InterruptedException {
		RpcClientConnectionPoolStub pool = new RpcClientConnectionPoolStub();
		RpcClientConnectionStub connection1 = new RpcClientConnectionStub();
		RpcClientConnectionStub connection2 = new RpcClientConnectionStub();
		RpcClientConnectionStub connection3 = new RpcClientConnectionStub();
		pool.put(ADDRESS_1, connection1);
		pool.put(ADDRESS_2, connection2);
		pool.put(ADDRESS_3, connection3);
		RpcStrategySingleServer server1 = new RpcStrategySingleServer(ADDRESS_1);
		RpcStrategySingleServer server2 = new RpcStrategySingleServer(ADDRESS_2);
		RpcStrategySingleServer server3 = new RpcStrategySingleServer(ADDRESS_3);
		RpcRequestSendingStrategy typeDispatchingStrategy = new RpcStrategyTypeDispatching()
				.on(RpcMessageDataTypeOne.class, server1)
				.on(RpcMessageDataTypeTwo.class, server2)
				.on(RpcMessageDataTypeThree.class, server3);

		RpcRequestSender sender = typeDispatchingStrategy.createSender(pool);
		// sender is not specified for RpcMessageDataStub, default sender is null
		ResultCallbackFuture<Object> callback = new ResultCallbackFuture<>();
		sender.sendRequest(new RpcMessageDataStub(), 50, callback);

		callback.get();
	}

	@Test
	public void itShouldNotBeCreatedWhenAtLeastOneOfCrucialSubStrategyIsNotActive() {
		RpcClientConnectionPoolStub pool = new RpcClientConnectionPoolStub();
		RpcClientConnectionStub connection1 = new RpcClientConnectionStub();
		RpcClientConnectionStub connection3 = new RpcClientConnectionStub();
		RpcStrategySingleServer server1 = new RpcStrategySingleServer(ADDRESS_1);
		RpcStrategySingleServer server2 = new RpcStrategySingleServer(ADDRESS_2);
		RpcStrategySingleServer server3 = new RpcStrategySingleServer(ADDRESS_3);
		RpcRequestSendingStrategy typeDispatchingStrategy = new RpcStrategyTypeDispatching()
				.on(RpcMessageDataTypeOne.class, server1)
				.on(RpcMessageDataTypeTwo.class, server2)
				.on(RpcMessageDataTypeThree.class, server3);

		pool.put(ADDRESS_1, connection1);
		// we don't put connection 2
		pool.put(ADDRESS_3, connection3);

		assertTrue(typeDispatchingStrategy.createSender(pool) == null);
	}

	@Test
	public void itShouldNotBeCreatedWhenDefaultStrategyIsNotActiveAndCrucial() {
		RpcClientConnectionPoolStub pool = new RpcClientConnectionPoolStub();
		RpcClientConnectionStub connection1 = new RpcClientConnectionStub();
		RpcClientConnectionStub connection2 = new RpcClientConnectionStub();
		RpcClientConnectionStub connection3 = new RpcClientConnectionStub();
		RpcStrategySingleServer server1 = new RpcStrategySingleServer(ADDRESS_1);
		RpcStrategySingleServer server2 = new RpcStrategySingleServer(ADDRESS_2);
		RpcStrategySingleServer server3 = new RpcStrategySingleServer(ADDRESS_3);
		RpcStrategySingleServer defaultServer = new RpcStrategySingleServer(ADDRESS_4);
		RpcRequestSendingStrategy typeDispatchingStrategy = new RpcStrategyTypeDispatching()
				.on(RpcMessageDataTypeOne.class, server1)
				.on(RpcMessageDataTypeTwo.class, server2)
				.on(RpcMessageDataTypeThree.class, server3)
				.onDefault(defaultServer);

		pool.put(ADDRESS_1, connection1);
		pool.put(ADDRESS_2, connection2);
		pool.put(ADDRESS_3, connection3);
		// we don't add connection for default server

		assertTrue(typeDispatchingStrategy.createSender(pool) == null);
	}

	static class RpcMessageDataTypeOne {

	}

	static class RpcMessageDataTypeTwo {

	}

	static class RpcMessageDataTypeThree {

	}
}
