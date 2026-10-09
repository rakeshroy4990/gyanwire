package com.gyanwire.research.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.llm.LlmClient;
import com.gyanwire.research.llm.LlmOutputs;
import com.gyanwire.usage.SpendGuard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
public class LlmService {

    static final String QUERY_SHARPEN_VERSION = "query-sharpen.v1";
    static final String BLEND_RANK_VERSION = "blend-rank.v1";

    private final LlmClient llmClient;
    private final ObjectMapper mapper;
    private final ObjectProvider<SpendGuard> spendGuard;

    public LlmService(LlmClient llmClient, ObjectMapper mapper) {
        this(llmClient, mapper, emptyGuard());
    }

    @Autowired
    public LlmService(LlmClient llmClient, ObjectMapper mapper, ObjectProvider<SpendGuard> spendGuard) {
        this.llmClient = llmClient;
        this.mapper = mapper;
        this.spendGuard = spendGuard;
    }

    private static ObjectProvider<SpendGuard> emptyGuard() {
        return new ObjectProvider<>() {
            @Override
            public SpendGuard getObject() {
                return null;
            }

            @Override
            public SpendGuard getObject(Object... args) {
                return null;
            }

            @Override
            public SpendGuard getIfAvailable() {
                return null;
            }

            @Override
            public SpendGuard getIfUnique() {
                return null;
            }
        };
    }

    public boolean isConfigured() {
        return llmClient.isConfigured();
    }

    public Map<String, Object> refineQuery(List<String> categories, String subcategory, String thoughts) {
        String scope = (subcategory == null || subcategory.isBlank())
                ? String.join(", ", categories)
                : String.join(", ", categories) + " / " + subcategory;
        String fallback = buildFallback(categories, thoughts, subcategory);
        JsonNode refined = completeValidated(
                null,
                QUERY_SHARPEN_VERSION,
                "query-sharpen",
                prompt("query-sharpen.v1.txt"),
                "Research scope: " + scope + "\nRegion preference: India first, then global\nResearch notes:\n" + thoughts,
                LlmOutputs::validateSharpen
        );
        if (refined == null) {
            return Map.of("query", fallback, "intent", "R&D research in " + scope + ".", "usedLlm", false);
        }
        Map<String, Object> out = new HashMap<>();
        String query = LlmOutputs.truncate(refined.path("query").asText(), 180);
        out.put("query", query);
        out.put("intent", LlmOutputs.truncate(refined.path("intent").asText("R&D research in " + scope + "."), 160));
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
            row.put("description", PageText.forModel(String.valueOf(item.getOrDefault("description", ""))));
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
            JsonNode ranked = completeValidated(
                    null,
                    BLEND_RANK_VERSION,
                    "blend-rank",
                    prompt("blend-rank.v1.txt"),
                    user,
                    LlmOutputs::validateBlend
            );
            if (ranked == null) {
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
                    int score = ((Number) item.getOrDefault("score", 0)).intValue()
                            + LlmOutputs.clampDelta(nudge.path("delta").asInt(0));
                    copy.put("score", LlmOutputs.clampScore(score));
                    String why = LlmOutputs.truncate(nudge.path("why").asText(""), 160);
                    if (!why.isBlank()) {
                        copy.put("why", why);
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

    public JsonNode completeValidated(
            UUID userId,
            String promptVersion,
            String feature,
            String system,
            String user,
            Function<JsonNode, String> validate
    ) {
        if (!spendAllowed(userId)) {
            return null;
        }
        JsonNode first = llmClient.complete(userId, feature, promptVersion, system, user);
        String error = validate.apply(first);
        if (error == null) {
            return first;
        }
        if (!spendAllowed(userId)) {
            return null;
        }
        JsonNode second = llmClient.complete(
                userId,
                feature,
                promptVersion,
                system,
                user + "\nValidation error: " + error + "\nReturn JSON that fixes it."
        );
        if (validate.apply(second) != null) {
            return null;
        }
        return second;
    }

    private boolean spendAllowed(UUID userId) {
        SpendGuard guard = spendGuard.getIfAvailable();
        return guard == null || guard.allow(userId);
    }

    public static String prompt(String name) {
        try {
            return new String(new ClassPathResource("prompts/" + name).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "Return JSON only.";
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
