package com.gyanwire.actionplan.news;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record NewsBrief(
        String newsId,
        String headline,
        String publisherUrl,
        String publisher,
        String publishedAt,
        List<String> entities,
        List<Fact> facts,
        List<String> whoItAffects,
        Integer freshnessHours
) {
    public record Fact(String id, String text, String sourceSpan, String confidence) {
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("newsId", newsId);
        out.put("headline", headline);
        out.put("publisherUrl", publisherUrl);
        out.put("publisher", publisher);
        out.put("publishedAt", publishedAt);
        out.put("entities", entities == null ? List.of() : List.copyOf(entities));
        List<Map<String, Object>> factRows = new ArrayList<>();
        if (facts != null) {
            for (Fact fact : facts) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", fact.id());
                row.put("text", fact.text());
                row.put("sourceSpan", fact.sourceSpan());
                row.put("confidence", fact.confidence());
                factRows.add(row);
            }
        }
        out.put("facts", factRows);
        out.put("whoItAffects", whoItAffects == null ? List.of() : List.copyOf(whoItAffects));
        out.put("freshnessHours", freshnessHours);
        return out;
    }

    public static NewsBrief insufficient(String headline, String publisherUrl) {
        return new NewsBrief(
                UUID.randomUUID().toString(),
                headline == null ? "" : headline,
                publisherUrl == null ? "" : publisherUrl,
                "",
                null,
                List.of(),
                List.of(),
                List.of(),
                null);
    }

    public boolean usable() {
        return facts != null && facts.size() >= 3;
    }
}
