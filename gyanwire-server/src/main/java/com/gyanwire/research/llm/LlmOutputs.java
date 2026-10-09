package com.gyanwire.research.llm;

import com.fasterxml.jackson.databind.JsonNode;

public final class LlmOutputs {

    private LlmOutputs() {
    }

    public static String validateSharpen(JsonNode node) {
        if (node == null || node.path("query").asText("").isBlank()) {
            return "query is required";
        }
        return null;
    }

    public static String validateBlend(JsonNode node) {
        if (node == null || !node.path("rankings").isArray() || node.path("rankings").isEmpty()) {
            return "rankings array is required";
        }
        for (JsonNode row : node.path("rankings")) {
            if (row.path("id").asText("").isBlank()) {
                return "ranking id is required";
            }
        }
        return null;
    }

    public static int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    public static int clampDelta(int delta) {
        return Math.max(-15, Math.min(15, delta));
    }

    public static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
