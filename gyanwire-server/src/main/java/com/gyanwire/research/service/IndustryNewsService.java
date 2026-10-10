package com.gyanwire.research.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.ideas.FindingIdeaPotential;
import com.gyanwire.ideas.IdeaVariety;
import com.gyanwire.ideas.PatternMatcher;
import com.gyanwire.research.ResearchException;
import com.gyanwire.research.engine.CuriosityRank;
import com.gyanwire.research.engine.DiscoverService;
import com.gyanwire.research.engine.IndiaSupport;
import com.gyanwire.research.engine.PublishedDates;
import jakarta.annotation.PostConstruct;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class IndustryNewsService {

    private final ObjectMapper mapper;
    private final DiscoverService discoverService;
    private final PatternMatcher patternMatcher;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private Map<String, JsonNode> byName = Map.of();

    public IndustryNewsService(ObjectMapper mapper, DiscoverService discoverService, PatternMatcher patternMatcher) {
        this.mapper = mapper;
        this.discoverService = discoverService;
        this.patternMatcher = patternMatcher;
    }

    @PostConstruct
    public void load() throws Exception {
        JsonNode root = mapper.readTree(new ClassPathResource("industry-news.json").getInputStream());
        Map<String, JsonNode> map = new LinkedHashMap<>();
        for (JsonNode node : root) {
            map.put(node.path("name").asText(), node);
        }
        byName = map;
    }

    public Map<String, Object> getIndustryProductNews(String category, int limit, String sub) {
        JsonNode config = byName.get(category);
        if (config == null) {
            throw new ResearchException("Unknown research industry: " + category, "UNKNOWN_INDUSTRY", 400);
        }
        JsonNode active = config;
        String activeSub = null;
        if (sub != null && !sub.isBlank()) {
            JsonNode found = null;
            for (JsonNode s : config.path("subs")) {
                if (sub.equals(s.path("name").asText())) { found = s; break; }
            }
            if (found == null) {
                throw new ResearchException("Unknown sub-combination: " + sub, "UNKNOWN_SUB", 400);
            }
            active = found;
            activeSub = sub;
        }

        List<String> queries = new ArrayList<>();
        for (JsonNode q : active.path("queries")) queries.add(q.asText());
        List<String> blockGeneric = new ArrayList<>();
        for (JsonNode b : config.path("blockGeneric")) blockGeneric.add(b.asText());

        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> results = new ArrayList<>();
        List<String> queryPlan = new ArrayList<>();
        for (String q : queries) queryPlan.addAll(IndiaSupport.indiaFirstQueries(q));

        for (String query : queryPlan) {
            if (results.size() >= limit) break;
            try {
                for (Map<String, Object> item : fetchGoogleNewsRss(query, limit + 4, active.path("why").asText(), blockGeneric)) {
                    if (results.size() >= Math.max(limit * 2, 8)) break;
                    String key = normalizeKey(String.valueOf(item.get("title")));
                    if (seen.contains(key) || isGeneric(String.valueOf(item.get("title")), blockGeneric)
                            || CuriosityRank.looksGeneric(String.valueOf(item.get("title")))) continue;
                    seen.add(key);
                    item.put("category", category);
                    item.put("sub", activeSub);
                    item.put("why", item.getOrDefault("why", active.path("why").asText()));
                    applyIdeaScore(item, category);
                    results.add(item);
                }
            } catch (Exception ignored) {}
        }

        if (results.size() < limit) {
            String fallback = (activeSub == null ? category + " product launch OR breakthrough OR update research"
                    : category + " " + activeSub + " product launch OR breakthrough OR update");
            try {
                List<Map<String, String>> discovered = discoverService.discoverPages(fallback, limit + 4, List.of(category));
                for (Map<String, String> item : discovered) {
                    if (results.size() >= Math.max(limit * 2, 8)) break;
                    String key = normalizeKey(item.get("title"));
                    if (seen.contains(key) || isGeneric(item.get("title"), blockGeneric)
                            || CuriosityRank.looksGeneric(item.get("title"))) continue;
                    seen.add(key);
                    Map<String, Object> row = new HashMap<>();
                    row.put("title", item.get("title"));
                    row.put("url", item.get("url"));
                    row.put("description", item.getOrDefault("description", active.path("why").asText()));
                    row.put("why", active.path("why").asText());
                    row.put("source", item.getOrDefault("source", "web"));
                    row.put("category", category);
                    row.put("sub", activeSub);
                    applyIdeaScore(row, category);
                    results.add(row);
                }
            } catch (Exception ignored) {}
        }

        results = IndiaSupport.sortIndiaFirst(results, r -> host(String.valueOf(r.get("url"))));
        List<Map<String, Object>> scored = new ArrayList<>();
        for (Map<String, Object> item : results) {
            Map<String, Object> copy = new HashMap<>(item);
            applyIdeaScore(copy, category);
            int ideaScore = copy.get("score") instanceof Number n ? n.intValue() : 0;
            if (ideaScore <= 0) {
                continue;
            }
            scored.add(copy);
        }
        scored.sort((a, b) -> Integer.compare(
                ((Number) b.getOrDefault("score", 0)).intValue(),
                ((Number) a.getOrDefault("score", 0)).intValue()));
        List<Map<String, Object>> ordered = scored.subList(0, Math.min(limit, scored.size()));
        List<Map<String, Object>> withIds = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            Map<String, Object> copy = new HashMap<>(ordered.get(i));
            copy.put("id", "n-" + (i + 1));
            withIds.add(copy);
        }

        Map<String, Object> out = new HashMap<>();
        out.put("query", queries.isEmpty() ? category : IndiaSupport.indiaFirstQueries(queries.get(0)).get(0));
        out.put("category", category);
        out.put("sub", activeSub);
        out.put("label", active.path("label").asText(category));
        out.put("regionPreference", "India first");
        out.put("results", withIds);
        return out;
    }

    public Map<String, Object> getDefaultProductNews(int limit) {
        List<Map<String, Object>> collected = new ArrayList<>();
        record Part(String category, String sub, int take) {}
        for (Part part : List.of(
                new Part("Share Market", "IT", 2),
                new Part("Share Market", "EV", 1),
                new Part("Medical", "Cardiology", 1),
                new Part("Medical", "Gynecology", 1)
        )) {
            int remaining = limit - collected.size();
            if (remaining <= 0) break;
            Map<String, Object> batch = getIndustryProductNews(part.category(), Math.min(part.take(), remaining), part.sub());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) batch.get("results");
            collected.addAll(rows);
        }
        List<Map<String, Object>> sliced = collected.subList(0, Math.min(limit, collected.size()));
        List<Map<String, Object>> withIds = new ArrayList<>();
        for (int i = 0; i < sliced.size(); i++) {
            Map<String, Object> copy = new HashMap<>(sliced.get(i));
            copy.put("id", "n-" + (i + 1));
            withIds.add(copy);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("query", "share IT/EV + medical cardiology/gynecology products");
        out.put("category", "Default");
        out.put("label", "Top searched product news");
        out.put("results", withIds);
        return out;
    }

    private List<Map<String, Object>> fetchGoogleNewsRss(String query, int limit, String why, List<String> blockGeneric) throws Exception {
        String url = "https://news.google.com/rss/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&hl=en-IN&gl=IN&ceid=IN:en&when:7d";
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", "GyanwireBot/1.0 (+local research tool)")
                .header("Accept", "application/rss+xml, application/xml, text/xml")
                .GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400) throw new IllegalStateException("rss " + res.statusCode());
        Document doc = Jsoup.parse(res.body(), "", org.jsoup.parser.Parser.xmlParser());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Element item : doc.select("item")) {
            if (items.size() >= limit) break;
            String title = item.selectFirst("title") == null ? "" : item.selectFirst("title").text();
            String link = item.selectFirst("link") == null ? "" : item.selectFirst("link").text();
            String description = item.selectFirst("description") == null ? "" : Jsoup.parse(item.selectFirst("description").text()).text();
            String pubDate = item.selectFirst("pubDate") == null ? "" : item.selectFirst("pubDate").text();
            if (title.isBlank() || link.isBlank() || isGeneric(title, blockGeneric)) continue;
            Map<String, Object> row = new HashMap<>();
            row.put("title", title);
            row.put("url", link);
            row.put("description", description.isBlank() ? why : description);
            row.put("why", why);
            row.put("source", "google-news");
            row.put("score", Math.max(60, 95 - items.size() * 5));
            Instant published = PublishedDates.parse(pubDate);
            if (published != null) {
                PublishedDates.apply(row, published);
            }
            items.add(row);
        }
        return items;
    }

    private void applyIdeaScore(Map<String, Object> item, String industry) {
        FindingIdeaPotential.Result idea = FindingIdeaPotential.score(
                String.valueOf(item.getOrDefault("title", "")),
                String.valueOf(item.getOrDefault("description", "")),
                industry,
                "working",
                patternMatcher.all()
        );
        item.put("score", idea.score());
        item.put("ideaDrivers", idea.drivers());
        item.put("ideaPatterns", idea.patternIds());
        item.put("eventType", idea.eventType());
        if (idea.score() > 0) {
            String title = String.valueOf(item.getOrDefault("title", ""));
            String description = String.valueOf(item.getOrDefault("description", ""));
            item.put("ideaCount", IdeaVariety.formatsFor(
                    idea.eventType(),
                    patternMatcher.shapesFor(idea.patternIds()),
                    title + "\n" + description,
                    idea.score()).size());
        }
        if (idea.score() > 0 && idea.why() != null && !idea.why().isBlank()) {
            item.put("why", idea.why());
        }
    }

    private static boolean isGeneric(String title, List<String> blockGeneric) {
        String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
        for (String b : blockGeneric) {
            if (t.contains(b.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static String normalizeKey(String title) {
        return title == null ? "" : title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static String host(String url) {
        try { return URI.create(url).getHost(); } catch (Exception e) { return ""; }
    }
}
