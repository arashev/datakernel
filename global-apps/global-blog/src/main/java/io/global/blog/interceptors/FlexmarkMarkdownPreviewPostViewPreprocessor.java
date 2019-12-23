package io.global.blog.interceptors;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import io.global.blog.http.view.PostView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Math.min;

public final class FlexmarkMarkdownPreviewPostViewPreprocessor implements Preprocessor<PostView> {
	private static final int DEFAULT_PREVIEW_LENGTH = 356;
	private static final int MAX_PREVIEW_LENGTH = 512;
	private static final String MEDIA_FULL_LINk = "!([PV])\\[.+?]\\(.*?\\)";
	private static final Pattern MEDIA_LINK_PATTERN = Pattern.compile(MEDIA_FULL_LINk);

	private final HtmlRenderer renderer;
	private final Parser parser;

	public FlexmarkMarkdownPreviewPostViewPreprocessor(HtmlRenderer renderer, Parser parser) {
		this.renderer = renderer;
		this.parser = parser;
	}

	@Override
	public PostView process(PostView postView, Object... params) {
		String content = postView.getContent();
		Matcher matcher = MEDIA_LINK_PATTERN.matcher(content);
		int end = matcher.find() && matcher.end() < MAX_PREVIEW_LENGTH ?
				matcher.end() :
				min(DEFAULT_PREVIEW_LENGTH, content.length());
		String previewContent = content.substring(0, end);
		Document doc = parser.parse(previewContent + "...");
		postView.withRenderedContent(renderer.render(doc));
		return postView;
	}
}
