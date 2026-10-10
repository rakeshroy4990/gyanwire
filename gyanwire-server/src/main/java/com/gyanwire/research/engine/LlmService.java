package com.gyanwire.research.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.llm.LlmClient;
import com.gyanwire.llm.LlmRequest;
import com.gyanwire.llm.LlmResult;
import com.gyanwire.llm.ModelTier;
import com.gyanwire.llm.PassageWindow;
import com.gyanwire.llm.QuerySharpener;
import com.gyanwire.llm.RerankGate;
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
    private final ObjectProvider<com.gyanwire.llm.LlmService> tiered;

    public LlmService(LlmClient llmClient, ObjectMapper mapper) {
        this(llmClient, mapper, emptyGuard(), emptyTiered());
    }

    public LlmService(LlmClient llmClient, ObjectMapper mapper, ObjectProvider<SpendGuard> spendGuard) {
        this(llmClient, mapper, spendGuard, emptyTiered());
    }

    @Autowired
    public LlmService(
            LlmClient llmClient,
            ObjectMapper mapper,
            ObjectProvider<SpendGuard> spendGuard,
            ObjectProvider<com.gyanwire.llm.LlmService> tiered
    ) {
        this.llmClient = llmClient;
        this.mapper = mapper;
        this.spendGuard = spendGuard;
        this.tiered = tiered == null ? emptyTiered() : tiered;
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
        com.gyanwire.llm.LlmService routed = tiered();
        if (routed != null && routed.stageEnabled("sharpen") && !QuerySharpener.messy(thoughts)) {
            return Map.of("query", fallback, "intent", "R&D research in " + scope + ".", "usedLlm", false);
        }
        if (routed != null && routed.routerEnabled() && (routed.stageEnabled("sharpen") || routed.shadow())) {
            LlmResult result = routed.callValidated(
                    new LlmRequest("sharpen", ModelTier.LIGHT, prompt("query-sharpen.v1.txt"),
                            "Research scope: " + scope + "\nRegion preference: India first, then global\nResearch notes:\n" + thoughts,
                            null, null),
                    node -> LlmOutputs.validateSharpen(node) == null
            );
            if (!routed.shadow() && result.ok() && result.json() != null) {
                Map<String, Object> out = new HashMap<>();
                out.put("query", LlmOutputs.truncate(result.json().path("query").asText(), 180));
                out.put("intent", LlmOutputs.truncate(result.json().path("intent").asText("R&D research in " + scope + "."), 160));
                out.put("usedLlm", true);
                return out;
            }
            if (!routed.shadow() && routed.stageEnabled("sharpen")) {
                return Map.of("query", fallback, "intent", "R&D research in " + scope + ".", "usedLlm", false);
            }
        }
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
        com.gyanwire.llm.LlmService routed = tiered();
        if (routed != null && routed.stageEnabled("rerank") && !RerankGate.shouldRerank(results)) {
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
                    node -> {
                        String error = LlmOutputs.validateBlend(node);
                        if (error != null) {
                            return error;
                        }
                        if (node.has("confidence") && node.path("confidence").asDouble(1) < 0.6) {
                            return "confidence below threshold";
                        }
                        return null;
                    }
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
        com.gyanwire.llm.LlmService routed = tiered();
        if (routed != null && routed.routerEnabled() && !routed.shadow()) {
            String stage = stageFor(feature);
            ModelTier tier = "hardcase".equals(stage) ? ModelTier.MAIN : ModelTier.LIGHT;
            String effort = "plan_b".equals(stage) ? "high" : null;
            LlmResult result = routed.callValidated(
                    new LlmRequest(stage, tier, system, user, userId, null, effort, null),
                    node -> validate.apply(node) == null
            );
            return result.ok() ? result.json() : null;
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

    public List<Map<String, Object>> fillWhyLines(UUID userId, List<Map<String, Object>> results) {
        com.gyanwire.llm.LlmService routed = tiered();
        if (routed == null || !routed.stageEnabled("whylines") || results == null || results.isEmpty()) {
            return results;
        }
        StringBuilder input = new StringBuilder();
        int count = 0;
        for (Map<String, Object> row : results) {
            if (count++ >= 5) {
                break;
            }
            input.append(row.get("id")).append(": ")
                    .append(PassageWindow.trim(String.valueOf(row.getOrDefault("text", row.getOrDefault("snippet", "")))))
                    .append('\n');
        }
        LlmResult result = routed.call(new LlmRequest(
                "whylines",
                ModelTier.LIGHT,
                "Return JSON only: {\"lines\":[{\"id\":\"r-1\",\"why\":\"one sentence\"}]}. Do not invent URLs.",
                input.toString(),
                userId,
                null
        ));
        if (!result.ok() || result.json() == null || !result.json().path("lines").isArray()) {
            return results;
        }
        for (JsonNode line : result.json().path("lines")) {
            String id = line.path("id").asText("");
            String why = line.path("why").asText("");
            if (id.isBlank() || why.isBlank()) {
                continue;
            }
            for (Map<String, Object> row : results) {
                if (id.equals(String.valueOf(row.get("id")))) {
                    row.put("why", why);
                }
            }
        }
        return results;
    }

    private com.gyanwire.llm.LlmService tiered() {
        try {
            return tiered.getIfAvailable();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String stageFor(String feature) {
        return switch (feature == null ? "" : feature) {
            case "query-sharpen" -> "sharpen";
            case "blend-rank" -> "rerank";
            case "idea" -> "hardcase";
            case "outline", "skill-plan" -> "plan_b";
            case "cited-brief", "claim-check" -> "whylines";
            default -> feature;
        };
    }

    private static ObjectProvider<com.gyanwire.llm.LlmService> emptyTiered() {
        return new ObjectProvider<>() {
            @Override
            public com.gyanwire.llm.LlmService getObject() {
                return null;
            }

            @Override
            public com.gyanwire.llm.LlmService getObject(Object... args) {
                return null;
            }

            @Override
            public com.gyanwire.llm.LlmService getIfAvailable() {
                return null;
            }

            @Override
            public com.gyanwire.llm.LlmService getIfUnique() {
                return null;
            }
        };
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
        List<String> cats = categories == null ? List.of() : categories;
        String scope = subcategory == null || subcategory.isBlank() || cats.isEmpty()
                ? String.join(" ", cats)
                : cats.get(0) + " " + subcategory;
        return ("India " + scope + " research " + cleaned).replaceAll("\\s+", " ").trim();
    }
}
