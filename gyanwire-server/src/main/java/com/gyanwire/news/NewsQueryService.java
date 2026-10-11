package com.gyanwire.news;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NewsQueryService {

    private final NewsStore store;
    private final NewsFetchCoordinator coordinator;
    private final NewsSeedLoader seed;
    private final NewsProperties properties;
    private final Environment environment;
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();

    public NewsQueryService(
            NewsStore store,
            NewsFetchCoordinator coordinator,
            NewsSeedLoader seed,
            NewsProperties properties,
            Environment environment
    ) {
        this.store = store;
        this.coordinator = coordinator;
        this.seed = seed;
        this.properties = properties;
        this.environment = environment;
    }

    public Map<String, Object> industry(String industry, String sub, int limit, String intent, int windowDays, String country) {
        int take = Math.max(1, Math.min(limit, 10));
        String code = country == null || country.isBlank() ? null : country;
        String key = industry + "|" + (sub == null ? "" : sub) + "|" + take + "|" + intent + "|" + windowDays
                + "|" + (code == null ? "" : code);
        Cached cached = cache.get(key);
        if (cached != null && cached.expires().isAfter(Instant.now())) {
            return cached.body();
        }
        Instant now = Instant.now();
        int[] windows = windows(windowDays);
        int lookback = windows[windows.length - 1];
        List<NewsRow> rows = store.eligible(industry, sub, now.minus(Duration.ofDays(lookback)), false);
        String forced = null;
        if (rows.isEmpty() && properties.getIngest().isEnabled()) {
            IndustryFetchGate.Outcome outcome = coordinator.fetch(
                    industry,
                    sub,
                    NewsFetchCoordinator.Mode.ON_DEMAND,
                    Duration.ofSeconds(Math.max(properties.getIngest().getOnDemandTimeoutSeconds(), 1)));
            rows = store.eligible(industry, sub, now.minus(Duration.ofDays(lookback)), false);
            if (outcome.timedOut()) {
                forced = "warming_up";
            }
            if (rows.isEmpty()) {
                boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
                seed.load(industry, prod, outcome.failed() || outcome.timedOut());
                rows = store.eligible(industry, sub, now.minus(Duration.ofDays(lookback)), true);
                if (forced == null && rows.stream().anyMatch(NewsRow::sample)) {
                    forced = rows.isEmpty() ? "warming_up" : null;
                }
            }
        }
        refreshRulesScores(rows, now);
        Prepared prepared = prepare(rows, take, windows, now, intent, code);
        String freshness = forced != null ? forced : prepared.freshness();
        Map<String, Object> body = payload(industry, sub, take, freshness, prepared.ranked(), now, code, prepared.countries());
        if (forced == null && !prepared.ranked().items().isEmpty()) {
            cache.put(key, new Cached(body, now.plusSeconds(Math.max(properties.getRank().getCacheSeconds(), 1))));
        }
        return body;
    }

    public Map<String, Object> defaults(int limit, String country) {
        Instant now = Instant.now();
        String code = country == null || country.isBlank() ? null : country;
        List<NewsRow> pooled = new ArrayList<>();
        pooled.addAll(store.eligible("Share Market", "IT", now.minus(Duration.ofDays(60)), false));
        pooled.addAll(store.eligible("Share Market", "EV", now.minus(Duration.ofDays(60)), false));
        pooled.addAll(store.eligible("Medical", "Cardiology", now.minus(Duration.ofDays(60)), false));
        pooled.addAll(store.eligible("Medical", "Gynecology", now.minus(Duration.ofDays(60)), false));
        if (pooled.isEmpty()) {
            Map<String, Object> filled = industry("Share Market", null, limit, "products", 14, code);
            filled.put("category", "Default");
            filled.put("label", "Top searched product news");
            filled.put("isDefaultNews", true);
            return filled;
        }
        refreshRulesScores(pooled, now);
        Prepared prepared = prepare(pooled, limit, windows(14), now, "products", code);
        Map<String, Object> body = payload("Default", null, limit, prepared.freshness(), prepared.ranked(), now, code, prepared.countries());
        body.put("label", "Top searched product news");
        body.put("isDefaultNews", true);
        return body;
    }

    /**
     * Re-applies the current rules cap to stored rule scores so a config change
     * (e.g. rules-cap 55 → 100) shows up without re-ingest. LLM scores stay as stored.
     */
    static void refreshRulesScores(List<NewsRow> rows, NewsProperties.Rank rank, Instant now) {
        if (rows == null || rows.isEmpty() || rank == null) {
            return;
        }
        for (NewsRow row : rows) {
            if (row == null || "llm".equals(row.scoredBy())) {
                continue;
            }
            String detected = SignalRules.detect(row.title());
            if (SignalRules.isFyi(detected) || "other".equals(row.signalType()) || row.signalType() == null) {
                row.signalType(detected);
                if (SignalRules.isFyi(detected)) {
                    row.whyIdea(SignalRules.whyIdea(detected, row.industry()));
                }
            }
            OpportunityScorer.Parts parts = OpportunityScorer.score(
                    rank,
                    row.signalType(),
                    row.specificity(),
                    row.indiaRelevance(),
                    SignalRules.actionability(row.signalType()),
                    row.publishedAt(),
                    now,
                    true);
            row.opportunityScore(parts.total());
        }
    }

    private void refreshRulesScores(List<NewsRow> rows, Instant now) {
        refreshRulesScores(rows, properties.getRank(), now);
    }

    private Prepared prepare(List<NewsRow> rows, int take, int[] windows, Instant now, String intent, String country) {
        CountrySignals.assignMissing(rows);
        List<Map<String, Object>> countries = new ArrayList<>(CountrySignals.options(rows));
        CountrySignals.ensureOption(countries, country);
        List<NewsRow> scoped = CountrySignals.matching(rows, country);
        FreshnessGate.Result gated = FreshnessGate.select(scoped, take, windows, now);
        NewsProperties.Rank rank = properties.getRank();
        NewsRanker.Ranked ranked = NewsRanker.rank(
                gated.items(), take, intent, rank.getIndiaBoost(), rank.getIntentBoost(), rank.getIntentDemote(), rank.getMinScore());
        return new Prepared(gated.freshness(), ranked, countries);
    }

    private Map<String, Object> payload(
            String industry,
            String sub,
            int limit,
            String freshness,
            NewsRanker.Ranked ranked,
            Instant now,
            String country,
            List<Map<String, Object>> countries
    ) {
        List<Map<String, Object>> items = new ArrayList<>();
        int index = 1;
        for (NewsRow row : ranked.items()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.id() == null ? "n-" + index : row.id().toString());
            item.put("title", row.title());
            item.put("url", row.url());
            item.put("source", row.sourceName() == null || row.sourceName().isBlank() ? row.domain() : row.sourceName());
            item.put("domain", row.domain());
            item.put("host", row.domain());
            item.put("publishedAt", row.publishedAt() == null ? null : row.publishedAt().toString());
            item.put("ageLabel", NewsTexts.ageLabel(row.publishedAt(), now));
            item.put("signalType", row.signalType());
            item.put("opportunityScore", row.opportunityScore());
            item.put("score", row.opportunityScore());
            item.put("whyIdea", row.whyIdea());
            item.put("why", row.whyIdea());
            item.put("description", row.summary());
            item.put("alsoCoveredBy", row.alsoCoveredBy());
            item.put("weakSignal", row.weakSignal());
            item.put("sample", row.sample());
            item.put("industry", row.industry());
            item.put("sub", row.sub());
            item.put("country", row.country());
            item.put("countryName", CountrySignals.name(row.country()));
            items.add(item);
            index++;
        }
        String label = (sub == null || sub.isBlank() ? industry : sub) + " products";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("results", items);
        body.put("items", items);
        body.put("freshness", freshness);
        body.put("requested", limit);
        body.put("returned", items.size());
        body.put("message", ranked.message());
        body.put("category", industry);
        body.put("sub", sub);
        body.put("label", label);
        body.put("query", industry);
        body.put("regionPreference", "Worldwide, India prioritized");
        body.put("country", country);
        body.put("countryName", CountrySignals.name(country));
        body.put("countries", countries == null ? List.of() : countries);
        body.put("isDefaultNews", false);
        if (country != null && items.isEmpty()) {
            body.put("message", "No findings for " + CountrySignals.name(country) + " right now.");
        }
        return body;
    }

    private record Prepared(String freshness, NewsRanker.Ranked ranked, List<Map<String, Object>> countries) {
    }

    private int[] windows(int requested) {
        int[] configured = properties.getRank().windowDays();
        int[] windows = configured.clone();
        if (requested > 0) {
            windows[0] = requested;
        }
        return windows;
    }

    private record Cached(Map<String, Object> body, Instant expires) {
    }
}
