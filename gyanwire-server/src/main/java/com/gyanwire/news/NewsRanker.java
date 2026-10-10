package com.gyanwire.news;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NewsRanker {

    public record Ranked(List<NewsRow> items, String message) {
    }

    private static final List<String> PRODUCT_SIGNALS = List.of(
            "launch", "funding", "approval", "regulation", "pricing", "shortage"
    );

    private NewsRanker() {
    }

    public static Ranked rank(List<NewsRow> pool, int limit, String intent, int indiaBoost, int intentBoost, int intentDemote, int minScore) {
        int take = Math.max(1, limit);
        List<Scored> ordered = new ArrayList<>();
        if (pool != null) {
            for (NewsRow row : pool) {
                ordered.add(new Scored(row, rankScore(row, intent, indiaBoost, intentBoost, intentDemote)));
            }
        }
        ordered.sort(Comparator.comparingInt(Scored::score).reversed()
                .thenComparing(s -> s.row().publishedAt(), Comparator.nullsLast(Comparator.reverseOrder())));
        List<NewsRow> strong = new ArrayList<>();
        List<NewsRow> weak = new ArrayList<>();
        for (Scored scored : ordered) {
            if (scored.row().opportunityScore() >= minScore) {
                strong.add(scored.row());
            } else {
                weak.add(scored.row());
            }
        }
        List<NewsRow> picked = diversify(strong, take);
        if (picked.size() < take) {
            for (NewsRow row : diversify(weak, take - picked.size())) {
                row.weakSignal(true);
                picked.add(row);
            }
        }
        String message = "";
        if (picked.size() < 3) {
            message = "Few items match this topic right now.";
        }
        return new Ranked(picked, message);
    }

    static int rankScore(NewsRow row, String intent, int indiaBoost, int intentBoost, int intentDemote) {
        int score = row.opportunityScore();
        if (row.indiaRelevance() >= 50) {
            score += indiaBoost;
        }
        if ("products".equalsIgnoreCase(intent)) {
            String signal = row.signalType() == null ? "other" : row.signalType().toLowerCase(Locale.ROOT);
            if (PRODUCT_SIGNALS.contains(signal)) {
                score += intentBoost;
            } else if ("research".equals(signal) || "opinion".equals(signal)) {
                score -= intentDemote;
            }
        }
        if (row.sub() != null && !row.sub().isBlank()) {
            score += 2;
        }
        return score;
    }

    static List<NewsRow> diversify(List<NewsRow> ordered, int limit) {
        if (limit <= 0 || ordered == null || ordered.isEmpty()) {
            return List.of();
        }
        List<NewsRow> picked = new ArrayList<>();
        Map<String, Integer> domains = new HashMap<>();
        Map<String, Integer> signals = new HashMap<>();
        List<NewsRow> skipped = new ArrayList<>();
        for (NewsRow row : ordered) {
            if (picked.size() >= limit) {
                break;
            }
            if (nearTitle(picked, row)) {
                continue;
            }
            String domain = row.domain() == null ? "" : row.domain();
            String signal = row.signalType() == null ? "other" : row.signalType();
            boolean crowded = domains.getOrDefault(domain, 0) >= 2 || signals.getOrDefault(signal, 0) >= 2;
            if (crowded && ordered.size() > limit) {
                skipped.add(row);
                continue;
            }
            picked.add(row);
            domains.merge(domain, 1, Integer::sum);
            signals.merge(signal, 1, Integer::sum);
        }
        if (picked.size() < limit) {
            for (NewsRow row : skipped) {
                if (picked.size() >= limit) {
                    break;
                }
                if (nearTitle(picked, row)) {
                    continue;
                }
                picked.add(row);
            }
        }
        return picked;
    }

    private static boolean nearTitle(List<NewsRow> picked, NewsRow candidate) {
        if (candidate == null || candidate.title() == null) {
            return false;
        }
        for (NewsRow row : picked) {
            if (NewsTexts.nearDuplicate(row.title(), candidate.title())) {
                return true;
            }
        }
        return false;
    }

    private record Scored(NewsRow row, int score) {
    }
}
