package com.gyanwire.ideas.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.gyanwire.ideas.pipeline.IdeaModels.BusinessIdea;
import static com.gyanwire.ideas.pipeline.IdeaModels.CriticResult;
import static com.gyanwire.ideas.pipeline.IdeaModels.NewsSignals;
import static com.gyanwire.ideas.pipeline.IdeaModels.OpportunityGate;
import static com.gyanwire.ideas.pipeline.IdeaModels.ParsedIdeas;
import static com.gyanwire.ideas.pipeline.IdeaModels.ScoredIdea;

/**
 * Replays fixture model output through the same checks the live pipeline uses.
 * Run with {@code ./gradlew evalIdeaPipeline}.
 */
public final class IdeaPipelineEval {

    private static final List<String> FIXTURES = List.of(
            "fitbit-edge.json",
            "celebrity-sighting.json",
            "clinic-calibration.json");

    private IdeaPipelineEval() {
    }

    public static void main(String[] args) {
        List<Row> rows = run();
        print(rows);
        if (rows.stream().anyMatch(row -> !row.passed())) {
            System.exit(1);
        }
    }

    public static List<Row> run() {
        ObjectMapper mapper = new ObjectMapper();
        IdeaScoreCalculator scorer = new IdeaScoreCalculator(IdeaScoreWeights.load());
        List<Row> rows = new ArrayList<>();
        for (String name : FIXTURES) {
            rows.add(evaluate(mapper, scorer, name));
        }
        return rows;
    }

    public static void print(List<Row> rows) {
        System.out.println("fixture                 result  notes");
        for (Row row : rows) {
            System.out.printf("%-23s %-7s %s%n", row.id(), row.passed() ? "PASS" : "FAIL", String.join("; ", row.notes()));
        }
    }

