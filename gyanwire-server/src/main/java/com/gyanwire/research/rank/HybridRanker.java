package com.gyanwire.research.rank;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reciprocal rank fusion of pointer rank, full-text overlap, and freshness. No embeddings.
 */
public final class HybridRanker {

    public static final int K = 60;

    private HybridRanker() {
    }

    public static List<Map<String, Object>> fuse(List<Map<String, Object>> pages, String query, int halfLifeDays) {
        if (pages == null || pages.size() < 2) {
            return pages == null ? List.of() : pages;
        }
        List<Map<String, Object>> byPointer = new ArrayList<>(pages);
        byPointer.sort(Comparator.comparingInt((Map<String, Object> p) -> score(p, "score")).reversed());

        List<Map<String, Object>> byText = new ArrayList<>(pages);
        byText.sort(Comparator.comparingInt((Map<String, Object> p) -> textScore(p, query)).reversed());

        List<Map<String, Object>> byFresh = new ArrayList<>(pages);
        byFresh.sort(Comparator.comparingDouble((Map<String, Object> p) -> freshness(p, halfLifeDays)).reversed());

        Map<String, Integer> pointerRank = ranks(byPointer);
        Map<String, Integer> textRank = ranks(byText);
        Map<String, Integer> freshRank = ranks(byFresh);

        List<Map<String, Object>> fused = new ArrayList<>();
        for (Map<String, Object> page : pages) {
            String key = String.valueOf(page.get("url"));
            double rrf = 1.0 / (K + pointerRank.getOrDefault(key, pages.size()))
                    + 1.0 / (K + textRank.getOrDefault(key, pages.size()))
                    + 1.0 / (K + freshRank.getOrDefault(key, pages.size()));
            Map<String, Object> copy = new HashMap<>(page);
            copy.put("rrf", rrf);
            copy.put("trust", TrustBadge.from(page));
            fused.add(copy);
        }
        fused.sort(Comparator.comparingDouble((Map<String, Object> p) -> ((Number) p.get("rrf")).doubleValue()).reversed());
        return fused;
    }

    public static int halfLifeDays(String industry) {
        if (industry == null) {
            return 30;
        }
        return switch (industry) {
            case "Share Market" -> 7;
            case "IT" -> 45;
            case "Medical" -> 90;
            case "Space" -> 60;
            case "Social Media" -> 14;
            case "Gaming" -> 21;
            case "Astrology" -> 30;
            default -> 30;
        };
    }

    private static Map<String, Integer> ranks(List<Map<String, Object>> ordered) {
        Map<String, Integer> ranks = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            ranks.put(String.valueOf(ordered.get(i).get("url")), i + 1);
        }
        return ranks;
    }

    private static int score(Map<String, Object> page, String key) {
        Object value = page.get(key);
        return value instanceof Number n ? n.intValue() : 0;
    }

    static int textScore(Map<String, Object> page, String query) {
        String hay = (String.valueOf(page.getOrDefault("title", "")) + " "
                + page.getOrDefault("description", "") + " "
                + page.getOrDefault("snippet", "") + " "
                + page.getOrDefault("text", "")).toLowerCase(Locale.ROOT);
        if (query == null || query.isBlank()) {
            return 0;
        }
        int hits = 0;
        for (String token : query.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (token.length() > 2 && hay.contains(token)) {
                hits++;
            }
        }
        return hits;
    }

    static double freshness(Map<String, Object> page, int halfLifeDays) {
        Object age = page.get("ageDays");
        double days = age instanceof Number n ? n.doubleValue() : 0;
        double half = Math.max(1, halfLifeDays);
        return Math.exp(-Math.log(2) * days / half) * 100.0;
    }
}
