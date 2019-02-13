package io.global.ot.http;

import io.datakernel.async.Promise;
import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.codec.StructuredCodec;
import io.datakernel.exception.ParseException;
import io.datakernel.http.HttpException;
import io.datakernel.http.HttpMessage;
import io.datakernel.http.HttpResponse;
import io.datakernel.http.IAsyncHttpClient;
import io.datakernel.ot.OTNode;
import io.global.ot.api.CommitId;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static io.datakernel.codec.json.JsonUtils.fromJson;
import static io.datakernel.codec.json.JsonUtils.toJson;
import static io.datakernel.http.HttpRequest.get;
import static io.datakernel.http.HttpRequest.post;
import static io.datakernel.http.UrlBuilder.urlEncode;
import static io.datakernel.ot.OTNode.getFetchDataCodec;
import static io.global.ot.api.OTNodeCommand.*;
import static io.global.ot.util.BinaryDataFormats.REGISTRY;
import static java.nio.charset.StandardCharsets.UTF_8;

public class OTNodeHttpClient<K, D> implements OTNode<K, D> {
	private final IAsyncHttpClient httpClient;
	private final String url;
	private final StructuredCodec<K> revisionCodec;
	private final StructuredCodec<FetchData<K, D>> fetchDataCodec;

	private OTNodeHttpClient(IAsyncHttpClient httpClient, String url, StructuredCodec<K> revisionCodec, StructuredCodec<D> diffCodec) {
		this.httpClient = httpClient;
		this.url = url.endsWith("/") ? url : url + '/';
		this.revisionCodec = revisionCodec;
		this.fetchDataCodec = getFetchDataCodec(revisionCodec, diffCodec);
	}

	public static <K, D> OTNodeHttpClient<K, D> create(IAsyncHttpClient httpClient, String url, StructuredCodec<K> revisionCodec, StructuredCodec<D> diffCodec) {
		return new OTNodeHttpClient<>(httpClient, url, revisionCodec, diffCodec);
	}

	public static <D> OTNodeHttpClient<CommitId, D> forGlobalNode(IAsyncHttpClient httpClient, String url, StructuredCodec<D> diffCodec) {
		return new OTNodeHttpClient<>(httpClient, url, REGISTRY.get(CommitId.class), diffCodec);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Promise<Object> createCommit(K parent, List<? extends D> diffs, long level) {
		FetchData<K, D> fetchData = new FetchData<>(parent, level, (List<D>) diffs);
		return httpClient.request(post(url + CREATE_COMMIT)
				.withBody(toJson(fetchDataCodec, fetchData).getBytes(UTF_8)))
				.thenCompose(HttpMessage::getBody)
				.thenApply(ByteBuf::asArray);
	}

	@Override
	public Promise<K> push(Object commitData) {
		return httpClient.request(post(url + PUSH)
				.withBody((byte[]) commitData))
				.thenCompose(response -> response.getBody()
						.thenCompose(body -> processResult(response, body, revisionCodec)));
	}

	@Override
	public Promise<FetchData<K, D>> checkout() {
		return httpClient.request(get(url + CHECKOUT))
				.thenCompose(response -> response.getBody()
						.thenCompose(body -> processResult(response, body, fetchDataCodec)));
	}

	@Override
	public Promise<FetchData<K, D>> fetch(K currentCommitId) {
		return httpClient.request(get(url + FETCH + "?id=" + urlEncode(toJson(revisionCodec, currentCommitId))))
				.thenCompose(response -> response.getBody()
						.thenCompose(body -> processResult(response, body, fetchDataCodec)));
	}

	private static <T> Promise<T> processResult(HttpResponse res, ByteBuf body, @NotNull StructuredCodec<T> json) {
		try {
			if (res.getCode() != 200) return Promise.ofException(HttpException.ofCode(res.getCode()));
			return Promise.of(fromJson(json, body.getString(UTF_8)));
		} catch (ParseException e) {
			return Promise.ofException(e);
		} finally {
			body.recycle();
		}
	}
}
