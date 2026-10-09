package com.gyanwire.research.rank;

import com.gyanwire.eval.EvalMetrics;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class TrustBadge {

    private TrustBadge() {
    }

    public static Map<String, Object> from(Map<String, Object> page) {
        String url = String.valueOf(page.getOrDefault("url", ""));
        String host = host(url);
        boolean https = url.startsWith("https://");
        boolean trusted = EvalMetrics.isTrusted(url);
        boolean aggregator = host.contains("wikipedia.org") || host.contains("news.google") || host.contains("reddit.com");
        String tier = trusted ? "high" : (host.contains("pinterest") || host.contains("quora") ? "low" : "mid");
        Map<String, Object> badge = new HashMap<>();
        badge.put("domainTier", tier);
        badge.put("https", https);
        badge.put("primarySource", trusted && !aggregator);
        badge.put("host", host);
        Object age = page.get("ageDays");
        if (age instanceof Number n) {
            badge.put("ageDays", n.intValue());
        }
        return badge;
    }

    private static String host(String url) {
        try {
            String rest = url.replaceFirst("^https?://", "");
            int slash = rest.indexOf('/');
            return (slash < 0 ? rest : rest.substring(0, slash)).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }
}
