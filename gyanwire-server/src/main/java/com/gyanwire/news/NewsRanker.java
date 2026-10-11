package com.gyanwire.news;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
                if (row == null || isFyiRow(row)) {
                    continue;
                }
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
        // Score picks the set; display order is always newest first.
        picked.sort(Comparator.comparing(NewsRow::publishedAt, Comparator.nullsLast(Comparator.reverseOrder())));
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
            } else if ("research".equals(signal) || "opinion".equals(signal) || "fyi".equals(signal)) {
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
        Map<String, Integer> countries = new HashMap<>();
        int countryCap = countryCap(ordered, limit);
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
            String country = row.country() == null ? "" : row.country();
            boolean countryCrowded = !country.isEmpty() && countries.getOrDefault(country, 0) >= countryCap;
            boolean crowded = domains.getOrDefault(domain, 0) >= 2
                    || signals.getOrDefault(signal, 0) >= 2
                    || countryCrowded;
            if (crowded && ordered.size() > limit) {
                skipped.add(row);
                continue;
            }
            picked.add(row);
            domains.merge(domain, 1, Integer::sum);
            signals.merge(signal, 1, Integer::sum);
            if (!country.isEmpty()) {
                countries.merge(country, 1, Integer::sum);
            }
        }
        if (picked.size() < limit) {
            fill(skipped, picked, limit, countries, countryCap, true);
        }
        if (picked.size() < limit) {
            fill(skipped, picked, limit, countries, countryCap, false);
        }
        return picked;
    }

    private static void fill(
            List<NewsRow> skipped,
            List<NewsRow> picked,
            int limit,
            Map<String, Integer> countries,
            int countryCap,
            boolean respectCountryCap
    ) {
        for (NewsRow row : skipped) {
            if (picked.size() >= limit) {
                return;
            }
            if (nearTitle(picked, row)) {
                continue;
            }
            String country = row.country() == null ? "" : row.country();
            if (respectCountryCap && !country.isEmpty() && countries.getOrDefault(country, 0) >= countryCap) {
                continue;
            }
            picked.add(row);
            if (!country.isEmpty()) {
                countries.merge(country, 1, Integer::sum);
            }
        }
    }

    /**
     * When several countries are in the pool, one country can lead but cannot fill the list.
     * A list of five keeps at most three from the leader, which is India after the India boost.
     */
    private static int countryCap(List<NewsRow> ordered, int limit) {
        Set<String> codes = new HashSet<>();
        for (NewsRow row : ordered) {
            if (row != null && row.country() != null && !row.country().isBlank()) {
                codes.add(row.country());
            }
        }
        if (codes.size() < 2) {
            return limit;
        }
        return Math.max(1, (limit * 3) / 5);
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

    private static boolean isFyiRow(NewsRow row) {
        if (SignalRules.isFyi(row.signalType())) {
            return true;
        }
        return SignalRules.isFyi(SignalRules.detect(row.title()));
    }

    private record Scored(NewsRow row, int score) {
    }
}
