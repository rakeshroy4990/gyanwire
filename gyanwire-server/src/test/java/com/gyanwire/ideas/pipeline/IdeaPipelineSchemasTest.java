package com.gyanwire.ideas.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.llm.ModelTier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdeaPipelineSchemasTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void buyersDoNotUnlockB2b() {
        List<String> allowed = AngleType.allowed(List.of("Fitness-curious buyers in Indian metros"));
        assertFalse(allowed.contains("b2b_service"));
        assertTrue(AngleType.allowed(List.of("diagnostic lab owners")).contains("b2b_service"));
    }

    @Test
    void prioritizePutsSocialThenEducationFirst() {
        List<String> allowed = AngleType.allowed(List.of("Fitness-curious buyers in Indian metros"));
        List<String> ordered = AngleType.prioritize(
                List.of("b2b_service", "data_or_tool", "accessories_or_addons"),
                allowed);
        assertEquals("comparison_content", ordered.get(0));
        assertEquals("education_or_training", ordered.get(1));
        assertTrue(ordered.contains("data_or_tool"));
        assertFalse(ordered.contains("b2b_service"));
        assertEquals(0, AngleType.displayPriority("comparison_content"));
        assertEquals(1, AngleType.displayPriority("education_or_training"));
        assertEquals("Social media", AngleType.labelOf("comparison_content"));
    }

    @Test
    void criticDoesNotDropPreferredAngles() {
        var rules = new IdeaModels.CriticResult(
                List.of(
                        idea("comparison_content", "Compare Fitbit Edge with Charge 6"),
                        idea("data_or_tool", "A tracker database for shops")),
                List.of());
        var node = mapper.createObjectNode();
        var removed = node.putArray("removed");
        removed.addObject().put("angleType", "comparison_content").put("reason", "content is common");
        removed.addObject().put("angleType", "data_or_tool").put("reason", "too thin");
        var merged = CriticStage.merge(rules, node);
        assertEquals(1, merged.kept().size());
        assertEquals("comparison_content", merged.kept().get(0).angleType());
        assertTrue(merged.removed().stream().anyMatch(r -> "data_or_tool".equals(r.angleType())));
    }

    private static IdeaModels.BusinessIdea idea(String angle, String title) {
        return new IdeaModels.BusinessIdea(
                angle,
                title,
                "A one liner with enough words for the schema.",
                new IdeaModels.Customer("Fitness buyers in metros", "YouTube"),
                "Shoppers compare trackers before an India listing exists this month.",
                "They want to avoid the wrong buy this week.",
                "The rumour window is still open for India shoppers.",
                "Publish a short video that compares the expected specs with Charge 6. Update when Google confirms.",
                new IdeaModels.RevenueModel("ads", "", "Views can carry display ads on the video."),
                List.of("Draft the script", "Film one short", "Post and count views"),
                new IdeaModels.ValidationTest("Post the video", "500 views in 7 days"),
                List.of("Specs may change"),
                "days",
                false);
    }

    @Test
    void gateFillsSuggestionsWhenTheModelOmitsThem() throws Exception {
        var gate = IdeaPipelineSchemas.parseGate(mapper.readTree("""
                {"proceed":false,"reason":"Nothing here creates a buyer.","confidence":0.2}
                """));
        assertNotNull(gate);
        assertFalse(gate.proceed());
        assertEquals(2, gate.suggestions().size());
    }

    @Test
    void rejectsPlaceholderPriceAndGenericCustomer() throws Exception {
        String json = """
                {"ideas":[{"angleType":"comparison_content","title":"A vague offer for readers",
                "oneLiner":"A short line that still has enough words here.",
                "customer":{"who":"People in India","where":"Online"},
                "trigger":"because of this news","whyTheyPay":"They might pay a low monthly fee someday soon.",
                "whyNow":"Because of this news people are talking about it.","product":"Send emails. That is it.",
                "revenueModel":{"type":"subscription","price":"₹0 to start","rationale":"low monthly fee"},
                "firstWeek":["Write something","Send it out","Hope it works"],
                "validationTest":{"action":"Send","successMetric":"Some"},
                "risks":[],"buildEffort":"days","dependsOnUnconfirmed":false}]}
                """;
        var parsed = IdeaPipelineSchemas.parseIdeas(
                mapper.readTree(json),
                null,
                java.util.Set.of("comparison_content"));
        assertTrue(parsed.valid().isEmpty());
        assertFalse(parsed.rejected().isEmpty());
    }

    @Test
    void retriesUntilTheSignalIsValid() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var good = mapper.readTree("""
                {"headline":"Labs must file logs","eventType":"regulation","entities":["labs"],
                "confirmedFacts":["Deadline is 1 November"],"unconfirmedClaims":[],
                "indiaRelevance":"high","timeWindow":{"urgency":"days"},"affectedGroups":["diagnostic lab owners"]}
                """);
        IdeaStageClient client = (userId, version, feature, system, user, tier) -> {
            if (calls.incrementAndGet() == 1) {
                return mapper.createObjectNode().put("eventType", "nope");
            }
            return good;
        };
        var node = StageCalls.completeValidated(
                client, UUID.randomUUID(), "idea-signals.v1", "idea-signals", "system", "user",
                ModelTier.LIGHT, IdeaPipelineSchemas::validateSignals, 2);
        assertEquals(2, calls.get());
        assertNotNull(IdeaPipelineSchemas.parseSignals(node));
    }

    @Test
    void stopsAfterTheRetryBudget() {
        IdeaStageClient client = (userId, version, feature, system, user, tier) -> null;
        var node = StageCalls.completeValidated(
                client, UUID.randomUUID(), "idea-build.v1", "idea-build", "system", "user",
                ModelTier.MAIN, json -> "ideas required", 3);
        assertNull(node);
    }

    @Test
    void launchPlanHasSevenDays() throws Exception {
        var fixture = mapper.readTree(getClass().getResourceAsStream("/ideas/fixtures/clinic-calibration.json"));
        var parsed = IdeaPipelineSchemas.parseIdeas(
                fixture.get("ideas"),
                IdeaPipelineSchemas.parseSignals(fixture.get("signals")),
                java.util.Set.copyOf(AngleType.allowed(List.of("diagnostic lab owners"))));
        var plan = LaunchPlanStage.fromIdea(parsed.valid().get(0), "Labs told to file calibration logs");
        assertEquals(7, ((List<?>) plan.get("schedule")).size());
        assertEquals(3, ((List<?>) plan.get("outreach")).size());
        assertNotNull(plan.get("validationMetric"));
    }
}
