package com.gyanwire.news;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class NewsUrls {

    private NewsUrls() {
    }

    public static String apply(String template, String query) {
        if (template == null) {
            return "";
        }
        if (!template.contains("{query}")) {
            return template;
        }
        String encoded = URLEncoder.encode(query == null ? "" : query, StandardCharsets.UTF_8);
        return template.replace("{query}", encoded);
    }
}