    private static Row evaluate(ObjectMapper mapper, IdeaScoreCalculator scorer, String name) {
        List<String> notes = new ArrayList<>();
        try (InputStream in = IdeaPipelineEval.class.getResourceAsStream("/ideas/fixtures/" + name)) {
            if (in == null) {
                return new Row(name, false, List.of("missing fixture"));
            }
            JsonNode fixture = mapper.readTree(in);
            String id = fixture.path("id").asText(name);
            JsonNode expect = fixture.path("expect");
            NewsSignals signals = IdeaPipelineSchemas.parseSignals(fixture.get("signals"));
            if (signals == null) {
                notes.add("signals invalid: " + IdeaPipelineSchemas.validateSignals(fixture.get("signals")));
                return new Row(id, false, notes);
            }
            if (expect.has("eventType") && !expect.path("eventType").asText().equals(signals.eventType())) {
                notes.add("eventType");
            }
            if (expect.path("unconfirmedPresent").asBoolean(false) && signals.unconfirmedClaims().isEmpty()) {
                notes.add("unconfirmed claims empty");
            }
            if (expect.path("confirmedAreNotRumours").asBoolean(false)) {
                for (String fact : signals.confirmedFacts()) {
                    String lower = fact.toLowerCase(Locale.ROOT);
                    if (lower.contains("expected") || lower.contains("rumour") || lower.contains("rumor") || lower.contains("likely")) {
                        notes.add("confirmed fact looks unconfirmed: " + fact);
                    }
                }
            }
            OpportunityGate gate = IdeaPipelineSchemas.parseGate(fixture.get("gate"));
            if (gate == null) {
                notes.add("gate invalid: " + IdeaPipelineSchemas.validateGate(fixture.get("gate")));
                return new Row(id, false, notes);
            }
            if (gate.proceed() != expect.path("proceed").asBoolean()) {
                notes.add("proceed");
            }
            if (!gate.proceed()) {
                if (expect.path("suggestions").asBoolean(false) && (gate.suggestions().size() < 1 || gate.suggestions().size() > 2)) {
                    notes.add("suggestions");
                }
                if (gate.reason().isBlank()) {
                    notes.add("reason");
                }
                return new Row(id, notes.isEmpty(), notes.isEmpty() ? List.of("no strong opportunity") : notes);
            }
            Set<String> allowed = Set.copyOf(AngleType.allowed(signals.affectedGroups()));
            ParsedIdeas parsed = IdeaPipelineSchemas.parseIdeas(fixture.get("ideas"), signals, allowed);
            if (expect.path("placeholderRejected").asBoolean(false) && parsed.rejected().isEmpty()) {
                notes.add("placeholder was not rejected");
            }
            if (expect.path("noB2b").asBoolean(false)
                    && parsed.valid().stream().anyMatch(idea -> "b2b_service".equals(idea.angleType()))) {
                notes.add("b2b kept without a business group");
            }
            CriticResult critic = IdeaCriticRules.review(parsed.valid(), signals);
            if (expect.path("duplicateRemoved").asBoolean(false)
                    && critic.removed().stream().noneMatch(removal -> removal.reason().toLowerCase(Locale.ROOT).contains("format")
                    || removal.reason().toLowerCase(Locale.ROOT).contains("duplicate"))) {
                notes.add("duplicate format was kept");
            }
            List<BusinessIdea> kept = critic.kept();
            long distinct = kept.stream().map(BusinessIdea::angleType).distinct().count();
            if (expect.path("distinctAngles").asBoolean(false) && distinct != kept.size()) {
                notes.add("angles not distinct");
            }
            int min = expect.path("minIdeas").asInt(1);
            int max = expect.path("maxIdeas").asInt(5);
            if (kept.size() < min || kept.size() > max) {
                notes.add("idea count " + kept.size());
            }
            if (expect.path("b2bPresent").asBoolean(false)
                    && kept.stream().noneMatch(idea -> "b2b_service".equals(idea.angleType()))) {
                notes.add("b2b missing");
            }
            if (expect.has("unconfirmedTagged")) {
                boolean any = kept.stream().anyMatch(BusinessIdea::dependsOnUnconfirmed);
                if (expect.path("unconfirmedTagged").asBoolean() != any) {
                    notes.add(any ? "unconfirmed tagged unexpectedly" : "unconfirmed facts were not tagged");
                }
            }
            if (expect.path("accessoriesStayConfirmed").asBoolean(false)) {
                kept.stream().filter(idea -> "accessories_or_addons".equals(idea.angleType())).findFirst()
                        .ifPresent(idea -> {
                            if (idea.dependsOnUnconfirmed()) {
                                notes.add("accessories tagged unconfirmed");
                            }
                        });
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> profile = mapper.convertValue(fixture.path("profile"), Map.class);
            List<ScoredIdea> scored = scorer.score(kept, signals, profile);
            if (expect.path("reasonsPresent").asBoolean(false)) {
                for (ScoredIdea item : scored) {
                    if (item.score().dimensions().stream().anyMatch(dimension -> dimension.reason() == null || dimension.reason().isBlank())) {
                        notes.add("score missing a reason");
                    }
                    if (item.score().dimensions().stream().noneMatch(dimension -> "demandEvidence".equals(dimension.key()) && dimension.assumed())) {
                        notes.add("demand not marked assumed");
                    }
                }
            }
            if (expect.path("scoresDiffer").asBoolean(false) && scored.size() >= 2) {
                int high = scored.stream().mapToInt(item -> item.score().stored()).max().orElse(0);
                int low = scored.stream().mapToInt(item -> item.score().stored()).min().orElse(0);
                if (high - low < 8) {
                    notes.add("scores too close (" + low + "-" + high + ")");
                }
            }
            return new Row(id, notes.isEmpty(), notes.isEmpty() ? List.of(kept.size() + " ideas") : notes);
        } catch (Exception ex) {
            return new Row(name, false, List.of(ex.getMessage() == null ? ex.toString() : ex.getMessage()));
        }
    }

    public record Row(String id, boolean passed, List<String> notes) {
    }
}
