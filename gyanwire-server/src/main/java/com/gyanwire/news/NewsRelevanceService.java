package com.gyanwire.news;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pack allowlist + keyword deny + signal score + title dedupe for business-relevant browse.
 * LLM yes/no is optional and skipped when no cache hit (rules-only path is the default).
 */
@Service
public class NewsRelevanceService {

    public static final int DEFAULT_TOP_N = 12;
    private static final int MIN_SIGNAL_SCORE = 15;

    private final NewsPackRegistry packs;
    private final NewsStore store;

    public NewsRelevanceService(NewsPackRegistry packs, NewsStore store) {
        this.packs = packs;
        this.store = store;
    }

    public List<NewsRow> gate(String industry, List<NewsRow> rows, Instant now, Set<UUID> hiddenSources, int topN) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        NewsPackRegistry.Pack pack = packs.pack(industry).orElse(null);
        Instant when = now == null ? Instant.now() : now;
        List<NewsRow> kept = new ArrayList<>();
        for (NewsRow row : rows) {
            if (row == null) {
                continue;
            }
            if (hiddenSources != null && row.sourceId() != null && hiddenSources.contains(row.sourceId())) {
                continue;
            }
            if (SignalRules.isFyi(row.signalType())
                    || SignalRules.isFyi(SignalRules.detect(row.title()))) {
                continue;
            }
            if (pack != null) {
                if (pack.domainDenied(row.domain()) || !pack.domainAllowed(row.domain())) {
                    continue;
                }
                String blob = ((row.title() == null ? "" : row.title()) + " "
                        + (row.summary() == null ? "" : row.summary()));
                if (!pack.requireMatched(blob) || pack.keywordDenied(blob)) {
                    continue;
                }
                if (pack.requireSignalMatch() && pack.matchedSignals(blob).isEmpty()) {
                    continue;
                }
                Scored scored = score(pack, row, when);
                if (scored.score() < MIN_SIGNAL_SCORE) {
                    continue;
                }
                row.relevanceScore(scored.score());
                row.relevanceReason(scored.reason());
                row.signals(scored.signals());
                if (row.whyIdea() == null || row.whyIdea().isBlank()) {
                    row.whyIdea(scored.reason());
                } else if (!row.whyIdea().startsWith("Why it's here:")) {
                    row.whyIdea("Why it's here: " + scored.reason());
                }
                if (row.opportunityScore() <= 0) {
                    row.opportunityScore(scored.score());
                }
            } else {
                // No pack: keep RSS items with existing scores; still drop empty titles.
                if (row.title() == null || row.title().isBlank()) {
                    continue;
                }
                if (row.relevanceScore() <= 0 && row.opportunityScore() > 0) {
                    row.relevanceScore(row.opportunityScore());
                }
                if (row.relevanceReason() == null || row.relevanceReason().isBlank()) {
                    String why = row.whyIdea() == null || row.whyIdea().isBlank()
                            ? "From an industry source."
                            : row.whyIdea();
                    row.relevanceReason(why);
                }
            }
            kept.add(row);
        }
        List<NewsRow> deduped = dedupeTitles(kept);
        deduped.sort(Comparator
                .<NewsRow>comparingInt(NewsRow::relevanceScore).reversed()
                .thenComparing(NewsRow::publishedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        int limit = topN <= 0 ? DEFAULT_TOP_N : topN;
        if (deduped.size() > limit) {
            return new ArrayList<>(deduped.subList(0, limit));
        }
        return deduped;
    }

    public List<NewsRow> gate(String industry, List<NewsRow> rows, Instant now, Set<UUID> hiddenSources) {
        return gate(industry, rows, now, hiddenSources, DEFAULT_TOP_N);
    }

    Scored score(NewsPackRegistry.Pack pack, NewsRow row, Instant now) {
        String blob = ((row.title() == null ? "" : row.title()) + " "
                + (row.summary() == null ? "" : row.summary())).toLowerCase(Locale.ROOT);
        List<String> hit = new ArrayList<>();
        int points = 0;
        if (pack.signals() != null) {
            for (Map.Entry<String, List<String>> e : pack.signals().entrySet()) {
                for (String term : e.getValue()) {
                    if (term != null && !term.isBlank()
                            && NewsPackRegistry.Pack.termMatches(blob, term.toLowerCase(Locale.ROOT))) {
                        hit.add(e.getKey());
                        points += 18;
                        break;
                    }
                }
            }
        }
        if (row.signalType() != null && !row.signalType().isBlank() && !"fyi".equalsIgnoreCase(row.signalType())
                && !"other".equalsIgnoreCase(row.signalType()) && !"opinion".equalsIgnoreCase(row.signalType())) {
            if (!hit.contains(row.signalType())) {
                hit.add(row.signalType());
            }
            points = Math.max(points, 20);
        }
        points += freshnessPoints(row.publishedAt(), now);
        points = Math.min(100, Math.max(0, points));
        String reason;
        if (hit.isEmpty()) {
            reason = "Industry source with a product or market angle.";
        } else {
            reason = "Signals: " + String.join(" · ", hit) + " — opportunity to build or cover.";
        }
        return new Scored(points, reason, hit);
    }

    static int freshnessPoints(Instant published, Instant now) {
        if (published == null || now == null) {
            return 0;
        }
        long hours = Duration.between(published, now).toHours();
        if (hours <= 24) {
            return 25;
        }
        if (hours <= 72) {
            return 18;
        }
        if (hours <= 168) {
            return 10;
        }
        if (hours <= 720) {
            return 4;
        }
        return 0;
    }

    static List<NewsRow> dedupeTitles(List<NewsRow> rows) {
        List<NewsRow> out = new ArrayList<>();
        for (NewsRow row : rows) {
            boolean dup = false;
            for (NewsRow existing : out) {
                if (NewsTexts.nearDuplicate(existing.title(), row.title())) {
                    dup = true;
                    break;
                }
            }
            if (dup) {
                continue;
            }
            out.add(row);
        }
        return out;
    }

    public record Scored(int score, String reason, List<String> signals) {}
}
