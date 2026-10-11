package com.gyanwire.ideas.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.llm.ModelTier;
import com.gyanwire.research.engine.LlmService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.gyanwire.ideas.pipeline.IdeaModels.BusinessIdea;
import static com.gyanwire.ideas.pipeline.IdeaModels.CriticResult;
import static com.gyanwire.ideas.pipeline.IdeaModels.NewsSignals;
import static com.gyanwire.ideas.pipeline.IdeaModels.Removal;

public final class CriticStage {

    private final ObjectMapper mapper;

    public CriticStage(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public CriticResult run(IdeaStageClient client, UUID userId, NewsSignals signals, List<BusinessIdea> ideas) {
        CriticResult rules = IdeaCriticRules.review(ideas, signals);
        if (rules.kept().isEmpty()) {
            finish(rules);
            return rules;
        }
        String user;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("signals", signals);
            payload.put("ideas", rules.kept());
            user = mapper.writeValueAsString(payload);
        } catch (Exception ex) {
            finish(rules);
            return rules;
        }
        JsonNode node = StageCalls.completeValidated(
                client,
                userId,
                "idea-critic.v1",
                "idea-critic",
                LlmService.prompt("idea-critic.v1.txt"),
                user,
                ModelTier.LIGHT,
                CriticStage::validateCritic,
                2);
        CriticResult merged = merge(rules, node);
        finish(merged);
        return merged;
    }

    static String validateCritic(JsonNode node) {
        if (node == null || !node.isObject()) {
            return "missing json";
        }
        if (!node.path("keep").isArray() && !node.path("removed").isArray()) {
            return "keep or removed";
        }
        return null;
    }

    static CriticResult merge(CriticResult rules, JsonNode node) {
        if (node == null || !node.path("removed").isArray()) {
            return rules;
        }
        List<BusinessIdea> kept = new ArrayList<>();
        List<Removal> removed = new ArrayList<>(rules.removed());
        Map<String, String> extra = new LinkedHashMap<>();
        for (JsonNode row : node.path("removed")) {
            String angle = row.path("angleType").asText("").trim();
            String reason = row.path("reason").asText("").trim();
            if (!angle.isBlank() && !reason.isBlank()) {
                extra.put(angle, reason);
            }
        }
        for (BusinessIdea idea : rules.kept()) {
            String reason = extra.get(idea.angleType());
            // Social content and education stay when rules already accepted them.
            if (reason == null || AngleType.isPreferred(idea.angleType())) {
                kept.add(idea);
            } else {
                removed.add(new Removal(idea.angleType(), idea.title(), reason));
            }
        }
        return new CriticResult(List.copyOf(kept), List.copyOf(removed));
    }

    private static void finish(CriticResult result) {
        PipelineLog.critic(result.kept().size(), result.removed().size());
        for (Removal removal : result.removed()) {
            PipelineLog.removed(removal.angleType(), removal.reason());
        }
    }
}
