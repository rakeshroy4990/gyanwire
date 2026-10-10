package com.gyanwire.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gyanwire.ideas.PatternMatcher;
import com.gyanwire.research.engine.DiscoverService;
import com.gyanwire.research.service.IndustryNewsService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Records how stale and how off-intent the news feed is.
 * Prefers a running API. {@code --legacy} calls the on-demand Google News path directly.
 */
public final class NewsBaselineMain {

    private static final List<String[]> QUERIES = List.of(
            new String[]{"Share Market", ""},
            new String[]{"Share Market", "IT"},
            new String[]{"Share Market", "Banking"},
            new String[]{"IT", ""},
            new String[]{"IT", "AI"},
            new String[]{"Medical", ""},
            new String[]{"Medical", "Paediatrics"},
            new String[]{"Medical", "Cardiology"},
            new String[]{"Space", ""},
            new String[]{"Space", "Satellites"},
            new String[]{"Social Media", ""},
            new String[]{"Social Media", "Short Video"},
            new String[]{"Gaming", ""},
            new String[]{"Gaming", "Mobile"},
            new String[]{"Astrology", ""},
            new String[]{"Astrology", "Vedic"}
    );

    private NewsBaselineMain() {
    }

    public static void main(String[] args) throws Exception {
        boolean legacy = false;
        String base = System.getenv().getOrDefault("NEWS_BASELINE_URL", "http://localhost:8080");
        for (String arg : args) {
            if ("--legacy".equals(arg)) {
                legacy = true;
            } else if (arg.startsWith("--url=")) {
                base = arg.substring("--url=".length());
            }
        }
        ObjectMapper mapper = new ObjectMapper();
        Instant now = Instant.now();
        ArrayNode queries = mapper.createArrayNode();
        ArrayNode manual = mapper.createArrayNode();
        String mode = legacy ? "legacy-service" : "api";
        IndustryNewsService legacyService = null;
        if (legacy) {
            legacyService = new IndustryNewsService(mapper, new DiscoverService(mapper, ""), new PatternMatcher());
            legacyService.load();
        }
        for (String[] pair : QUERIES) {
            String industry = pair[0];
            String sub = pair[1].isBlank() ? null : pair[1];
            List<Map<String, Object>> results = legacy
                    ? legacyResults(legacyService, industry, sub)
                    : apiResults(mapper, base, industry, sub);
            ObjectNode summary = NewsBaseline.summarize(mapper, industry, sub, results, now);
            queries.add(summary);
            for (JsonNode item : summary.path("items")) {
                if (manual.size() >= 20) {
                    break;
                }
                ObjectNode label = mapper.createObjectNode();
                label.put("industry", industry);
                label.put("sub", sub == null ? "" : sub);
                label.put("title", item.path("title").asText());
                label.put("ageDays", item.path("ageDays").asLong(-1));
                label.putNull("signalLabel");
                manual.add(label);
            }
            System.out.println(industry + (sub == null ? "" : " / " + sub) + " count=" + summary.path("count").asInt());
        }
        ObjectNode root = mapper.createObjectNode();
        root.put("capturedAt", now.toString());
        root.put("mode", mode);
        root.put("note", "signalLabel on manualReview is filled by hand: launch, funding, approval, regulation, pricing, shortage, research, opinion, or other.");
        root.set("queries", queries);
        root.set("manualReview", manual);
        Path out = Path.of("eval/news-baseline.json");
        if (!Files.exists(out.getParent() == null ? Path.of("eval") : out.getParent())) {
            Files.createDirectories(out.getParent());
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), root);
        System.out.println("Wrote " + out.toAbsolutePath());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> legacyResults(IndustryNewsService service, String industry, String sub) {
        try {
            Map<String, Object> payload = service.getIndustryProductNews(industry, 5, sub);
            Object results = payload.get("results");
            if (results instanceof List<?> list) {
                return (List<Map<String, Object>>) list;
            }
        } catch (Exception ex) {
            System.out.println("legacy failed " + industry + ": " + ex.getMessage());
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> apiResults(ObjectMapper mapper, String base, String industry, String sub) {
        try {
            String path = base + "/api/news/" + java.net.URLEncoder.encode(industry, java.nio.charset.StandardCharsets.UTF_8);
            if (sub != null) {
                path += "?sub=" + java.net.URLEncoder.encode(sub, java.nio.charset.StandardCharsets.UTF_8);
            }
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(path)).timeout(Duration.ofSeconds(40)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());
            JsonNode results = root.path("data").path("results");
            if (!results.isArray()) {
                results = root.path("data").path("items");
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            if (results.isArray()) {
                for (JsonNode node : results) {
                    rows.add(mapper.convertValue(node, Map.class));
                }
            }
            return rows;
        } catch (Exception ex) {
            System.out.println("api failed " + industry + ": " + ex.getMessage());
            return List.of();
        }
    }
}
