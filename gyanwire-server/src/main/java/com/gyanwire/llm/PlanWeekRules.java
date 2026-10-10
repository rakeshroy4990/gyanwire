package com.gyanwire.llm;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.Set;

public final class PlanWeekRules {

    private PlanWeekRules() {
    }

    public static boolean valid(JsonNode node, Set<String> catalogIds, int expectedTotal) {
        JsonNode weeks = node == null ? null : node.path("weeks");
        if (weeks == null || !weeks.isArray() || weeks.size() != 12) {
            return false;
        }
        Set<String> titles = new HashSet<>();
        Set<String> tools = new HashSet<>();
        int sum = 0;
        for (JsonNode week : weeks) {
            String title = week.path("title").asText("").trim();
            String tool = week.path("toolId").asText("").trim();
            if (title.isBlank() || tool.isBlank() || titles.contains(title)) {
                return false;
            }
            if (catalogIds != null && !catalogIds.isEmpty() && !catalogIds.contains(tool)) {
                return false;
            }
            titles.add(title);
            if (tools.add(tool)) {
                sum += week.path("costInr").asInt(0);
            }
        }
        return sum == expectedTotal;
    }
}
