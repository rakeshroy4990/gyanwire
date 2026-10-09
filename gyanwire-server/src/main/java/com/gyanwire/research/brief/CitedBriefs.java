package com.gyanwire.research.brief;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CitedBriefs {

    private CitedBriefs() {
    }

    public static String validate(JsonNode node) {
        if (node == null || !node.has("summary")) {
            return "summary";
        }
        if (claimsMissing(node.path("summary_claims")) || claimsMissing(node.path("agreements")) || claimsMissing(node.path("conflicts"))) {
            return "citation";
        }
        return null;
    }

    private static boolean claimsMissing(JsonNode array) {
        if (array == null || !array.isArray()) {
            return false;
        }
        for (JsonNode claim : array) {
            JsonNode ids = claim.path("citations");
            if (!ids.isArray() || ids.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static Map<String, Object> toMap(JsonNode node) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", node.path("summary").asText(""));
        out.put("agreements", array(node.path("agreements")));
        out.put("conflicts", array(node.path("conflicts")));
        out.put("followUps", textArray(node.path("follow_ups")));
        return out;
    }

    private static List<Map<String, Object>> array(JsonNode node) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("text", item.path("text").asText(""));
                List<Integer> ids = new ArrayList<>();
                for (JsonNode id : item.path("citations")) {
                    ids.add(id.asInt());
                }
                row.put("citations", ids);
                rows.add(row);
            }
        }
        return rows;
    }

    private static List<String> textArray(JsonNode node) {
        List<String> rows = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                rows.add(item.asText());
            }
        }
        return rows;
    }
}
