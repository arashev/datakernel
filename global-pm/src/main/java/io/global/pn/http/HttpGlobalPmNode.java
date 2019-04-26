package io.global.pn.http;

import io.datakernel.async.Promise;
import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.codec.binary.BinaryUtils;
import io.datakernel.exception.ParseException;
import io.datakernel.http.HttpException;
import io.datakernel.http.HttpRequest;
import io.datakernel.http.IAsyncHttpClient;
import io.datakernel.http.UrlBuilder;
import io.global.common.PubKey;
import io.global.common.SignedData;
import io.global.pn.api.GlobalPmNode;
import io.global.pn.api.RawMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

import static io.global.pn.http.PmCommand.*;
import static io.global.pn.util.BinaryDataFormats.SIGNED_LONG_CODEC;
import static io.global.pn.util.BinaryDataFormats.SIGNED_RAW_MSG_CODEC;

public class HttpGlobalPmNode implements GlobalPmNode {
	private static final String PM_NODE_SUFFIX = "/pm/";

	private final String url;
	private final IAsyncHttpClient client;

	private HttpGlobalPmNode(String url, IAsyncHttpClient client) {
		this.url = url + PM_NODE_SUFFIX;
		this.client = client;
	}

	public static HttpGlobalPmNode create(String url, IAsyncHttpClient client) {
		return new HttpGlobalPmNode(url, client);
	}

	@Override
	public @NotNull Promise<Void> send(PubKey space, SignedData<RawMessage> message) {
		return client.request(HttpRequest.post(url +
				UrlBuilder.relative()
						.appendPathPart(SEND)
						.appendPathPart(space.asString())
						.build())
				.withBody(BinaryUtils.encode(SIGNED_RAW_MSG_CODEC, message)))
				.then(response -> response.getCode() == 200 ?
						Promise.complete() :
						Promise.ofException(HttpException.ofCode(response.getCode())))
				.toVoid();
	}

	@Override
	public @NotNull Promise<@Nullable SignedData<RawMessage>> poll(PubKey space) {
		return client.request(HttpRequest.get(url +
				UrlBuilder.relative()
						.appendPathPart(POLL)
						.appendPathPart(space.asString())
						.build()))
				.then(response -> {
					if (response.getCode() == 200) {
						return response.getBody()
								.then(buf -> {
									try {
										return Promise.of(BinaryUtils.decode(SIGNED_RAW_MSG_CODEC.nullable(), buf));
									} catch (ParseException e) {
										return Promise.ofException(e);
									}
								});
					}
					if (response.getCode() == 404) {
						return Promise.of(null);
					}
					return Promise.ofException(HttpException.ofCode(response.getCode()));
				});
	}

	@Override
	public Promise<Void> drop(PubKey space, SignedData<Long> id) {
		ByteBuf buf = BinaryUtils.encode(SIGNED_LONG_CODEC, id);
		System.out.println("encoded " + id + " as " + Arrays.toString(buf.getArray()));
		return client.request(HttpRequest.post(url +
				UrlBuilder.relative()
						.appendPathPart(DROP)
						.appendPathPart(space.asString())
						.build())
				.withBody(buf))
				.then(response -> response.getCode() == 200 ?
						Promise.complete() :
						Promise.ofException(HttpException.ofCode(response.getCode())))
				.toVoid();

	}
}