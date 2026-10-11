package com.gyanwire.ideas.pipeline;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.gyanwire.ideas.pipeline.IdeaModels.BusinessIdea;
import static com.gyanwire.ideas.pipeline.IdeaModels.Customer;
import static com.gyanwire.ideas.pipeline.IdeaModels.NewsSignals;
import static com.gyanwire.ideas.pipeline.IdeaModels.OpportunityGate;
import static com.gyanwire.ideas.pipeline.IdeaModels.ParsedIdeas;
import static com.gyanwire.ideas.pipeline.IdeaModels.Removal;
import static com.gyanwire.ideas.pipeline.IdeaModels.RevenueModel;
import static com.gyanwire.ideas.pipeline.IdeaModels.TimeWindow;
import static com.gyanwire.ideas.pipeline.IdeaModels.ValidationTest;

/**
 * Jackson validation for each stage. This is the project's stand-in for Zod:
 * a failed check returns a short error the retry loop sends back to the model.
 */
public final class IdeaPipelineSchemas {

    public static final Set<String> EVENT_TYPES = Set.of(
            "launch", "price_change", "regulation", "funding", "outage", "trend", "other");
    public static final Set<String> INDIA = Set.of("high", "medium", "low");
    public static final Set<String> URGENCY = Set.of("hours", "days", "weeks", "evergreen");
    public static final Set<String> EFFORT = Set.of("hours", "days", "weeks");
    public static final Set<String> REVENUE = Set.of(
            "affiliate", "one_time", "subscription", "service", "ads", "lead_gen");

    static final List<String> DEFAULT_SUGGESTIONS = List.of(
            "A launch, price change, or rule that names who in India has to act this month.",
            "News that creates a dated buying moment, a shortage, or a new compliance task.");

    private IdeaPipelineSchemas() {
    }

    public static String validateSignals(JsonNode node) {
        if (node == null || !node.isObject()) {
            return "missing json";
        }
        if (text(node, "headline").length() < 8) {
            return "headline";
        }
        if (!EVENT_TYPES.contains(text(node, "eventType"))) {
            return "eventType";
        }
        if (strings(node, "entities").isEmpty()) {
            return "entities";
        }
        List<String> confirmed = strings(node, "confirmedFacts");
        List<String> unconfirmed = strings(node, "unconfirmedClaims");
        if (confirmed.isEmpty() && unconfirmed.isEmpty()) {
            return "facts";
        }
        if (!INDIA.contains(text(node, "indiaRelevance"))) {
            return "indiaRelevance";
        }
        JsonNode window = node.get("timeWindow");
        if (window == null || !window.isObject() || !URGENCY.contains(text(window, "urgency"))) {
            return "timeWindow";
        }
        if (strings(node, "affectedGroups").isEmpty()) {
            return "affectedGroups";
        }
        return null;
    }

    public static NewsSignals parseSignals(JsonNode node) {
        if (validateSignals(node) != null) {
            return null;
        }
        JsonNode window = node.get("timeWindow");
        String starts = text(window, "startsAt");
        return new NewsSignals(
                text(node, "headline"),
                text(node, "eventType"),
                strings(node, "entities"),
                strings(node, "confirmedFacts"),
                strings(node, "unconfirmedClaims"),
                text(node, "indiaRelevance"),
                new TimeWindow(starts.isBlank() ? null : starts, text(window, "urgency")),
                strings(node, "affectedGroups"));
    }

    public static String validateGate(JsonNode node) {
        if (node == null || !node.isObject() || !node.path("proceed").isBoolean()) {
            return "proceed";
        }
        if (text(node, "reason").length() < 12) {
            return "reason";
        }
        if (!node.path("confidence").isNumber()) {
            return "confidence";
        }
        double confidence = node.path("confidence").asDouble();
        if (confidence < 0 || confidence > 1) {
            return "confidence";
        }
        return null;
    }

