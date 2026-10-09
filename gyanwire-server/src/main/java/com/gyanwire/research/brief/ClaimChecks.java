package com.gyanwire.research.brief;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClaimChecks {

    private ClaimChecks() {
    }

    public static String validate(JsonNode node) {
        if (node == null || node.path("stance").asText("").isBlank()) {
            return "stance";
        }
        if (!node.path("evidence").isArray()) {
            return "evidence";
        }
        for (JsonNode row : node.path("evidence")) {
            if (!row.path("citations").isArray() || row.path("citations").isEmpty()) {
                return "citation";
            }
        }
        return null;
    }

    public static Map<String, Object> toMap(JsonNode node) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stance", node.path("stance").asText(""));
        List<Map<String, Object>> evidence = new ArrayList<>();
        for (JsonNode row : node.path("evidence")) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("text", row.path("text").asText(""));
            List<Integer> ids = new ArrayList<>();
            for (JsonNode id : row.path("citations")) {
                ids.add(id.asInt());
            }
            item.put("citations", ids);
            evidence.add(item);
        }
        out.put("evidence", evidence);
        return out;
    }
}
