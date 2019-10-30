package io.global.mustache;

import io.datakernel.bytebuf.util.ByteBufWriter;
import io.datakernel.common.ref.Ref;
import io.datakernel.http.HttpResponse;
import io.datakernel.promise.Promise;
import io.datakernel.promise.Promises;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.datakernel.http.ContentTypes.HTML_UTF_8;
import static io.datakernel.http.HttpHeaderValue.ofContentType;
import static io.datakernel.http.HttpHeaders.CONTENT_TYPE;
import static java.util.Collections.emptyMap;

public final class MustacheTemplater {
	private final MustacheSupplier mustacheSupplier;
	private final Map<String, Object> staticContext = new HashMap<>();

	public MustacheTemplater(MustacheSupplier mustacheSupplier) {
		this.mustacheSupplier = mustacheSupplier;
	}

	public void put(String name, Object object) {
		staticContext.put(name, object);
	}

	public void clear() {
		staticContext.clear();
	}

	@SuppressWarnings("SuspiciousMethodCalls")
	public Promise<HttpResponse> render(int code, String templateName, Map<String, Object> scope, boolean compress) {
		Map<String, Object> context = new HashMap<>(scope);
		context.putAll(staticContext);
		List<Promise<?>> promisesToWait = new ArrayList<>();

		for (Map.Entry<String, Object> entry : context.entrySet()) {
			Object value = entry.getValue();
			if (value instanceof Ref) {
				entry.setValue(context.get(((Ref<?>) value).get()));
			}
			if (!(value instanceof Promise)) {
				continue;
			}
			Promise<?> promise = (Promise<?>) value;
			if (promise.isResult()) {
				entry.setValue(promise.getResult());
			} else {
				promisesToWait.add(promise.whenResult(entry::setValue));
			}
		}
		return Promises.all(promisesToWait).map($ -> {
			ByteBufWriter writer = new ByteBufWriter();
			mustacheSupplier.getMustache(templateName + ".mustache").execute(writer, context);
			HttpResponse httpResponse = HttpResponse.ofCode(code);
			if (compress){
				httpResponse.withBodyGzipCompression();
			}
			return httpResponse
					.withBody(writer.getBuf())
					.withHeader(CONTENT_TYPE, ofContentType(HTML_UTF_8));
		});
	}
	public Promise<HttpResponse> render(int code, String templateName, Map<String, Object> scope) {
		return render(code, templateName, scope, false);
	}

	public Promise<HttpResponse> render(String templateName, Map<String, Object> scope) {
		return render(200, templateName, scope, false);
	}
	public Promise<HttpResponse> render(String templateName, Map<String, Object> scope, boolean compress) {
		return render(200, templateName, scope, compress);
	}

	public Promise<HttpResponse> render(String templateName) {
		return render(200, templateName, emptyMap(), false);
	}

	public Promise<HttpResponse> render(String templateName, boolean compress) {
		return render(200, templateName, emptyMap(), compress);
	}
}
