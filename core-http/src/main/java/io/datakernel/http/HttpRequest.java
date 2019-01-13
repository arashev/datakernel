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

package io.datakernel.http;

import io.datakernel.async.Promise;
import io.datakernel.bytebuf.ByteBuf;
import io.datakernel.csp.ChannelSupplier;
import io.datakernel.exception.ParseException;
import io.datakernel.http.HttpHeaderValue.HttpHeaderValueOfSimpleCookies;
import io.datakernel.util.Initializable;
import io.datakernel.util.MemSize;
import io.datakernel.util.ParserFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.util.*;

import static io.datakernel.bytebuf.ByteBufStrings.*;
import static io.datakernel.http.HttpHeaders.*;
import static io.datakernel.http.HttpMethod.*;

/**
 * Represents the HTTP request which {@link AsyncHttpClient} sends to
 * {@link AsyncHttpServer}. It must have only one owner in each  part of time.
 * After creating an {@link HttpResponse} in a server, it will be recycled and
 * can not be used later.
 * <p>
 * {@code HttpRequest} class provides methods which can be used intuitively for
 * creating and configuring an HTTP request.
 */
public final class HttpRequest extends HttpMessage implements Initializable<HttpRequest> {
	private final static int LONGEST_HTTP_METHOD_SIZE = 12;
	private static final byte[] HTTP_1_1 = encodeAscii(" HTTP/1.1");
	private static final int HTTP_1_1_SIZE = HTTP_1_1.length;
	private final HttpMethod method;
	private UrlParser url;
	private InetAddress remoteAddress;
	private Map<String, String> pathParameters;
	private Map<String, String> queryParameters;

	// region creators
	HttpRequest(HttpMethod method, UrlParser url) {
		this.method = method;
		this.url = url;
	}

	public static HttpRequest of(HttpMethod method, String url) {
		assert method != null;
		HttpRequest request = new HttpRequest(method, null);
		request.setUrl(url);
		return request;
	}

	public static HttpRequest get(String url) {
		return HttpRequest.of(GET, url);
	}

	public static HttpRequest post(String url) {
		return HttpRequest.of(POST, url);
	}

	public static HttpRequest put(String url) {
		return HttpRequest.of(PUT, url);
	}

	public HttpRequest withHeader(HttpHeader header, String value) {
		addHeader(header, value);
		return this;
	}

	public HttpRequest withHeader(HttpHeader header, byte[] value) {
		addHeader(header, value);
		return this;
	}

	public HttpRequest withHeader(HttpHeader header, HttpHeaderValue value) {
		addHeader(header, value);
		return this;
	}

	public HttpRequest withBody(byte[] array) {
		setBody(array);
		return this;
	}

	public HttpRequest withBody(ByteBuf body) {
		setBody(body);
		return this;
	}

	public HttpRequest withBodyStream(ChannelSupplier<ByteBuf> stream) {
		setBodyStream(stream);
		return this;
	}

	@Override
	public void addCookies(List<HttpCookie> cookies) {
		assert !isRecycled();
		headers.add(COOKIE, new HttpHeaderValueOfSimpleCookies(cookies));
	}

	public HttpRequest withCookies(List<HttpCookie> cookies) {
		addCookies(cookies);
		return this;
	}

	public HttpRequest withCookies(HttpCookie... cookie) {
		addCookies(cookie);
		return this;
	}

	public HttpRequest withCookie(HttpCookie cookie) {
		addCookie(cookie);
		return this;
	}

	public HttpRequest withBodyGzipCompression() {
		setBodyGzipCompression();
		return this;
	}
	// endregion

	public HttpMethod getMethod() {
		assert !isRecycled();
		return method;
	}

	public InetAddress getRemoteAddress() {
		assert !isRecycled();
		return remoteAddress;
	}

	void setRemoteAddress(InetAddress inetAddress) {
		this.remoteAddress = inetAddress;
	}

	public String getFullUrl() {
		if (!url.isRelativePath()) {
			return url.toString();
		}
		return "http://" + getHeaderOrNull(HOST) + url.getPathAndQuery();
	}

	public boolean isHttps() {
		return url.isHttps();
	}

	UrlParser getUrl() {
		assert !isRecycled();
		return url;
	}

	void setUrl(String url) {
		assert !isRecycled();
		this.url = UrlParser.of(url);
		if (!this.url.isRelativePath()) {
			assert this.url.getHostAndPort() != null; // sadly no advanced contracts yet
			addHeader(HOST, this.url.getHostAndPort());
		}
	}

	@Nullable
	public String getHostAndPort() {
		return url.getHostAndPort();
	}

	public String getPath() {
		assert !isRecycled();
		return url.getPath();
	}

	public String getPathAndQuery() {
		assert !isRecycled();
		return url.getPathAndQuery();
	}

	private Map<String, String> parsedCookies;

	public Map<String, String> getCookies() throws ParseException {
		if (parsedCookies != null) return parsedCookies;
		Map<String, String> cookies = new LinkedHashMap<>();
		for (HttpCookie cookie : parseHeader(COOKIE, HttpHeaderValue::toSimpleCookies)) {
			cookies.put(cookie.getName(), cookie.getValue());
		}
		return this.parsedCookies = cookies;
	}

