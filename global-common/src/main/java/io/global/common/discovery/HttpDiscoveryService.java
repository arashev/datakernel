/*
 * Copyright (C) 2015-2018 SoftIndex LLC.
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

package io.global.common.discovery;

import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.common.parse.ParseException;
import io.datakernel.common.parse.ParserFunction;
import io.datakernel.http.*;
import io.datakernel.promise.Promise;
import io.global.common.Hash;
import io.global.common.PubKey;
import io.global.common.SharedSimKey;
import io.global.common.SignedData;
import io.global.common.api.AnnounceData;
import io.global.common.api.DiscoveryService;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.datakernel.async.util.LogUtils.toLogger;
import static io.datakernel.codec.binary.BinaryUtils.decode;
import static io.datakernel.codec.binary.BinaryUtils.encode;
import static io.global.common.api.DiscoveryCommand.*;
import static io.global.common.discovery.DiscoveryServlet.*;
import static java.nio.charset.StandardCharsets.UTF_8;

public final class HttpDiscoveryService implements DiscoveryService {
	private static final Logger logger = LoggerFactory.getLogger(HttpDiscoveryService.class);

	private final IAsyncHttpClient client;
	private final String url;

	private HttpDiscoveryService(String url, IAsyncHttpClient client) {
		this.client = client;
		this.url = url.endsWith("/") ? url : (url + "/");
	}

	public static HttpDiscoveryService create(String url, IAsyncHttpClient client) {
		return new HttpDiscoveryService(url, client);
	}

	@Override
	public Promise<Void> announce(PubKey space, SignedData<AnnounceData> announceData) {
		return client.request(
				HttpRequest.of(HttpMethod.PUT,
						url + UrlBuilder.relative()
								.appendPathPart(ANNOUNCE)
								.appendPathPart(space.asString())
								.build())
						.withBody(encode(SIGNED_ANNOUNCE, announceData)))
				.then(response -> response.getCode() != 201 ?
						Promise.ofException(HttpException.ofCode(response.getCode())) : Promise.of(response))
				.toVoid()
				.whenComplete(toLogger(logger, "announce", space, announceData, this));
	}

	private <T> Promise<T> tryParseResponse(HttpResponse response, ByteBuf body, ParserFunction<ByteBuf, T> from) {
		switch (response.getCode()) {
			case 200:
				try {
					return Promise.of(from.parse(body));
				} catch (ParseException e) {
					return Promise.ofException(HttpException.ofCode(400, e));
				}
			case 404:
				return Promise.of(null);
			default:
				return Promise.ofException(HttpException.ofCode(response.getCode(), body.getString(UTF_8)));
		}
	}

	@Override
	public Promise<@Nullable SignedData<AnnounceData>> find(PubKey space) {
		return client.request(HttpRequest.get(
				url + UrlBuilder.relative()
						.appendPathPart(FIND)
						.appendPathPart(space.asString())
						.build()))
				.thenEx((response, e) -> {
					if (e != null) {
						logger.trace("Failed to find announcements", e);
						return Promise.of(null);
					}
					return response.loadBody()
							.then(body -> tryParseResponse(response, body, buf -> decode(SIGNED_ANNOUNCE, buf.slice())));
				});
	}

	@Override
	public Promise<Void> shareKey(PubKey receiver, SignedData<SharedSimKey> simKey) {
		return client.request(
				HttpRequest.post(
						url + UrlBuilder.relative()
								.appendPathPart(SHARE_KEY)
								.appendPathPart(receiver.asString())
								.build())
						.withBody(encode(SIGNED_SHARED_SIM_KEY, simKey)))
				.then(response -> response.getCode() != 201 ?
						Promise.ofException(HttpException.ofCode(response.getCode())) : Promise.of(response))
				.toVoid();
	}

	@Override
	public Promise<@Nullable SignedData<SharedSimKey>> getSharedKey(PubKey receiver, Hash hash) {
		return client.request(HttpRequest.get(
				url + UrlBuilder.relative()
						.appendPathPart(GET_SHARED_KEY)
						.appendPathPart(receiver.asString())
						.appendPathPart(hash.asString())
						.build()))
				.then(response -> response.loadBody()
						.then(body -> tryParseResponse(response, body,
								buf -> decode(NULLABLE_SIGNED_SHARED_SIM_KEY, buf.slice()))));
	}

	@Override
	public Promise<List<SignedData<SharedSimKey>>> getSharedKeys(PubKey receiver) {
		return client.request(HttpRequest.get(
				url + UrlBuilder.relative()
						.appendPathPart(GET_SHARED_KEY)
						.appendPathPart(receiver.asString())
						.build()))
				.then(response -> response.loadBody()
						.then(body -> tryParseResponse(response, body,
								buf -> decode(LIST_OF_SIGNED_SHARED_SIM_KEYS, buf.slice()))));
	}
}
