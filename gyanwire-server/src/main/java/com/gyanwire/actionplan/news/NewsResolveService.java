package com.gyanwire.actionplan.news;

import com.gyanwire.news.NewsTexts;
import com.gyanwire.news.RedirectResolver;
import com.gyanwire.research.engine.HttpFetchClient;
import com.gyanwire.research.engine.PageText;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

/**
 * Resolve aggregator redirects and extract clean article text. Never throws to the caller.
 */
@Service
public class NewsResolveService {

    public static final int MIN_CHARS = 600;
    public static final int MAX_CHARS = 12_000;
    private static final int TIMEOUT_MS = 8_000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; GyanwireBot/1.0; +https://gyanwire.com)";

    private final RedirectResolver redirects;
    private final HttpFetchClient http;

    public NewsResolveService(RedirectResolver redirects, HttpFetchClient http) {
        this.redirects = redirects;
        this.http = http;
    }

    public ArticleText resolve(String url, String title, String source) {
        return resolve(url, title, source, TIMEOUT_MS);
    }

    /**
     * Same as {@link #resolve(String, String, String)} with a caller-chosen HTTP timeout.
     * Used by the ideas path to avoid waiting the full publisher timeout.
     */
    public ArticleText resolve(String url, String title, String source, int timeoutMs) {
        String input = url == null ? "" : url.trim();
        if (input.isBlank()) {
            return ArticleText.empty("missing_url");
        }
        int waitMs = Math.max(500, timeoutMs);
        try {
            String publisherUrl = input;
            boolean resolveFailed = false;
            if (NewsTexts.aggregator(input) || hostIs(input, "news.google.com")) {
                RedirectResolver.Resolved resolved = redirects.resolve(input);
                if (resolved == null || !resolved.resolved() || stillGoogle(resolved.url())) {
                    resolveFailed = true;
                } else {
                    publisherUrl = resolved.url();
                }
            }
            if (resolveFailed || stillGoogle(publisherUrl)) {
                return ArticleText.empty("resolve_failed");
            }
            String html = http.get(publisherUrl, USER_AGENT, waitMs);
            if (html == null || html.isBlank()) {
                return ArticleText.empty("fetch_empty");
            }
            String text = extract(html, publisherUrl);
            if (text.length() < MIN_CHARS) {
                return ArticleText.empty("too_short");
            }
            if (text.length() > MAX_CHARS) {
                text = text.substring(0, MAX_CHARS);
            }
            String publisher = NewsTexts.domainOf(publisherUrl);
            if (publisher == null || publisher.isBlank()) {
                publisher = source == null ? "" : source.trim();
            }
            return ArticleText.of(publisherUrl, publisher, text);
        } catch (Exception ex) {
            return ArticleText.empty("fetch_error");
        }
    }

    /** Extract from already-fetched HTML (tests / pasted pages). */
    public ArticleText fromHtml(String html, String publisherUrl) {
        try {
            String text = extract(html == null ? "" : html, publisherUrl == null ? "" : publisherUrl);
            if (text.length() < MIN_CHARS) {
                return ArticleText.empty("too_short");
            }
            if (text.length() > MAX_CHARS) {
                text = text.substring(0, MAX_CHARS);
            }
            return ArticleText.of(publisherUrl, NewsTexts.domainOf(publisherUrl), text);
        } catch (Exception ex) {
            return ArticleText.empty("parse_error");
        }
    }

    public ArticleText fromPastedText(String pasted, String headline, String publisherUrl) {
        String text = pasted == null ? "" : pasted.replaceAll("\\s+", " ").trim();
        if (text.length() < MIN_CHARS) {
            return ArticleText.empty("too_short");
        }
        if (text.length() > MAX_CHARS) {
            text = text.substring(0, MAX_CHARS);
        }
        return ArticleText.of(
                publisherUrl == null ? "" : publisherUrl,
                NewsTexts.domainOf(publisherUrl),
                text);
    }

    static String extract(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl == null ? "" : baseUrl);
        doc.select("nav, footer, script, style, noscript, template, header, aside, form, iframe").remove();
        Element article = doc.selectFirst("article");
        if (article != null) {
            String fromArticle = article.text().replaceAll("\\s+", " ").trim();
            if (fromArticle.length() >= MIN_CHARS) {
                return fromArticle;
            }
        }
        Element main = doc.selectFirst("main");
        if (main != null) {
            String fromMain = main.text().replaceAll("\\s+", " ").trim();
            if (fromMain.length() >= MIN_CHARS) {
                return fromMain;
            }
        }
        String largest = largestBlock(doc);
        if (largest.length() >= MIN_CHARS) {
            return largest;
        }
        return PageText.visibleText(doc);
    }

    private static String largestBlock(Document doc) {
        String best = "";
        for (Element el : doc.select("p, div, section")) {
            String t = el.ownText();
            if (t == null || t.isBlank()) {
                t = el.text();
            }
            t = t.replaceAll("\\s+", " ").trim();
            if (t.length() > best.length()) {
                best = t;
            }
        }
        return best;
    }

    private static boolean stillGoogle(String url) {
        return hostIs(url, "news.google.com");
    }

    private static boolean hostIs(String url, String host) {
        String h = hostOf(url);
        return h != null && (h.equalsIgnoreCase(host) || h.endsWith("." + host));
    }

    private static String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            return java.net.URI.create(url).getHost();
        } catch (Exception ex) {
            return null;
        }
    }
}
