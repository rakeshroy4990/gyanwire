package com.gyanwire.news;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NewsQueryService {

    /** Browse always covers the last month and pages newest-first. */
    static final int BROWSE_LOOKBACK_DAYS = 30;
    private static final int MAX_POOL = 500;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private final NewsStore store;
    private final NewsFetchCoordinator coordinator;
    private final NewsSeedLoader seed;
    private final NewsProperties properties;
    private final Environment environment;
    private final NewsRelevanceService relevance;
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();

    public NewsQueryService(
            NewsStore store,
            NewsFetchCoordinator coordinator,
            NewsSeedLoader seed,
            NewsProperties properties,
            Environment environment,
            NewsRelevanceService relevance
    ) {
        this.store = store;
        this.coordinator = coordinator;
        this.seed = seed;
        this.properties = properties;
        this.environment = environment;
        this.relevance = relevance;
    }

    public Map<String, Object> industry(
            String industry,
            String sub,
            int limit,
            String intent,
            int windowDays,
            String country,
            NewsViewer viewer,
            int page
    ) {
        int pageSize = clampPageSize(limit);
        int pageNo = Math.max(1, page);
        int lookback = Math.max(1, Math.min(windowDays <= 0 ? BROWSE_LOOKBACK_DAYS : windowDays, BROWSE_LOOKBACK_DAYS));
        String code = country == null || country.isBlank() ? null : country;
        NewsViewer who = viewer == null ? NewsViewer.of(null, null) : viewer;
        String key = who.cacheKey() + "|" + industry + "|" + (sub == null ? "" : sub) + "|" + pageSize + "|" + pageNo
                + "|" + intent + "|" + lookback + "|" + (code == null ? "" : code);
        Cached cached = cache.get(key);
        if (cached != null && cached.expires().isAfter(Instant.now())) {
            return cached.body();
        }
        Instant now = Instant.now();
        Instant since = now.minus(Duration.ofDays(lookback));
        NewsStore.Dismissals dismissals = store.loadDismissals(who);
        Set<UUID> hidden = who.userId() == null ? Set.of() : store.loadHiddenSources(who.userId());
        List<NewsRow> rows = withoutDismissed(
                labRows(industry, sub, store.eligible(industry, sub, since, false)),
                dismissals);
        String forced = null;
        if (pageNo == 1 && properties.getIngest().isEnabled()) {
            IndustryFetchGate.Outcome outcome = coordinator.fetch(
                    industry,
                    sub,
                    NewsFetchCoordinator.Mode.ON_DEMAND,
                    Duration.ofSeconds(Math.max(properties.getIngest().getOnDemandTimeoutSeconds(), 1)));
            rows = withoutDismissed(
                    labRows(industry, sub, store.eligible(industry, sub, since, false)),
                    dismissals);
            if (outcome.timedOut() && rows.isEmpty()) {
                forced = "warming_up";
            }
            if (rows.isEmpty()) {
                boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
                seed.load(industry, prod, outcome.failed() || outcome.timedOut());
                rows = withoutDismissed(
                        labRows(industry, sub, store.eligible(industry, sub, since, true)),
                        dismissals);
                if (forced == null && rows.stream().anyMatch(NewsRow::sample)) {
                    forced = rows.isEmpty() ? "warming_up" : null;
                }
            }
        }
        refreshRulesScores(rows, now);
        rows = relevance.gate(industry, rows, now, hidden, Math.max(NewsRelevanceService.DEFAULT_TOP_N, pageSize * 3));
        Prepared prepared = prepareChronological(rows, pageNo, pageSize, now, code);
        String freshness = forced != null ? forced : "last 30 days";
        Map<String, Object> body = payload(
                industry, sub, pageSize, pageNo, prepared.total(), freshness, prepared.ranked(), now, code, prepared.countries());
        if (forced == null && !prepared.ranked().items().isEmpty()) {
            cache.put(key, new Cached(body, now.plusSeconds(Math.max(properties.getRank().getCacheSeconds(), 1))));
        }
        return body;
    }

    public Map<String, Object> defaults(int limit, String country, NewsViewer viewer) {
        return defaults(limit, country, viewer, 1);
    }

    public Map<String, Object> defaults(int limit, String country, NewsViewer viewer, int page) {
        Instant now = Instant.now();
        int pageSize = clampPageSize(limit);
        int pageNo = Math.max(1, page);
        String code = country == null || country.isBlank() ? null : country;
        NewsViewer who = viewer == null ? NewsViewer.of(null, null) : viewer;
        NewsStore.Dismissals dismissals = store.loadDismissals(who);
        Set<UUID> hidden = who.userId() == null ? Set.of() : store.loadHiddenSources(who.userId());
        Instant since = now.minus(Duration.ofDays(BROWSE_LOOKBACK_DAYS));
        List<NewsRow> pooled = new ArrayList<>();
        pooled.addAll(gateIndustry("AI", null, since, dismissals, hidden, now));
        pooled.addAll(gateIndustry("Share Market", "IT", since, dismissals, hidden, now));
        pooled.addAll(gateIndustry("Share Market", "EV", since, dismissals, hidden, now));
        pooled.addAll(gateIndustry("Medical", "Cardiology", since, dismissals, hidden, now));
        pooled.addAll(gateIndustry("Medical", "Gynecology", since, dismissals, hidden, now));
        if (pooled.isEmpty()) {
            Map<String, Object> filled = industry("AI", null, pageSize, "products", BROWSE_LOOKBACK_DAYS, code, who, pageNo);
            filled.put("category", "Default");
            filled.put("label", "Top searched product news");
            filled.put("isDefaultNews", true);
            return filled;
        }
        if (pageNo == 1 && properties.getIngest().isEnabled()) {
            coordinator.fetch("AI", null, NewsFetchCoordinator.Mode.ON_DEMAND,
                    Duration.ofSeconds(Math.max(properties.getIngest().getOnDemandTimeoutSeconds(), 1)));
            pooled = new ArrayList<>();
            pooled.addAll(gateIndustry("AI", null, since, dismissals, hidden, now));
            pooled.addAll(gateIndustry("Share Market", "IT", since, dismissals, hidden, now));
            pooled.addAll(gateIndustry("Share Market", "EV", since, dismissals, hidden, now));
            pooled.addAll(gateIndustry("Medical", "Cardiology", since, dismissals, hidden, now));
            pooled.addAll(gateIndustry("Medical", "Gynecology", since, dismissals, hidden, now));
        }
        Prepared prepared = prepareChronological(pooled, pageNo, pageSize, now, code);
        Map<String, Object> body = payload(
                "Default", null, pageSize, pageNo, prepared.total(), "last 30 days", prepared.ranked(), now, code, prepared.countries());
        body.put("label", "Top searched product news");
        body.put("isDefaultNews", true);
        return body;
    }

    public void invalidateViewer(NewsViewer viewer) {
        if (viewer == null) {
            return;
        }
        String prefix = viewer.cacheKey() + "|";
        cache.keySet().removeIf(key -> key.startsWith(prefix));
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

    /**
     * A lab chip should show that lab's feature launches. Parent AI rows are stored
     * without a sub, so they would otherwise fill Anthropic, OpenAI, and Grok alike.
     */
    static List<NewsRow> labRows(String industry, String sub, List<NewsRow> rows) {
        if (!"AI".equals(industry) || sub == null || sub.isBlank() || rows == null || rows.isEmpty()) {
            return rows == null ? List.of() : rows;
        }
        return rows.stream().filter(row -> matchesAiLab(sub, row)).toList();
    }

    static boolean matchesAiLab(String sub, NewsRow row) {
        if (row == null) {
            return false;
        }
        if (sub.equals(row.sub())) {
            return true;
        }
        String blob = ((row.title() == null ? "" : row.title()) + " " + (row.summary() == null ? "" : row.summary()))
                .toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "Anthropic" -> blob.contains("anthropic") || blob.contains("claude");
            case "OpenAI" -> blob.contains("openai") || blob.contains("chatgpt") || blob.contains("gpt-6") || blob.contains("gpt 6");
            case "Grok" -> blob.contains("grok") || blob.contains("xai");
            case "Google" -> blob.contains("gemini") || blob.contains("gemma") || blob.contains("deepmind");
            case "Meta" -> blob.contains("meta ai") || blob.contains("llama") || blob.contains("muse");
            case "Microsoft" -> blob.contains("copilot") || blob.contains("windows ml");
            default -> blob.contains(sub.toLowerCase(Locale.ROOT));
        };
    }

    static List<NewsRow> withoutDismissed(List<NewsRow> rows, NewsStore.Dismissals dismissals) {
        if (rows == null || rows.isEmpty()) {
            return rows == null ? List.of() : rows;
        }
        if (dismissals == null) {
            return rows;
        }
        Set<UUID> ids = dismissals.itemIds() == null ? Set.of() : dismissals.itemIds();
        Set<String> urls = dismissals.urls() == null ? Set.of() : dismissals.urls();
        if (ids.isEmpty() && urls.isEmpty()) {
            return rows;
        }
        List<NewsRow> out = new ArrayList<>();
        for (NewsRow row : rows) {
            if (row == null) {
                continue;
            }
            if (row.id() != null && ids.contains(row.id())) {
                continue;
            }
            if (row.url() != null && urls.contains(row.url())) {
                continue;
            }
            out.add(row);
        }
        return out;
    }

    /** Newest first within the lookback; estimated dates stay out of the browse list. */
    static List<NewsRow> chronological(List<NewsRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<NewsRow> dated = new ArrayList<>();
        for (NewsRow row : rows) {
            if (row == null || row.dateEstimated() || row.publishedAt() == null) {
                continue;
            }
            dated.add(row);
        }
        dated.sort(Comparator.comparing(NewsRow::publishedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        if (dated.size() > MAX_POOL) {
            return new ArrayList<>(dated.subList(0, MAX_POOL));
        }
        return dated;
    }

    static PageSlice slice(List<NewsRow> ordered, int page, int pageSize) {
        int size = Math.max(1, pageSize);
        int pageNo = Math.max(1, page);
        int total = ordered == null ? 0 : ordered.size();
        if (total == 0) {
            return new PageSlice(List.of(), 0, pageNo, size, 0, false);
        }
        int totalPages = (total + size - 1) / size;
        if (pageNo > totalPages) {
            pageNo = totalPages;
        }
        int from = (pageNo - 1) * size;
        int to = Math.min(from + size, total);
        return new PageSlice(ordered.subList(from, to), total, pageNo, size, totalPages, pageNo < totalPages);
    }

    static int clampPageSize(int limit) {
        if (limit <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
    }

    /** @deprecated Prefer chronological browse; kept for tests that assert widen steps. */
    static int[] windowsFor(int requested) {
        int[] steps = new int[]{1, 7, 30};
        if (requested > 0) {
            steps[0] = requested;
        }
        return steps;
    }

    private List<NewsRow> gateIndustry(
            String industry,
            String sub,
            Instant since,
            NewsStore.Dismissals dismissals,
            Set<UUID> hidden,
            Instant now
    ) {
        List<NewsRow> rows = withoutDismissed(store.eligible(industry, sub, since, false), dismissals);
        refreshRulesScores(rows, now);
        return relevance.gate(industry, rows, now, hidden, NewsRelevanceService.DEFAULT_TOP_N);
    }

    private void refreshRulesScores(List<NewsRow> rows, Instant now) {
        refreshRulesScores(rows, properties.getRank(), now);
    }

    private Prepared prepareChronological(List<NewsRow> rows, int page, int pageSize, Instant now, String country) {
        CountrySignals.assignMissing(rows);
        List<Map<String, Object>> countries = new ArrayList<>(CountrySignals.options(rows));
        CountrySignals.ensureOption(countries, country);
        List<NewsRow> scoped = CountrySignals.matching(rows, country);
        List<NewsRow> ordered = chronological(scoped);
        PageSlice pageSlice = slice(ordered, page, pageSize);
        String message = "";
        if (pageSlice.total() == 0) {
            message = "No business-relevant news from pack sources yet.";
        } else if (pageSlice.total() < 3) {
            message = "Fewer than 3 relevant items right now — we do not pad with junk.";
        }
        return new Prepared(
                new NewsRanker.Ranked(pageSlice.items(), message),
                countries,
                pageSlice.total(),
                pageSlice.page(),
                pageSlice.pageSize(),
                pageSlice.totalPages(),
                pageSlice.hasMore());
    }

    private Map<String, Object> payload(
            String industry,
            String sub,
            int pageSize,
            int page,
            int total,
            String freshness,
            NewsRanker.Ranked ranked,
            Instant now,
            String country,
            List<Map<String, Object>> countries
    ) {
        int totalPages = total <= 0 ? 0 : (total + pageSize - 1) / pageSize;
        int pageNo = totalPages == 0 ? 1 : Math.min(Math.max(page, 1), Math.max(totalPages, 1));
        List<Map<String, Object>> items = new ArrayList<>();
        int index = (pageNo - 1) * pageSize + 1;
        for (NewsRow row : ranked.items()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.id() == null ? "n-" + index : row.id().toString());
            item.put("rank", index);
            item.put("title", row.title());
            item.put("url", row.url());
            item.put("source", row.sourceName() == null || row.sourceName().isBlank() ? row.domain() : row.sourceName());
            item.put("domain", row.domain());
            item.put("host", row.domain());
            item.put("publishedAt", row.publishedAt() == null ? null : row.publishedAt().toString());
            item.put("ageLabel", NewsTexts.ageLabel(row.publishedAt(), now));
            item.put("signalType", row.signalType());
            item.put("opportunityScore", row.opportunityScore());
            item.put("score", row.relevanceScore() > 0 ? row.relevanceScore() : row.opportunityScore());
            item.put("relevanceScore", row.relevanceScore() > 0 ? row.relevanceScore() : row.opportunityScore());
            String whyHere = row.relevanceReason() != null && !row.relevanceReason().isBlank()
                    ? row.relevanceReason()
                    : row.whyIdea();
            item.put("relevanceReason", whyHere);
            item.put("whyIdea", whyHere);
            item.put("why", whyHere);
            item.put("description", row.summary());
            item.put("signals", row.signals() == null ? List.of() : row.signals());
            item.put("alsoCoveredBy", row.alsoCoveredBy());
            item.put("weakSignal", row.weakSignal());
            item.put("sample", row.sample());
            item.put("industry", row.industry());
            item.put("sub", row.sub());
            item.put("country", row.country());
            item.put("countryName", CountrySignals.name(row.country()));
            item.put("sourceId", row.sourceId() == null ? null : row.sourceId().toString());
            items.add(item);
            index++;
        }
        String label = (sub == null || sub.isBlank() ? industry : sub) + " products";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("results", items);
        body.put("items", items);
        body.put("freshness", freshness);
        body.put("requested", pageSize);
        body.put("returned", items.size());
        body.put("page", pageNo);
        body.put("pageSize", pageSize);
        body.put("total", total);
        body.put("totalPages", totalPages);
        body.put("hasMore", pageNo < totalPages);
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
        body.put("sort", "publishedAtDesc");
        body.put("windowDays", BROWSE_LOOKBACK_DAYS);
        if (country != null && items.isEmpty()) {
            body.put("message", "No findings for " + CountrySignals.name(country) + " right now.");
        }
        return body;
    }

    private record Prepared(
            NewsRanker.Ranked ranked,
            List<Map<String, Object>> countries,
            int total,
            int page,
            int pageSize,
            int totalPages,
            boolean hasMore
    ) {
    }

    record PageSlice(List<NewsRow> items, int total, int page, int pageSize, int totalPages, boolean hasMore) {
    }

    private record Cached(Map<String, Object> body, Instant expires) {
    }
}
