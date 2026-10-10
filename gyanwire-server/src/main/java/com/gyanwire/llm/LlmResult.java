package com.gyanwire.llm;

import com.fasterxml.jackson.databind.JsonNode;

public record LlmResult(
        JsonNode json,
        String text,
        int inputTokens,
        int cachedInputTokens,
        int outputTokens,
        String model,
        int latencyMs,
        boolean ok,
        String error,
        ModelTier tier,
        String escalatedFrom,
        boolean skipped
) {
    public static LlmResult skipped(String reason) {
        return new LlmResult(null, null, 0, 0, 0, ModelCatalog.LIGHT_MODEL, 0, false, reason, ModelTier.LIGHT, null, true);
    }
}
