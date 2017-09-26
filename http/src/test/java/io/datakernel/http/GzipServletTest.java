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

package io.datakernel.http;

import io.datakernel.async.AsyncRunnables;
import io.datakernel.async.Stages;
import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.eventloop.Eventloop;
import io.datakernel.eventloop.FatalErrorHandlers;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static io.datakernel.bytebuf.ByteBufPool.*;
import static io.datakernel.bytebuf.ByteBufStrings.decodeAscii;
import static io.datakernel.bytebuf.ByteBufStrings.wrapAscii;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.*;

public class GzipServletTest {
	private static final AsyncServlet helloWorldServlet = request -> Stages.of(HttpResponse.ok200().withBody(wrapAscii("Hello, World!")));

	@Test
	public void testGzipServletBase() throws Exception {
		final Eventloop eventloop = Eventloop.create().withFatalErrorHandler(FatalErrorHandlers.rethrowOnAnyError());
		GzipServlet gzipServlet = GzipServlet.create(5, helloWorldServlet);
		HttpRequest request = HttpRequest.get("http://example.com")
				.withBody(wrapAscii("Hello, world!"));
		HttpRequest requestGzip = HttpRequest.get("http://example.com")
				.withAcceptEncodingGzip().withBody(wrapAscii("Hello, world!"));

		CompletableFuture<HttpResponse> future = gzipServlet.serve(request).toCompletableFuture();
		eventloop.run();
		HttpResponse response = future.get();
		assertFalse(response.useGzip);
		response.recycleBufs();

		future = gzipServlet.serve(requestGzip).toCompletableFuture();
		eventloop.run();
		response = future.get();
		assertTrue(response.useGzip);
		response.recycleBufs();

		request.recycleBufs();
		requestGzip.recycleBufs();
		assertEquals(getPoolItemsString(), getCreatedItems(), getPoolItems());
	}

	@Test
	public void testDoesNotServeSmallBodies() throws Exception {
		final Eventloop eventloop = Eventloop.create().withFatalErrorHandler(FatalErrorHandlers.rethrowOnAnyError());
		AsyncServlet asyncServlet = request -> {
			HttpResponse response = HttpResponse.ok200();
			String requestNum = decodeAscii(request.getBody());
			ByteBuf body = "1".equals(requestNum) ? wrapAscii("0123456789012345678901") : wrapAscii("0");
			return Stages.of(response.withBody(body));
		};
		GzipServlet customGzipServlet = GzipServlet.create(20, asyncServlet);
		HttpRequest requestWBody = HttpRequest.get("http://example.com").withAcceptEncodingGzip().withBody(wrapAscii("1"));
		HttpRequest requestWOBody = HttpRequest.get("http://example.com").withAcceptEncodingGzip().withBody(wrapAscii("2"));

		CompletableFuture<HttpResponse> future = customGzipServlet.serve(requestWOBody).toCompletableFuture();
		eventloop.run();
		HttpResponse response = future.get();
		assertFalse(response.useGzip);
		response.recycleBufs();

		future = customGzipServlet.serve(requestWBody).toCompletableFuture();
		eventloop.run();
		response = future.get();
		assertTrue(response.useGzip);
		response.recycleBufs();

		requestWBody.recycleBufs();
		requestWOBody.recycleBufs();
		assertEquals(getPoolItemsString(), getCreatedItems(), getPoolItems());
	}

	@Test
	public void testClientServerIntegration() throws Exception {
		final Eventloop eventloop = Eventloop.create().withFatalErrorHandler(FatalErrorHandlers.rethrowOnAnyError());
		final AsyncHttpServer server = AsyncHttpServer.create(eventloop, GzipServlet.create(5, helloWorldServlet))
				.withListenPort(1239);
		final AsyncHttpClient client = AsyncHttpClient.create(eventloop);

		server.listen();
		AsyncRunnables.runInSequence(eventloop,
				() -> client.send(HttpRequest.get("http://127.0.0.1:1239")).thenAccept(response ->
						assertNull(response.getHeader(HttpHeaders.CONTENT_ENCODING))),
				() -> client.send(HttpRequest.get("http://127.0.0.1:1239").withAcceptEncodingGzip()).thenAccept(response ->
						assertNotNull(response.getHeader(HttpHeaders.CONTENT_ENCODING))))
				.run()
				.whenComplete(($, throwable) -> {
					if (throwable != null) {
						throwable.printStackTrace();
						fail("should not end here");
					} else {
						server.close();
						client.stop();
					}
				});
		eventloop.run();

		assertEquals(getPoolItemsString(), getCreatedItems(), getPoolItems());
	}
}