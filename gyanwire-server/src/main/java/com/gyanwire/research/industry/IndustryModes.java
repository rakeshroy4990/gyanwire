package com.gyanwire.research.industry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class IndustryModes {

    private IndustryModes() {
    }

    public static Map<String, Object> build(String industry, List<Map<String, Object>> pages) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (pages != null) {
            for (Map<String, Object> page : pages) {
                String title = String.valueOf(page.getOrDefault("title", ""));
                String url = String.valueOf(page.getOrDefault("url", ""));
                String blob = (title + " " + page.getOrDefault("description", "")).toLowerCase(Locale.ROOT);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("title", title);
                row.put("url", url);
                row.put("tag", tag(industry, blob));
                items.add(row);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("industry", industry);
        out.put("mode", mode(industry));
        out.put("disclaimer", disclaimer(industry));
        out.put("items", items);
        return out;
    }

    public static String disclaimer(String industry) {
        return switch (industry == null ? "" : industry) {
            case "Share Market" -> "Informational only. Not investment advice or a recommendation to buy or sell.";
            case "Medical" -> "Research aid, not a diagnosis or treatment.";
            case "Astrology" -> "Cultural and educational context, not a health or financial prediction.";
            default -> "";
        };
    }

    private static String mode(String industry) {
        return switch (industry == null ? "" : industry) {
            case "Share Market" -> "what-changed";
            case "IT" -> "compare";
            case "Medical" -> "evidence";
            case "Space", "Gaming" -> "timeline";
            case "Social Media" -> "policy";
            default -> "list";
        };
    }

    private static String tag(String industry, String blob) {
        return switch (industry == null ? "" : industry) {
            case "Medical" -> blob.contains("guideline") ? "guideline"
                    : blob.contains("trial") || blob.contains("rct") ? "rct"
                    : blob.contains("review") ? "review" : "blog";
            case "Share Market" -> blob.contains("circular") || blob.contains("filing") ? "filing" : "coverage";
            case "Social Media" -> blob.contains("policy") || blob.contains("rule") ? "policy-change" : "note";
            case "IT" -> "source";
            default -> "event";
        };
    }
}
