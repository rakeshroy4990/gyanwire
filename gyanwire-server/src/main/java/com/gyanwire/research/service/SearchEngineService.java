package com.gyanwire.research.service;

import com.gyanwire.research.engine.DiscoverService;
import com.gyanwire.research.engine.IndiaSupport;
import com.gyanwire.research.engine.LlmService;
import com.gyanwire.research.engine.PointersService;
import com.gyanwire.research.engine.ScrapeService;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SearchEngineService {

    private final DiscoverService discoverService;
    private final ScrapeService scrapeService;
    private final PointersService pointersService;
    private final LlmService llmService;

    public SearchEngineService(
            DiscoverService discoverService,
            ScrapeService scrapeService,
            PointersService pointersService,
            LlmService llmService
    ) {
        this.discoverService = discoverService;
        this.scrapeService = scrapeService;
        this.pointersService = pointersService;
        this.llmService = llmService;
    }

    public Map<String, Object> findBestResults(List<String> categories, String subcategory, String thoughts, int limit) {
        Map<String, Object> refined = llmService.refineQuery(categories, subcategory, thoughts);
        String query = String.valueOf(refined.get("query"));
        String thoughtsScoped = subcategory == null || subcategory.isBlank() ? thoughts : subcategory + ". " + thoughts;
        List<Map<String, Object>> results = searchWeb(query, categories, thoughtsScoped, limit);

        String blendThoughts = subcategory == null || subcategory.isBlank()
                ? thoughts
                : categories.get(0) + " / " + subcategory + ": " + thoughts;
        Map<String, Object> blended = llmService.blendRankings(categories, blendThoughts, query, results);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> finalResults = (List<Map<String, Object>>) blended.get("results");

        List<Map<String, Object>> publicResults = new ArrayList<>();
        for (Map<String, Object> item : finalResults) {
            Map<String, Object> row = new HashMap<>(item);
            row.put("excerpt", item.getOrDefault("snippet", item.get("description")));
            row.remove("text");
            row.remove("snippet");
            publicResults.add(row);
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
        return out;
    }

    private List<Map<String, Object>> searchWeb(String query, List<String> categories, String thoughts, int limit) {
        int discoverLimit = Math.min(Math.max(limit * 2, 8), 16);
        List<Map<String, String>> discovered = discoverService.discoverPages(query, discoverLimit, categories);
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
            Map<String, Object> pointer = pointersService.score(row, categories, thoughts, query, i);
            row.put("score", pointer.get("score"));
            row.put("why", pointer.get("why"));
            row.put("pointers", pointer.get("breakdown"));
            row.put("usedLlm", false);
            pages.add(row);
        }

        pages = IndiaSupport.sortIndiaFirst(pages, p -> host(String.valueOf(p.get("url"))));
        pages.sort((a, b) -> Integer.compare(
                ((Number) b.getOrDefault("score", 0)).intValue(),
                ((Number) a.getOrDefault("score", 0)).intValue()));
        List<Map<String, Object>> top = pages.subList(0, Math.min(limit, pages.size()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            Map<String, Object> copy = new HashMap<>(top.get(i));
            copy.put("id", "r-" + (i + 1));
            out.add(copy);
        }
        return out;
    }

    private static String host(String url) {
        try { return URI.create(url).getHost(); } catch (Exception e) { return ""; }
    }
}
