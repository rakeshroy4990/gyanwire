package com.gyanwire.llm;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.function.Function;

public final class StructuredCall {

    private StructuredCall() {
    }

    public record Outcome(JsonNode json, ModelTier tier, String escalatedFrom, boolean usedFallback) {
    }

    public static Outcome run(
            ModelTier start,
            Function<ModelTier, JsonNode> call,
            Function<JsonNode, Boolean> valid
    ) {
        ModelTier firstTier = start == null ? ModelTier.LIGHT : start;
        JsonNode first = call.apply(firstTier);
        if (Boolean.TRUE.equals(valid.apply(first))) {
            return new Outcome(first, firstTier, null, false);
        }
        if (firstTier == ModelTier.LIGHT) {
            JsonNode second = call.apply(ModelTier.MAIN);
            if (Boolean.TRUE.equals(valid.apply(second))) {
                return new Outcome(second, ModelTier.MAIN, ModelTier.LIGHT.name(), false);
            }
        }
        return new Outcome(null, firstTier, firstTier == ModelTier.LIGHT ? ModelTier.LIGHT.name() : null, true);
    }
}
