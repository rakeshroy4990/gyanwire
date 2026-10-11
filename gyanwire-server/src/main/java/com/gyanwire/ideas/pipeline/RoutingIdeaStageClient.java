package com.gyanwire.ideas.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.gyanwire.llm.LlmClient;
import com.gyanwire.llm.LlmRequest;
import com.gyanwire.llm.LlmResult;
import com.gyanwire.llm.ModelTier;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RoutingIdeaStageClient implements IdeaStageClient {

    private final com.gyanwire.llm.LlmService tiered;
    private final LlmClient llmClient;

    public RoutingIdeaStageClient(com.gyanwire.llm.LlmService tiered, LlmClient llmClient) {
        this.tiered = tiered;
        this.llmClient = llmClient;
    }

    @Override
    public JsonNode complete(
            UUID userId,
            String promptVersion,
            String feature,
            String system,
            String user,
            ModelTier tier
    ) {
        if (tiered != null && tiered.routerEnabled() && !tiered.shadow()) {
            LlmResult result = tiered.call(new LlmRequest(
                    feature,
                    tier == null ? ModelTier.LIGHT : tier,
                    system,
                    user,
                    userId,
                    null));
            return result.ok() ? result.json() : null;
        }
        return llmClient.complete(userId, feature, promptVersion, system, user);
    }
}
