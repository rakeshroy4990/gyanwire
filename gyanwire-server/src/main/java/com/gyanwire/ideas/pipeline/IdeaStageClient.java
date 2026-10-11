package com.gyanwire.ideas.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.gyanwire.llm.ModelTier;

import java.util.UUID;

/**
 * One completion. The caller owns retries. Flag-off uses {@code LlmClient}
 * (gpt-4o-mini, temperature 0.2). Flag-on uses the tiered client.
 */
public interface IdeaStageClient {

    JsonNode complete(
            UUID userId,
            String promptVersion,
            String feature,
            String system,
            String user,
            ModelTier tier
    );
}
