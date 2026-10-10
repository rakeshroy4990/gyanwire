package com.gyanwire.research.service;

import com.gyanwire.config.TracingConfig;
import com.gyanwire.ideas.FindingIdeaPotential;
import com.gyanwire.ideas.IdeaVariety;
import com.gyanwire.ideas.PatternMatcher;
import com.gyanwire.research.engine.DiscoverService;
import com.gyanwire.research.engine.IndiaSupport;
import com.gyanwire.research.engine.LlmService;
import com.gyanwire.research.engine.PointersService;
import com.gyanwire.research.engine.ScrapeService;
import com.gyanwire.research.rank.HybridRanker;
import com.gyanwire.research.rank.TrustBadge;
import com.gyanwire.sources.SourcePackCatalog;
import com.gyanwire.sources.SourcePackService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class SearchEngineService {

    private final DiscoverService discoverService;
    private final ScrapeService scrapeService;
    private final PointersService pointersService;
    private final LlmService llmService;
    private final PageCacheService pageCacheService;
    private final QueryCacheService queryCacheService;
    private final SourcePackService sourcePackService;
    private final SourcePackCatalog sourcePackCatalog;
    private final PatternMatcher patternMatcher;
    private final boolean hybrid;
    private final CircuitBreaker discoverBreaker = CircuitBreaker.ofDefaults("discover");

    public SearchEngineService(
            DiscoverService discoverService,
            ScrapeService scrapeService,
            PointersService pointersService,
            LlmService llmService,
            PageCacheService pageCacheService,
            QueryCacheService queryCacheService,
            SourcePackService sourcePackService,
            SourcePackCatalog sourcePackCatalog,
            PatternMatcher patternMatcher,
            @Value("${app.rank.hybrid:true}") boolean hybrid
    ) {
        this.discoverService = discoverService;
        this.scrapeService = scrapeService;
        this.pointersService = pointersService;
        this.llmService = llmService;
        this.pageCacheService = pageCacheService;
        this.queryCacheService = queryCacheService;
        this.sourcePackService = sourcePackService;
        this.sourcePackCatalog = sourcePackCatalog;
        this.patternMatcher = patternMatcher;
        this.hybrid = hybrid;
    }

    public Map<String, Object> findBestResults(List<String> categories, String subcategory, String thoughts, int limit) {
        return findBestResults(null, categories, subcategory, thoughts, limit, null, null);
    }

    public Map<String, Object> findBestResults(
            java.util.UUID userId,
            List<String> categories,
            String subcategory,
            String thoughts,
            int limit,
            Consumer<String> status,
            Consumer<Map<String, Object>> onFinding
    ) {
        sourcePackService.apply(userId, sourcePackCatalog);
        try {
            return search(userId, categories, subcategory, thoughts, limit, status, onFinding);
        } finally {
            sourcePackCatalog.clear();
        }
    }

    private Map<String, Object> search(
            java.util.UUID userId,
            List<String> categories,
            String subcategory,
            String thoughts,
            int limit,
            Consumer<String> status,
            Consumer<Map<String, Object>> onFinding
    ) {
        emit(status, "discovering");
        var span = TracingConfig.tracer().spanBuilder("search").startSpan();
        Map<String, Object> refined;
        try {
            refined = llmService.refineQuery(categories, subcategory, thoughts);
        } finally {
            span.end();
        }
        String query = String.valueOf(refined.get("query"));
        String cacheKey = queryCacheService.key(query, categories.isEmpty() ? "" : categories.get(0));
        Map<String, Object> cached = queryCacheService.get(cacheKey);
        String industry = categories == null || categories.isEmpty() ? "" : categories.get(0);
        if (cached != null && cached.get("results") instanceof List<?> results && !results.isEmpty()) {
            List<Map<String, Object>> ranked = new ArrayList<>();
            for (Object row : results) {
                if (row instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> finding = (Map<String, Object>) map;
                    ranked.add(finding);
                }
            }
            ranked = applyBusinessIdeaScores(ranked, industry);
            ranked = llmService.fillWhyLines(userId, ranked);
            Map<String, Object> hit = new HashMap<>(cached);
            hit.put("results", ranked);
            hit.put("cacheHit", true);
            if (onFinding != null) {
                for (Map<String, Object> finding : ranked) {
                    onFinding.accept(finding);
                }
            }
            return hit;
        }
        String thoughtsScoped = subcategory == null || subcategory.isBlank() ? thoughts : subcategory + ". " + thoughts;
        emit(status, "reading");
        List<Map<String, Object>> results = searchWeb(query, categories, thoughtsScoped, limit, onFinding);
        if (hybrid && !results.isEmpty()) {
            results = HybridRanker.fuse(results, query, HybridRanker.halfLifeDays(industry));
            for (int i = 0; i < results.size(); i++) {
                results.get(i).put("id", "r-" + (i + 1));
            }
        }

        emit(status, "scoring");
        String blendThoughts = subcategory == null || subcategory.isBlank()
                ? thoughts
                : categories.get(0) + " / " + subcategory + ": " + thoughts;
        Map<String, Object> blended = llmService.blendRankings(categories, blendThoughts, query, results);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> finalResults = (List<Map<String, Object>>) blended.get("results");
        finalResults = applyBusinessIdeaScores(finalResults, industry);
        finalResults = llmService.fillWhyLines(userId, finalResults);

        List<Map<String, Object>> publicResults = new ArrayList<>();
        for (Map<String, Object> item : finalResults) {
            publicResults.add(publicRow(item));
        }

        Map<String, Object> out = new HashMap<>();
        out.put("query", query);
        out.put("intent", refined.get("intent"));
        out.put("engine", "gyanwire");
        out.put("usedLlm", Boolean.TRUE.equals(refined.get("usedLlm")) || Boolean.TRUE.equals(blended.get("usedLlm")));
        out.put("llmAvailable", llmService.isConfigured());
        out.put("categories", categories);
        out.put("subcategory", subcategory);
        out.put("results", publicResults);
        out.put("cacheHit", false);
        queryCacheService.put(cacheKey, out);
        return out;
    }

    private List<Map<String, Object>> searchWeb(
            String query,
            List<String> categories,
            String thoughts,
            int limit,
            Consumer<Map<String, Object>> onFinding
    ) {
        int discoverLimit = Math.min(Math.max(limit * 2, 8), 16);
        List<Map<String, String>> discovered;
        try {
            discovered = discoverBreaker.executeCallable(() -> discoverService.discoverPages(query, discoverLimit, categories));
        } catch (Exception e) {
            discovered = List.of();
        }
        Map<String, Map<String, Object>> scraped = scrapeService.scrapeMany(
                discovered.stream().map(d -> d.get("url")).toList(), 4, 8000);

        List<Map<String, Object>> pages = new ArrayList<>();
        for (int i = 0; i < discovered.size(); i++) {
            Map<String, String> item = discovered.get(i);
            Map<String, Object> page = scraped.getOrDefault(item.get("url"), Map.of());
            Map<String, Object> row = new HashMap<>();
            row.put("id", "r-" + (i + 1));
            row.put("title", page.getOrDefault("title", item.getOrDefault("title", "Untitled page")));
            row.put("url", item.get("url"));
            row.put("description", page.getOrDefault("description", item.getOrDefault("description", "No summary available.")));
            row.put("snippet", page.getOrDefault("snippet", item.getOrDefault("description", "")));
            row.put("text", page.getOrDefault("text", ""));
            row.put("source", item.get("source"));
            row.put("position", i + 1);
            row.put("scraped", Boolean.TRUE.equals(page.get("ok")));
            if (page.get("publishedAt") != null) {
                row.put("publishedAt", page.get("publishedAt"));
                row.put("publishedLabel", page.get("publishedLabel"));
                row.put("ageDays", page.getOrDefault("ageDays", 0));
            } else {
                row.put("ageDays", 0);
            }
            Map<String, Object> pointer = pointersService.score(row, categories, thoughts, query, i);
            row.put("researchScore", pointer.get("score"));
            row.put("why", pointer.get("why"));
            row.put("pointers", pointer.get("breakdown"));
            row.put("usedLlm", false);
            row.put("trust", TrustBadge.from(row));
            String industry = categories == null || categories.isEmpty() ? "" : categories.get(0);
            applyBusinessIdeaScore(row, industry);
            pageCacheService.remember(
                    String.valueOf(row.get("url")),
                    String.valueOf(row.getOrDefault("snippet", "")),
                    String.valueOf(row.getOrDefault("text", ""))
            );
            pages.add(row);
            if (onFinding != null && ideaScore(row) > 0) {
                onFinding.accept(publicRow(row));
            }
        }

        pages = IndiaSupport.sortIndiaFirst(pages, p -> host(String.valueOf(p.get("url"))));
        pages = rankByIdeaScore(pages);
        List<Map<String, Object>> top = pages.subList(0, Math.min(limit, pages.size()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            Map<String, Object> copy = new HashMap<>(top.get(i));
            copy.put("id", "r-" + (i + 1));
            out.add(copy);
        }
        return out;
    }

    /**
     * Recompute business-idea strength, drop zeros, sort strongest idea first.
     */
    List<Map<String, Object>> applyBusinessIdeaScores(List<Map<String, Object>> pages, String industry) {
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> page : pages) {
            Map<String, Object> row = new HashMap<>(page);
            applyBusinessIdeaScore(row, industry);
            copy.add(row);
        }
        return rankByIdeaScore(copy);
    }

    private void applyBusinessIdeaScore(Map<String, Object> row, String industry) {
        String title = String.valueOf(row.getOrDefault("title", ""));
        String description = String.valueOf(row.getOrDefault("description", ""));
        if (description.isBlank()) {
            description = String.valueOf(row.getOrDefault("snippet", ""));
        }
        String text = String.valueOf(row.getOrDefault("text", ""));
        if (!text.isBlank()) {
            description = description + "\n" + text.substring(0, Math.min(text.length(), 800));
        }
        FindingIdeaPotential.Result idea = FindingIdeaPotential.score(
                title, description, industry, "working", patternMatcher.all());
        if (row.get("researchScore") == null && row.get("score") instanceof Number n) {
            row.put("researchScore", n.intValue());
        }
        row.put("score", idea.score());
        row.put("ideaDrivers", idea.drivers());
        row.put("ideaPatterns", idea.patternIds());
        row.put("eventType", idea.eventType());
        if (idea.score() > 0) {
            row.put("ideaCount", IdeaVariety.formatsFor(
                    idea.eventType(),
                    patternMatcher.shapesFor(idea.patternIds()),
                    title + "\n" + description,
                    idea.score()).size());
        }
        if (idea.score() > 0 && idea.why() != null && !idea.why().isBlank()) {
            row.put("why", idea.why());
        }
    }

    /**
     * Findings with no business-idea score are dropped; the rest are highest score first.
     */
    static List<Map<String, Object>> rankByIdeaScore(List<Map<String, Object>> pages) {
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> scored = new ArrayList<>();
        for (Map<String, Object> page : pages) {
            if (ideaScore(page) > 0) {
                scored.add(page);
            }
        }
        scored.sort((a, b) -> Integer.compare(ideaScore(b), ideaScore(a)));
        for (int i = 0; i < scored.size(); i++) {
            scored.get(i).put("id", "r-" + (i + 1));
        }
        return scored;
    }

    private static int ideaScore(Map<String, Object> page) {
        Object value = page == null ? null : page.get("score");
        return value instanceof Number n ? n.intValue() : 0;
    }

    private static Map<String, Object> publicRow(Map<String, Object> item) {
        Map<String, Object> row = new HashMap<>(item);
        row.put("excerpt", item.getOrDefault("snippet", item.get("description")));
        row.remove("text");
        row.remove("snippet");
        if (!row.containsKey("trust")) {
            row.put("trust", TrustBadge.from(item));
        }
        return row;
    }

    private static void emit(Consumer<String> status, String stage) {
        if (status != null) {
            status.accept(stage);
        }
    }

    private static String host(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return "";
        }
    }
}
