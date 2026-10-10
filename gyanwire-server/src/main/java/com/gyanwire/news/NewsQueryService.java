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

    public Map<String, Object> industry(String industry, String sub, int limit, String intent, int windowDays) {
        int take = Math.max(1, Math.min(limit, 10));
        String key = industry + "|" + (sub == null ? "" : sub) + "|" + take + "|" + intent + "|" + windowDays;
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
        FreshnessGate.Result gated = FreshnessGate.select(rows, take, windows, now);
        String freshness = forced != null ? forced : gated.freshness();
        NewsProperties.Rank rank = properties.getRank();
        NewsRanker.Ranked ranked = NewsRanker.rank(
                gated.items(), take, intent, rank.getIndiaBoost(), rank.getIntentBoost(), rank.getIntentDemote(), rank.getMinScore());
        Map<String, Object> body = payload(industry, sub, take, freshness, ranked, now);
        if (forced == null && !ranked.items().isEmpty()) {
            cache.put(key, new Cached(body, now.plusSeconds(Math.max(properties.getRank().getCacheSeconds(), 1))));
        }
        return body;
    }

    public Map<String, Object> defaults(int limit) {
        List<NewsRow> pooled = new ArrayList<>();
        pooled.addAll(store.eligible("Share Market", "IT", Instant.now().minus(Duration.ofDays(60)), false));
        pooled.addAll(store.eligible("Share Market", "EV", Instant.now().minus(Duration.ofDays(60)), false));
        pooled.addAll(store.eligible("Medical", "Cardiology", Instant.now().minus(Duration.ofDays(60)), false));
        pooled.addAll(store.eligible("Medical", "Gynecology", Instant.now().minus(Duration.ofDays(60)), false));
        if (pooled.isEmpty()) {
            Map<String, Object> filled = industry("Share Market", null, limit, "products", 14);
            filled.put("category", "Default");
            filled.put("label", "Top searched product news");
            filled.put("isDefaultNews", true);
            return filled;
        }
        FreshnessGate.Result gated = FreshnessGate.select(pooled, limit, windows(14), Instant.now());
        NewsProperties.Rank rank = properties.getRank();
        NewsRanker.Ranked ranked = NewsRanker.rank(
                gated.items(), limit, "products", rank.getIndiaBoost(), rank.getIntentBoost(), rank.getIntentDemote(), rank.getMinScore());
        Map<String, Object> body = payload("Default", null, limit, gated.freshness(), ranked, Instant.now());
        body.put("label", "Top searched product news");
        body.put("isDefaultNews", true);
        return body;
    }

    private Map<String, Object> payload(String industry, String sub, int limit, String freshness, NewsRanker.Ranked ranked, Instant now) {
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
        body.put("regionPreference", "India first");
        body.put("isDefaultNews", false);
        return body;
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
