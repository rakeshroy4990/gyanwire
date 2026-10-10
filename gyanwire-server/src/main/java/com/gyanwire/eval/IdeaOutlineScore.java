package com.gyanwire.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.ideas.IdeaVariety;
import com.gyanwire.plans.OutlineDraft;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic checks for the idea and outline prompts.
 * A higher score means the JSON followed the prompt more closely.
 */
public final class IdeaOutlineScore {

    private static final List<String> IDEA_FIELDS = List.of(
            "title", "segment", "pain", "offer", "business_model", "why_now", "first_customer_path");

    private static final List<String> PROSE_FIELDS = List.of(
            "problem", "customer", "offer", "pricing", "channels", "milestones90", "risks", "skills");

    private static final List<String> RANGE_FIELDS = List.of(
            "market_size", "competition_gap", "regulatory_risk", "confidence");

    private IdeaOutlineScore() {
    }

    public static double ideas(JsonNode node) {
        JsonNode ideas = node == null ? null : node.path("ideas");
        if (ideas == null || !ideas.isArray() || ideas.isEmpty()) {
            return 0;
        }
        int size = ideas.size();
        double count = size == 8 ? 1 : 0;
        int courses = 0;
        Set<String> kinds = new HashSet<>();
        int complete = 0;
        int ranged = 0;
        ObjectMapper mapper = new ObjectMapper();
        for (JsonNode idea : ideas) {
            Map<String, Object> row = mapper.convertValue(idea, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            String kind = IdeaVariety.formatKind(row);
            if ("course".equals(kind)) {
                courses++;
            }
            if (!"other".equals(kind)) {
                kinds.add(kind);
            }
            if (filled(idea)) {
                complete++;
            }
            if (ranged(idea)) {
                ranged++;
            }
        }
        double course = courses == 1 ? 1 : 0;
        double variety = kinds.size() / 8.0;
        double fields = (double) complete / size;
        double ranges = (double) ranged / size;
        return (count + course + variety + fields + ranges) / 5.0;
    }

    public static double outline(JsonNode node, Map<String, Object> template) {
        if (node == null || !node.isObject() || template == null) {
            return 0;
        }
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> content = mapper.convertValue(node, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        double sections = OutlineDraft.hasNineSections(content) ? 1 : 0;
        double cost = sameNumber(node, "monthlyCost", template.get("monthlyCost")) ? 1 : 0;
        double even = sameNumber(node, "breakEvenCustomers", template.get("breakEvenCustomers")) ? 1 : 0;
        int proseFilled = 0;
        int rewritten = 0;
        for (String key : PROSE_FIELDS) {
            String text = node.path(key).asText("");
            if (!text.isBlank()) {
                proseFilled++;
            }
            Object original = template.get(key);
            if (original != null && !text.equals(String.valueOf(original))) {
                rewritten++;
            }
        }
        double prose = proseFilled / (double) PROSE_FIELDS.size();
        double rewrite = rewritten > 0 ? 1 : 0;
        return (sections + cost + even + prose + rewrite) / 5.0;
    }

    private static boolean filled(JsonNode idea) {
        for (String field : IDEA_FIELDS) {
            if (idea.path(field).asText("").isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static boolean ranged(JsonNode idea) {
        for (String field : RANGE_FIELDS) {
            JsonNode value = idea.get(field);
            if (value == null || !value.isNumber()) {
                return false;
            }
            double n = value.asDouble();
            if (n < 0 || n > 1) {
                return false;
            }
        }
        JsonNode capital = idea.get("capital_needed_inr");
        JsonNode hours = idea.get("hours_per_week");
        return capital != null && capital.isNumber() && capital.asInt() >= 0
                && hours != null && hours.isNumber() && hours.asInt() >= 0;
    }

    private static boolean sameNumber(JsonNode node, String key, Object expected) {
        if (!(expected instanceof Number number) || !node.path(key).isNumber()) {
            return false;
        }
        return node.path(key).asInt() == number.intValue();
    }
}
