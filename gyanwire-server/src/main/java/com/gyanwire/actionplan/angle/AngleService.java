package com.gyanwire.actionplan.angle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.actionplan.format.FormatSpec;
import com.gyanwire.actionplan.news.NewsBrief;
import com.gyanwire.llm.ModelTier;
import com.gyanwire.research.engine.LlmService;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AngleService {

    private static final Pattern TEST = Pattern.compile("(?i)\\d+\\s+\\w+.*\\b(\\d+\\s*(h|hr|hrs|hours?|d|days?)|\\d+\\s*h)\\b|\\d+\\s+views?\\s+in\\s+\\d+");

    private final ObjectMapper mapper;
    private final ObjectProvider<com.gyanwire.ideas.pipeline.IdeaStageClient> stageClient;

    public AngleService(
            ObjectMapper mapper,
            ObjectProvider<com.gyanwire.ideas.pipeline.IdeaStageClient> stageClient
    ) {
        this.mapper = mapper;
        this.stageClient = stageClient;
    }

    public List<Map<String, Object>> pick(NewsBrief brief, FormatSpec format, UUID userId) {
        List<Map<String, Object>> candidates = fromLlm(brief, userId);
        if (candidates.isEmpty()) {
            candidates = fallback(brief);
        }
        List<Map<String, Object>> scored = new ArrayList<>();
        for (Map<String, Object> angle : candidates) {
            if (!validTest(str(angle.get("test")))) {
                angle.put("test", "100 views in 48 h");
            }
            int score = score(brief, angle, format);
            angle.put("score", score);
            scored.add(angle);
        }
        scored.sort(Comparator.comparingInt((Map<String, Object> a) -> intVal(a.get("score"), 0)).reversed());
        if (!scored.isEmpty()) {
            scored.get(0).put("bestBet", true);
            scored.get(0).put("effortMinutes", format == null ? 240 : format.defaultBudgetMinutes());
        }
        return scored.stream().limit(3).toList();
    }

    private List<Map<String, Object>> fromLlm(NewsBrief brief, UUID userId) {
        try {
            String system = LlmService.prompt("action-plan-angles.v1.txt");
            String user = mapper.writeValueAsString(Map.of(
                    "headline", brief.headline(),
                    "entities", brief.entities() == null ? List.of() : brief.entities(),
                    "facts", brief.facts() == null ? List.of() : brief.facts().stream().map(NewsBrief.Fact::text).toList(),
                    "whoItAffects", brief.whoItAffects() == null ? List.of() : brief.whoItAffects(),
                    "freshnessHours", brief.freshnessHours() == null ? "" : brief.freshnessHours()));
            JsonNode node = null;
            var client = stageClient.getIfAvailable();
            if (client != null) {
                node = client.complete(userId, "action-plan-angles.v1", "action-plan-angles", system, user, ModelTier.LIGHT);
            }
            if (node == null || !node.path("angles").isArray()) {
                return List.of();
            }
            List<Map<String, Object>> out = new ArrayList<>();
            int i = 1;
            for (JsonNode angle : node.path("angles")) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", "a" + i++);
                row.put("oneLine", text(angle, "oneLine"));
                row.put("forWhom", text(angle, "forWhom"));
                row.put("whyNow", text(angle, "whyNow"));
                row.put("test", text(angle, "test"));
                if (str(row.get("oneLine")).length() >= 8) {
                    out.add(row);
                }
            }
            return out;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Map<String, Object>> fallback(NewsBrief brief) {
        String entity = brief.entities() != null && !brief.entities().isEmpty()
                ? brief.entities().get(0)
                : "this news";
        String who = brief.whoItAffects() != null && !brief.whoItAffects().isEmpty()
                ? brief.whoItAffects().get(0)
                : "developers";
        Map<String, Object> best = new LinkedHashMap<>();
        best.put("id", "a1");
        best.put("oneLine", "Explain what " + entity + " changes for " + who + " today");
        best.put("forWhom", who);
        best.put("whyNow", brief.freshnessHours() != null && brief.freshnessHours() <= 48
                ? "The story is under two days old"
                : "People are still forming an opinion on this release");
        best.put("test", "100 views in 48 h");
        return List.of(best);
    }

    int score(NewsBrief brief, Map<String, Object> angle, FormatSpec format) {
        int freshness = 20;
        if (brief.freshnessHours() != null) {
            int h = brief.freshnessHours();
            if (h <= 6) {
                freshness = 40;
            } else if (h <= 24) {
                freshness = 30;
            } else if (h <= 72) {
                freshness = 20;
            } else {
                freshness = 5;
            }
        }
        int audience = 15;
        String forWhom = str(angle.get("forWhom")).toLowerCase(Locale.ROOT);
        if (brief.whoItAffects() != null) {
            for (String who : brief.whoItAffects()) {
                if (who != null && forWhom.contains(who.toLowerCase(Locale.ROOT))) {
                    audience = 30;
                    break;
                }
            }
        }
        int factStrength = brief.facts() == null ? 0 : Math.min(20, brief.facts().size() * 5);
        int effortFit = format != null && format.defaultBudgetMinutes() <= 240 ? 10 : 5;
        return freshness + audience + factStrength + effortFit;
    }

    static boolean validTest(String test) {
        if (test == null || test.isBlank()) {
            return false;
        }
        return TEST.matcher(test).find() || test.matches("(?i).*\\d+.*(views?|saves?|visits?).*(in|within).*\\d+.*");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int intVal(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return fallback;
        }
    }
}
