package com.gyanwire.ideas;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SignalSchemas {

    public static final Set<String> EVENT_TYPES = Set.of(
            "regulation", "price_move", "tech_release", "funding", "supply_disruption",
            "consumer_trend", "infrastructure", "exit_failure", "skills_gap", "data_release",
            "event_season", "govt_scheme", "other"
    );

    private SignalSchemas() {
    }

    public static String validate(JsonNode node) {
        if (node == null || node.isNull()) {
            return "missing json";
        }
        String event = node.path("event_type").asText("");
        if (!EVENT_TYPES.contains(event)) {
            return "event_type";
        }
        int magnitude = node.path("magnitude").asInt(-1);
        if (magnitude < 1 || magnitude > 5) {
            return "magnitude";
        }
        String direction = node.path("direction").asText("");
        if (!Set.of("up", "down", "neutral").contains(direction)) {
            return "direction";
        }
        if (!node.has("evidence_quality")) {
            return "evidence_quality";
        }
        return null;
    }

    public static Map<String, Object> fallback(String text, String industry) {
        String blob = text == null ? "" : text.toLowerCase(Locale.ROOT);
        String event = "other";
        if (blob.contains("sebi") || blob.contains("rbi") || blob.contains("regulation") || blob.contains("circular")) {
            event = "regulation";
        } else if (blob.contains("price") || blob.contains("cost")) {
            event = "price_move";
        } else if (blob.contains("launch") || blob.contains("release") || blob.contains("api")) {
            event = "tech_release";
        } else if (blob.contains("funding") || blob.contains("raised")) {
            event = "funding";
        } else if (blob.contains("scheme") || blob.contains("pli") || blob.contains("tender")) {
            event = "govt_scheme";
        }
        Map<String, Object> signal = new LinkedHashMap<>();
        signal.put("event_type", event);
        signal.put("entities", List.of());
        signal.put("geography", blob.contains("india") ? "India" : "global");
        signal.put("sector", industry == null ? "" : industry);
        signal.put("industry", industry == null ? "" : industry);
        int magnitude = switch (event) {
            case "regulation", "govt_scheme" -> 4;
            case "funding", "tech_release", "skills_gap", "supply_disruption" -> 3;
            case "price_move", "consumer_trend", "infrastructure", "data_release" -> 3;
            default -> 2;
        };
        signal.put("magnitude", magnitude);
        signal.put("direction", "neutral");
        signal.put("time_horizon_days", event.equals("regulation") || event.equals("govt_scheme") ? 45 : 90);
        signal.put("who_is_affected", new ArrayList<String>());
        signal.put("new_capability", null);
        signal.put("new_constraint", null);
        signal.put("evidence_quality", "other".equals(event) ? 0.4 : 0.55);
        return signal;
    }
}
