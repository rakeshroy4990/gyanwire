package com.gyanwire.ideas.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.llm.ModelTier;
import com.gyanwire.research.engine.LlmService;

import java.util.UUID;

import static com.gyanwire.ideas.pipeline.IdeaModels.NewsSignals;
import static com.gyanwire.ideas.pipeline.IdeaModels.OpportunityGate;

public final class GateStage {

    private final ObjectMapper mapper;

    public GateStage(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public OpportunityGate run(IdeaStageClient client, UUID userId, NewsSignals signals) {
        String user;
        try {
            user = mapper.writeValueAsString(signals);
        } catch (Exception ex) {
            return null;
        }
        var node = StageCalls.completeValidated(
                client,
                userId,
                "idea-gate.v1",
                "idea-gate",
                LlmService.prompt("idea-gate.v1.txt"),
                user,
                ModelTier.LIGHT,
                IdeaPipelineSchemas::validateGate,
                2);
        return IdeaPipelineSchemas.parseGate(node);
    }
}