	public String getCookie(String cookie) throws ParseException {
		String httpCookie = getCookies().get(cookie);
		if (httpCookie != null) return httpCookie;
		throw new ParseException(HttpMessage.class, "There is no cookie: " + cookie);
	}

	@Nullable
	public String getCookieOrNull(String cookie) throws ParseException {
		return getCookies().get(cookie);
	}

	public String getQuery() {
		return url.getQuery();
	}

	public String getFragment() {
		return url.getFragment();
	}

	public Map<String, String> getQueryParameters() {
		assert !isRecycled();
		if (queryParameters != null) return queryParameters;
		queryParameters = url.getQueryParameters();
		return queryParameters;
	}

	@NotNull
	public String getQueryParameter(String key) throws ParseException {
		assert !isRecycled();
		String result = url.getQueryParameter(key);
		if (result != null) return result;
		throw new ParseException(HttpRequest.class, "Query parameter '" + key + "' is required");
	}

	public String getQueryParameter(String key, String defaultValue) {
		assert !isRecycled();
		String result = url.getQueryParameter(key);
		return result != null ? result : defaultValue;
	}

	@Nullable
	public String getQueryParameterOrNull(String key) {
		assert !isRecycled();
		return url.getQueryParameter(key);
	}

	public List<String> getQueryParameters(String key) {
		assert !isRecycled();
		return url.getQueryParameters(key);
	}

	public Iterable<QueryParameter> getQueryParametersIterable() {
		assert !isRecycled();
		return url.getQueryParametersIterable();
	}

	@NotNull
	public <T> T parseQueryParameter(String key, ParserFunction<String, T> parser) throws ParseException {
		return parser.parse(getQueryParameter(key));
	}

	public <T> T parseQueryParameter(String key, ParserFunction<String, T> parser, T defaultValue) throws ParseException {
		return parser.parseOrDefault(getQueryParameterOrNull(key), defaultValue);
	}

	public Promise<Map<String, String>> getPostParameters() {
		return getPostParameters(DEFAULT_LOAD_LIMIT_BYTES);
	}

	public Promise<Map<String, String>> getPostParameters(MemSize memSize) {
		return getPostParameters(memSize.toInt());
	}

	public Promise<Map<String, String>> getPostParameters(int maxSize) {
		assert !isRecycled();
		ContentType contentType;
		try {
			contentType = parseHeader(CONTENT_TYPE, HttpHeaderValue::toContentType, null);
		} catch (ParseException e) {
			return Promise.ofException(e);
		}
		if (method != POST
				|| contentType == null
				|| contentType.getMediaType() != MediaTypes.X_WWW_FORM_URLENCODED) {
			return Promise.ofException(new ParseException(HttpRequest.class, "Invalid request type"));
		}
		return getBody(maxSize)
				.thenApply(body -> {
					try {
						return UrlParser.parseQueryIntoMap(decodeAscii(body.array(), body.readPosition(), body.readRemaining()));
					} finally {
						body.recycle();
					}
				});
	}

	public Map<String, String> getPathParameters() {
		assert !isRecycled();
		return pathParameters != null ? pathParameters : Collections.emptyMap();
	}

	@NotNull
	public String getPathParameter(String key) throws ParseException {
		assert !isRecycled();
		String result = pathParameters != null ? pathParameters.get(key) : null;
		if (result != null) return result;
		throw new ParseException(HttpRequest.class, "There is no path parameter with key: " + key);
	}

	@Nullable
	public String getPathParameterOrNull(String key) {
		assert !isRecycled();
		return pathParameters != null ? pathParameters.get(key) : null;
	}

	@NotNull
	public <T> T parsePathParameter(String key, ParserFunction<String, T> parser) throws ParseException {
		return parser.parse(getPathParameter(key));
	}

	public <T> T parsePathParameter(String key, ParserFunction<String, T> parser, T defaultValue) throws ParseException {
		return parser.parseOrDefault(getPathParameterOrNull(key), defaultValue);
	}

	int getPos() {
		return url.pos;
	}

	void setPos(int pos) {
		url.pos = (short) pos;
	}

	public String getRelativePath() {
		assert !isRecycled();
		String partialPath = url.getPartialPath();
		return partialPath.startsWith("/") ? partialPath.substring(1) : partialPath; // strip first '/'
	}

	String pollUrlPart() {
		assert !isRecycled();
		return url.pollUrlPart();
	}

	void removePathParameter(String key) {
		pathParameters.remove(key);
	}

	void putPathParameter(String key, String value) {
		if (pathParameters == null) {
			pathParameters = new HashMap<>();
		}
		pathParameters.put(key, UrlParser.urlDecode(value));
	}

	@Override
	protected int estimateSize() {
		return estimateSize(LONGEST_HTTP_METHOD_SIZE
				+ 1 // SPACE
				+ url.getPathAndQueryLength())
				+ HTTP_1_1_SIZE;
	}

	@Override
	protected void writeTo(ByteBuf buf) {
		method.write(buf);
		buf.put(SP);
		url.writePathAndQuery(buf);
		buf.put(HTTP_1_1);
		writeHeaders(buf);
	}

	@Nullable
	@Override
	public String toString() {
		return getFullUrl();
	}
}
