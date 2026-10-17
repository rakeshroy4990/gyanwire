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
        // windowDays stays on the API; browse always reads the month and picks
        // 1d → 7d → 30d in memory so the first card is not blocked on ingest.
        int lookback = BROWSE_LOOKBACK_DAYS;
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
        if (pageNo == 1 && properties.getIngest().isEnabled()) {
            coordinator.refreshInBackground(industry, sub);
            if (rows.isEmpty()) {
                boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
                seed.load(industry, prod, true);
                rows = withoutDismissed(
                        labRows(industry, sub, store.eligible(industry, sub, since, true)),
                        dismissals);
            }
        }
        refreshRulesScores(rows, now);
        rows = relevance.gate(industry, rows, now, hidden, Math.max(NewsRelevanceService.DEFAULT_TOP_N, pageSize * 3));
        Prepared prepared = prepareBrowse(rows, pageNo, pageSize, now, code);
        String freshness = prepared.freshness();
        Map<String, Object> body = payload(
                industry,
                sub,
                pageSize,
                pageNo,
                prepared.total(),
                prepared.windowDays(),
                freshness,
                prepared.ranked(),
                now,
                code,
                prepared.countries());
        if (prepared.paintFirst() > 0) {
            body.put("paintFirst", prepared.paintFirst());
        }
        if (!"warming_up".equals(freshness) && !prepared.ranked().items().isEmpty()) {
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
        if (pageNo == 1 && properties.getIngest().isEnabled()) {
            coordinator.refreshInBackground("AI", null);
        }
        if (pooled.isEmpty()) {
            Map<String, Object> filled = industry("AI", null, pageSize, "products", BROWSE_LOOKBACK_DAYS, code, who, pageNo);
            filled.put("category", "Default");
            filled.put("label", "Top searched product news");
            filled.put("isDefaultNews", true);
            return filled;
        }
        Prepared prepared = prepareBrowse(pooled, pageNo, pageSize, now, code);
        Map<String, Object> body = payload(
                "Default",
                null,
                pageSize,
                pageNo,
                prepared.total(),
                prepared.windowDays(),
                prepared.freshness(),
                prepared.ranked(),
                now,
                code,
                prepared.countries());
        if (prepared.paintFirst() > 0) {
            body.put("paintFirst", prepared.paintFirst());
        }
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
     * A sub chip should show that specialty's news. Parent industry rows are often
     * stored without a sub, so they would otherwise fill every chip with generic news.
     */
    static List<NewsRow> labRows(String industry, String sub, List<NewsRow> rows) {
        if (sub == null || sub.isBlank() || rows == null || rows.isEmpty()) {
            return rows == null ? List.of() : rows;
        }
        return rows.stream().filter(row -> NewsSubMatcher.matches(industry, sub, row)).toList();
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

    /**
     * Progressive browse windows: 1 day → 7 days → 30 days, capped by the request.
     */
    static int[] windowsFor(int requested) {
        int cap = requested <= 0 ? BROWSE_LOOKBACK_DAYS : Math.min(requested, BROWSE_LOOKBACK_DAYS);
        int[] all = {1, 7, 30};
        List<Integer> steps = new ArrayList<>();
        for (int day : all) {
            if (day < cap) {
                steps.add(day);
            }
        }
        steps.add(cap);
        return steps.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Page 1 paints the narrowest non-empty window first, then fills from the month.
     * Returns empty with warming_up when nothing is stored yet.
     */
    static FirstPage firstPage(List<NewsRow> ordered, int pageSize, Instant now) {
        int size = Math.max(1, pageSize);
        Instant when = now == null ? Instant.now() : now;
        int usedDays = BROWSE_LOOKBACK_DAYS;
        List<NewsRow> firstSlice = List.of();
        for (int days : new int[]{1, 7, 30}) {
            List<NewsRow> slice = withinDays(ordered, days, when);
            if (!slice.isEmpty()) {
                firstSlice = slice;
                usedDays = days;
                break;
            }
        }
        List<NewsRow> page = new ArrayList<>();
        for (NewsRow row : firstSlice) {
            if (page.size() >= size) {
                break;
            }
            page.add(row);
        }
        int paintFirst = page.size();
        if (page.size() < size && ordered != null) {
            for (NewsRow row : ordered) {
                if (page.size() >= size) {
                    break;
                }
                if (page.contains(row)) {
                    continue;
                }
                page.add(row);
            }
        }
        if (page.isEmpty()) {
            return new FirstPage(List.of(), 0, BROWSE_LOOKBACK_DAYS, "warming_up");
        }
        return new FirstPage(page, paintFirst, usedDays, FreshnessGate.labelForDays(usedDays));
    }

    static List<NewsRow> withinDays(List<NewsRow> rows, int days, Instant now) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Instant cutoff = now.minus(Duration.ofDays(Math.max(days, 1)));
        List<NewsRow> out = new ArrayList<>();
        for (NewsRow row : rows) {
            if (row == null || row.publishedAt() == null) {
                continue;
            }
            if (!row.publishedAt().isBefore(cutoff)) {
                out.add(row);
            }
        }
        return out;
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

    private Prepared prepareBrowse(List<NewsRow> rows, int page, int pageSize, Instant now, String country) {
        CountrySignals.assignMissing(rows);
        List<Map<String, Object>> countries = new ArrayList<>(CountrySignals.options(rows));
        CountrySignals.ensureOption(countries, country);
        List<NewsRow> scoped = CountrySignals.matching(rows, country);
        List<NewsRow> ordered = chronological(scoped);
        int pageNo = Math.max(1, page);
        if (pageNo == 1) {
            FirstPage first = firstPage(ordered, pageSize, now);
            String message = browseMessage(ordered.size());
            return new Prepared(
                    new NewsRanker.Ranked(first.items(), message),
                    countries,
                    ordered.size(),
                    1,
                    pageSize,
                    ordered.isEmpty() ? 0 : (ordered.size() + pageSize - 1) / pageSize,
                    ordered.size() > first.items().size(),
                    first.windowDays(),
                    first.freshness(),
                    first.paintFirst());
        }
        PageSlice pageSlice = slice(ordered, pageNo, pageSize);
        return new Prepared(
                new NewsRanker.Ranked(pageSlice.items(), browseMessage(pageSlice.total())),
                countries,
                pageSlice.total(),
                pageSlice.page(),
                pageSlice.pageSize(),
                pageSlice.totalPages(),
                pageSlice.hasMore(),
                BROWSE_LOOKBACK_DAYS,
                FreshnessGate.labelForDays(BROWSE_LOOKBACK_DAYS),
                0);
    }

    private static String browseMessage(int total) {
        if (total == 0) {
            return "No business-relevant news from pack sources yet.";
        }
        if (total < 3) {
            return "Fewer than 3 relevant items right now — we do not pad with junk.";
        }
        return "";
    }

    private Map<String, Object> payload(
            String industry,
            String sub,
            int pageSize,
            int page,
            int total,
            int windowDays,
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
        body.put("windowDays", Math.max(1, windowDays));
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
            boolean hasMore,
            int windowDays,
            String freshness,
            int paintFirst
    ) {
    }

    record FirstPage(List<NewsRow> items, int paintFirst, int windowDays, String freshness) {
    }

    record PageSlice(List<NewsRow> items, int total, int page, int pageSize, int totalPages, boolean hasMore) {
    }

    private record Cached(Map<String, Object> body, Instant expires) {
    }
}
