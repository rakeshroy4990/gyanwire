package com.gyanwire.ideas.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.llm.ModelTier;
import com.gyanwire.research.engine.LlmService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.gyanwire.ideas.pipeline.IdeaModels.NewsSignals;

public final class AngleStage {

    private final ObjectMapper mapper;

    public AngleStage(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<String> run(IdeaStageClient client, UUID userId, NewsSignals signals) {
        List<String> allowed = AngleType.allowed(signals.affectedGroups());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("signals", signals);
        payload.put("allowedAngles", allowed);
        payload.put("rule", "Always include comparison_content first and education_or_training second when both are in allowedAngles. Then add 1 to 3 other distinct angles. Never reuse an angle as a second format.");
        String user;
        try {
            user = mapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return List.of();
        }
        var node = StageCalls.completeValidated(
                client,
                userId,
                "idea-angles.v1",
                "idea-angles",
                LlmService.prompt("idea-angles.v1.txt"),
                user,
                ModelTier.MAIN,
                json -> IdeaPipelineSchemas.validateAngles(json, allowed),
                2);
        if (node == null) {
            return AngleType.prioritize(List.of(), allowed);
        }
        return AngleType.prioritize(IdeaPipelineSchemas.strings(node, "angles"), allowed);
    }
}
