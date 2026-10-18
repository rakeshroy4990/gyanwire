package com.gyanwire.actionplan.news;

/**
 * Result of resolving and fetching a news article. Never throws to callers.
 */
public record ArticleText(
        String publisherUrl,
        String publisher,
        String text,
        boolean empty,
        String reason
) {
    public static ArticleText empty(String reason) {
        return new ArticleText("", "", "", true, reason == null ? "empty" : reason);
    }

    public static ArticleText of(String publisherUrl, String publisher, String text) {
        String body = text == null ? "" : text.trim();
        if (body.length() < NewsResolveService.MIN_CHARS) {
            return empty("too_short");
        }
        return new ArticleText(
                publisherUrl == null ? "" : publisherUrl,
                publisher == null ? "" : publisher,
                body,
                false,
                null);
    }

    /**
     * Short title/summary snippets for the ideas path (below {@link NewsResolveService#MIN_CHARS}).
     */
    public static ArticleText snippet(String publisherUrl, String publisher, String text, int minChars) {
        String body = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (body.length() < Math.max(1, minChars)) {
            return empty("too_short");
        }
        if (body.length() > NewsResolveService.MAX_CHARS) {
            body = body.substring(0, NewsResolveService.MAX_CHARS);
        }
        return new ArticleText(
                publisherUrl == null ? "" : publisherUrl,
                publisher == null ? "" : publisher,
                body,
                false,
                null);
    }
}
