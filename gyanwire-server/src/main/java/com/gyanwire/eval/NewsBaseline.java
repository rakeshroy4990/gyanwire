package com.gyanwire.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Measures one news payload: count, age, duplicate titles, and aggregator links.
 */
public final class NewsBaseline {

    private NewsBaseline() {
    }

    public static ObjectNode summarize(ObjectMapper mapper, String industry, String sub, List<Map<String, Object>> results, Instant now) {
        List<Map<String, Object>> rows = results == null ? List.of() : results;
        List<Long> ages = new ArrayList<>();
        int redirects = 0;
        int dupes = 0;
        List<String> keys = new ArrayList<>();
        ArrayNode items = mapper.createArrayNode();
        for (Map<String, Object> row : rows) {
            String title = String.valueOf(row.getOrDefault("title", ""));
            String url = String.valueOf(row.getOrDefault("url", ""));
            String key = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
            if (keys.contains(key) && !key.isBlank()) {
                dupes++;
            }
            keys.add(key);
            if (url.contains("news.google.com")) {
                redirects++;
            }
            Long age = ageDays(row.get("publishedAt"), now);
            if (age == null && row.get("ageDays") instanceof Number n) {
                age = n.longValue();
            }
            if (age != null) {
                ages.add(age);
            }
            ObjectNode item = mapper.createObjectNode();
            item.put("title", title);
            item.put("url", url);
            item.put("score", row.get("score") instanceof Number n ? n.intValue() : 0);
            item.put("why", String.valueOf(row.getOrDefault("why", "")));
            if (age != null) {
                item.put("ageDays", age);
            }
            items.add(item);
        }
        ages.sort(Long::compare);
        ObjectNode out = mapper.createObjectNode();
        out.put("industry", industry);
        out.put("sub", sub == null ? "" : sub);
        out.put("count", rows.size());
        out.put("newestAgeDays", ages.isEmpty() ? -1 : ages.get(0));
        out.put("medianAgeDays", median(ages));
        out.put("duplicateRate", rows.isEmpty() ? 0 : round(dupes / (double) rows.size()));
        out.put("redirectLinkRate", rows.isEmpty() ? 0 : round(redirects / (double) rows.size()));
        out.set("items", items);
        return out;
    }

    private static Long ageDays(Object publishedAt, Instant now) {
        if (publishedAt == null) {
            return null;
        }
        try {
            Instant published = Instant.parse(String.valueOf(publishedAt));
            return Math.max(0, ChronoUnit.DAYS.between(published, now));
        } catch (Exception e) {
            return null;
        }
    }

    private static long median(List<Long> ages) {
        if (ages.isEmpty()) {
            return -1;
        }
        int mid = ages.size() / 2;
        if (ages.size() % 2 == 1) {
            return ages.get(mid);
        }
        return Math.round((ages.get(mid - 1) + ages.get(mid)) / 2.0);
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
