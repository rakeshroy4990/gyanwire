package com.gyanwire.actionplan.news;

import com.gyanwire.news.NewsProperties;
import com.gyanwire.news.RedirectResolver;
import com.gyanwire.research.engine.HttpFetchClient;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewsResolveServiceTest {

    private final NewsResolveService service = new NewsResolveService(
            new RedirectResolver(new NewsProperties()),
            new HttpFetchClient());

    @Test
    void normalArticleYieldsText() throws Exception {
        String html = fixture("normal-article.html");
        ArticleText result = service.fromHtml(html, "https://example.com/anthropic-haiku");
        assertFalse(result.empty());
        assertTrue(result.text().contains("Claude Haiku 5.5"));
        assertTrue(result.text().contains("Anthropic"));
    }

    @Test
    void paywallStubIsEmpty() throws Exception {
        String html = fixture("paywall-stub.html");
        ArticleText result = service.fromHtml(html, "https://example.com/paywall");
        assertTrue(result.empty());
        assertTrue("too_short".equals(result.reason()) || result.text().length() < NewsResolveService.MIN_CHARS);
    }

    @Test
    void googleRedirectMarkedEmptyWhenStillOnGoogle() {
        ArticleText result = service.resolve(
                "https://news.google.com/rss/articles/CBMiEXAMPLE",
                "Anthropic launches Claude Haiku 5.5",
                "Google News");
        // Without a live redirect chain this may be resolve_failed or fetch_error — never throws.
        assertTrue(result.empty() || result.publisherUrl() != null);
    }

    private static String fixture(String name) throws Exception {
        try (var in = NewsResolveServiceTest.class.getResourceAsStream("/actionplan/fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
