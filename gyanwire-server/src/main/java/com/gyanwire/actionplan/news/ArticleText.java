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
}