    public static OpportunityGate parseGate(JsonNode node) {
        if (validateGate(node) != null) {
            return null;
        }
        if (node == null || !node.isObject() || !node.path("proceed").isBoolean()) {
            return null;
        }
        boolean proceed = node.path("proceed").asBoolean();
        List<String> suggestions = new ArrayList<>(strings(node, "suggestions"));
        if (!proceed) {
            if (suggestions.isEmpty()) {
                suggestions = new ArrayList<>(DEFAULT_SUGGESTIONS);
            }
            if (suggestions.size() > 2) {
                suggestions = new ArrayList<>(suggestions.subList(0, 2));
            }
        }
        return new OpportunityGate(
                proceed,
                text(node, "reason"),
                node.path("confidence").asDouble(),
                List.copyOf(suggestions));
    }

    public static String validateAngles(JsonNode node, List<String> allowed) {
        if (node == null || !node.path("angles").isArray()) {
            return "angles";
        }
        List<String> angles = strings(node, "angles");
        if (angles.isEmpty() || angles.size() > 5) {
            return "angles";
        }
        Set<String> seen = new HashSet<>();
        Set<String> permit = Set.copyOf(allowed);
        for (String angle : angles) {
            if (!permit.contains(angle) || !seen.add(angle)) {
                return "angles";
            }
        }
        return null;
    }

    public static String validateIdea(JsonNode node, NewsSignals signals, Set<String> allowed) {
        if (node == null || !node.isObject()) {
            return "missing json";
        }
        String angle = text(node, "angleType");
        if (!allowed.contains(angle)) {
            return "angleType";
        }
        String title = text(node, "title");
        if (title.length() < 12 || TextCheck.words(title) < 3 || TextCheck.banned(title) != null) {
            return "title";
        }
        String oneLiner = text(node, "oneLiner");
        int oneLinerWords = TextCheck.words(oneLiner);
        if (oneLinerWords < 6 || oneLinerWords > 18 || TextCheck.banned(oneLiner) != null) {
            return "oneLiner";
        }
        JsonNode customer = node.get("customer");
        if (customer == null || !customer.isObject()) {
            return "customer";
        }
        String who = text(customer, "who");
        String where = text(customer, "where");
        if (TextCheck.genericWho(who) || where.length() < 3) {
            return "customer";
        }
        String trigger = text(node, "trigger");
        if (TextCheck.genericTrigger(trigger) || TextCheck.banned(trigger) != null) {
            return "trigger";
        }
        if (tooShort(node, "whyTheyPay", 24) || tooShort(node, "whyNow", 24)) {
            return tooShort(node, "whyTheyPay", 24) ? "whyTheyPay" : "whyNow";
        }
        if (TextCheck.banned(text(node, "whyTheyPay")) != null || TextCheck.banned(text(node, "whyNow")) != null) {
            return "banned phrase";
        }
        String product = text(node, "product");
        if (product.length() < 40 || countSentences(product) < 2 || TextCheck.banned(product) != null) {
            return "product";
        }
        String revenueError = revenueError(node.get("revenueModel"));
        if (revenueError != null) {
            return revenueError;
        }
        List<String> week = strings(node, "firstWeek");
        if (week.size() != 3 || week.stream().anyMatch(step -> step.length() < 12)) {
            return "firstWeek";
        }
        JsonNode test = node.get("validationTest");
        if (test == null || text(test, "action").length() < 8 || text(test, "successMetric").length() < 8) {
            return "validationTest";
        }
        List<String> risks = strings(node, "risks");
        if (risks.isEmpty() || risks.size() > 2) {
            return "risks";
        }
        if (!EFFORT.contains(text(node, "buildEffort"))) {
            return "buildEffort";
        }
        if (!node.path("dependsOnUnconfirmed").isBoolean()) {
            return "dependsOnUnconfirmed";
        }
        if (signals != null && angle.equals("b2b_service") && !AngleType.hasBusinessGroup(signals.affectedGroups())) {
            return "angleType";
        }
        return null;
    }

