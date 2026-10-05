package com.gyanwire.research.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmService {

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public LlmService(
            @Value("${app.llm.api-key:}") String apiKey,
            @Value("${app.llm.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${app.llm.model:gpt-4o-mini}") String model,
            ObjectMapper mapper
    ) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl == null ? "https://api.openai.com/v1" : baseUrl.replaceAll("/$", "");
        this.model = model;
        this.mapper = mapper;
    }

    public boolean isConfigured() {
        return !apiKey.isBlank() && !apiKey.contains("your-key");
    }

    public Map<String, Object> refineQuery(List<String> categories, String subcategory, String thoughts) {
        String scope = (subcategory == null || subcategory.isBlank())
                ? String.join(", ", categories)
                : String.join(", ", categories) + " / " + subcategory;
        String fallback = buildFallback(categories, thoughts, subcategory);
        JsonNode refined = chatJson(
                """
                You turn research notes into a precise R&D web search query.
                Return JSON only: {"query":"...","intent":"one short sentence"}.
                Rules:
                - Always frame the query for research and development value
                - Prefer India-first context whenever relevant
                - Prefer papers, trials, patents, labs, technical reports, and primary sources
                - Query max 18 words
                - Keep the person's real intent
                - Do not invent facts they did not mention
                """,
                "Research scope: " + scope + "\nRegion preference: India first, then global\nResearch notes:\n" + thoughts
        );
        if (refined == null || refined.path("query").asText("").isBlank()) {
            return Map.of("query", fallback, "intent", "R&D research in " + scope + ".", "usedLlm", false);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("query", refined.path("query").asText().trim().substring(0, Math.min(180, refined.path("query").asText().trim().length())));
        String intent = refined.path("intent").asText("R&D research in " + scope + ".").trim();
        out.put("intent", intent.substring(0, Math.min(160, intent.length())));
        out.put("usedLlm", true);
        return out;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> blendRankings(List<String> categories, String thoughts, String query, List<Map<String, Object>> results) {
        if (results.isEmpty() || !isConfigured()) {
            return Map.of("results", results, "usedLlm", false);
        }
        List<Map<String, Object>> compact = new ArrayList<>();
        for (Map<String, Object> item : results) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", item.get("id"));
            row.put("title", item.get("title"));
            row.put("url", item.get("url"));
            row.put("description", item.get("description"));
            row.put("pointerScore", item.get("score"));
            row.put("why", item.get("why"));
            compact.add(row);
        }
        try {
            String user = mapper.writeValueAsString(Map.of(
                    "categories", categories,
                    "thoughts", thoughts,
                    "query", query,
                    "results", compact
            ));
            JsonNode ranked = chatJson(
                    """
                    You nudge rankings for an R&D research search engine that already scored pages with pointers.
                    Return JSON only:
                    {"rankings":[{"id":"r-1","delta":-15to15,"why":"one plain sentence under 22 words"}]}.
                    Include every id exactly once.
                    """,
                    user
            );
            if (ranked == null || !ranked.path("rankings").isArray()) {
                return Map.of("results", results, "usedLlm", false);
            }
            Map<String, JsonNode> byId = new HashMap<>();
            for (JsonNode n : ranked.path("rankings")) {
                byId.put(n.path("id").asText(), n);
            }
            List<Map<String, Object>> blended = new ArrayList<>();
            for (Map<String, Object> item : results) {
                Map<String, Object> copy = new HashMap<>(item);
                JsonNode nudge = byId.get(String.valueOf(item.get("id")));
                if (nudge != null) {
                    int score = ((Number) item.getOrDefault("score", 0)).intValue() + nudge.path("delta").asInt(0);
                    copy.put("score", Math.max(0, Math.min(100, score)));
                    if (!nudge.path("why").asText("").isBlank()) {
                        copy.put("why", nudge.path("why").asText());
                    }
                    copy.put("usedLlm", true);
                }
                blended.add(copy);
            }
            blended.sort((a, b) -> Integer.compare(
                    ((Number) b.getOrDefault("score", 0)).intValue(),
                    ((Number) a.getOrDefault("score", 0)).intValue()));
            return Map.of("results", blended, "usedLlm", true);
        } catch (Exception e) {
            return Map.of("results", results, "usedLlm", false);
        }
    }

    private JsonNode chatJson(String system, String user) {
        if (!isConfigured()) return null;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", model);
            payload.put("temperature", 0.2);
            payload.put("response_format", Map.of("type", "json_object"));
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", system),
                    Map.of("role", "user", "content", user)
            ));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400) return null;
            JsonNode root = mapper.readTree(res.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) return null;
            return mapper.readTree(content);
        } catch (Exception e) {
            return null;
        }
    }

    private static String buildFallback(List<String> categories, String thoughts, String subcategory) {
        String cleaned = thoughts.replaceAll("[^\\p{L}\\p{N}\\s'-]", " ").replaceAll("\\s+", " ").trim();
        String scope = subcategory == null || subcategory.isBlank()
                ? String.join(" ", categories)
                : categories.get(0) + " " + subcategory;
        return ("India " + scope + " research " + cleaned).trim();
    }
}
