package com.gyanwire.llm;

import java.util.UUID;

public record LlmRequest(
        String stage,
        ModelTier tier,
        String system,
        String input,
        UUID userId,
        String planCode,
        String effortOverride,
        Integer maxOutputTokens
) {
    public LlmRequest(String stage, ModelTier tier, String system, String input, UUID userId, String planCode) {
        this(stage, tier, system, input, userId, planCode, null, null);
    }
}