    public static ParsedIdeas parseIdeas(JsonNode node, NewsSignals signals, Set<String> allowed) {
        List<BusinessIdea> valid = new ArrayList<>();
        List<Removal> rejected = new ArrayList<>();
        if (node == null) {
            return new ParsedIdeas(valid, rejected);
        }
        JsonNode ideas = node.path("ideas").isArray() ? node.path("ideas") : node.isArray() ? node : null;
        if (ideas == null) {
            return new ParsedIdeas(valid, List.of(new Removal("", "", "ideas required")));
        }
        Set<String> seen = new HashSet<>();
        for (JsonNode idea : ideas) {
            String error = validateIdea(idea, signals, allowed);
            String angle = text(idea, "angleType");
            String title = text(idea, "title");
            if (error != null) {
                rejected.add(new Removal(angle, title, error));
                continue;
            }
            if (!seen.add(angle)) {
                rejected.add(new Removal(angle, title, "duplicate angleType"));
                continue;
            }
            valid.add(tagUnconfirmed(toIdea(idea), signals));
        }
        return new ParsedIdeas(List.copyOf(valid), List.copyOf(rejected));
    }

    public static BusinessIdea tagUnconfirmed(BusinessIdea idea, NewsSignals signals) {
        if (idea == null) {
            return null;
        }
        if (idea.dependsOnUnconfirmed() || touchesUnconfirmed(idea, signals)) {
            return idea.withUnconfirmed(true);
        }
        return idea;
    }

    static boolean touchesUnconfirmed(BusinessIdea idea, NewsSignals signals) {
        if (signals == null || signals.unconfirmedClaims() == null) {
            return false;
        }
        String blob = idea.blob().toLowerCase(Locale.ROOT);
        for (String claim : signals.unconfirmedClaims()) {
            if (claim == null || claim.isBlank()) {
                continue;
            }
            int hits = 0;
            for (String token : claim.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
                if (token.length() > 4 && blob.contains(token)) {
                    hits++;
                }
            }
            if (hits >= 2) {
                return true;
            }
        }
        return false;
    }

    private static BusinessIdea toIdea(JsonNode node) {
        JsonNode customer = node.get("customer");
        JsonNode revenue = node.get("revenueModel");
        JsonNode test = node.get("validationTest");
        String price = text(revenue, "price");
        return new BusinessIdea(
                text(node, "angleType"),
                text(node, "title"),
                text(node, "oneLiner"),
                new Customer(text(customer, "who"), text(customer, "where")),
                text(node, "trigger"),
                text(node, "whyTheyPay"),
                text(node, "whyNow"),
                text(node, "product"),
                new RevenueModel(text(revenue, "type"), price.isBlank() ? null : price, text(revenue, "rationale")),
                strings(node, "firstWeek"),
                new ValidationTest(text(test, "action"), text(test, "successMetric")),
                strings(node, "risks"),
                text(node, "buildEffort"),
                node.path("dependsOnUnconfirmed").asBoolean(false));
    }

    private static String revenueError(JsonNode revenue) {
        if (revenue == null || !revenue.isObject()) {
            return "revenueModel";
        }
        if (!REVENUE.contains(text(revenue, "type"))) {
            return "revenueModel";
        }
        String rationale = text(revenue, "rationale");
        if (rationale.length() < 20 || TextCheck.banned(rationale) != null || TextCheck.banned(text(revenue, "price")) != null) {
            return "revenueModel";
        }
        return null;
    }

    private static boolean tooShort(JsonNode node, String field, int min) {
        String value = text(node, field);
        return value.length() < min || TextCheck.words(value) < 4;
    }

    private static int countSentences(String text) {
        int marks = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '.' || c == '!') {
                marks++;
            }
        }
        return marks;
    }

    static String text(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText("").trim();
    }

    static List<String> strings(JsonNode node, String field) {
        if (node == null || !node.path(field).isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : node.path(field)) {
            String value = item.asText("").trim();
            if (!value.isBlank()) {
                out.add(value);
            }
        }
        return List.copyOf(out);
    }
}
