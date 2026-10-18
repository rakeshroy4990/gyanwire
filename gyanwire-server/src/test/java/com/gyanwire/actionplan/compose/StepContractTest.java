package com.gyanwire.actionplan.compose;

import com.gyanwire.actionplan.KnowledgeCatalog;
import com.gyanwire.actionplan.StepDetailService;
import com.gyanwire.actionplan.format.FormatRegistry;
import com.gyanwire.actionplan.news.NewsBrief;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepContractTest {

    @Test
    void everyStepHasMandatoryFields() {
        KnowledgeCatalog catalog = new KnowledgeCatalog();
        StepComposer composer = new StepComposer(new FormatRegistry(), catalog, new StepDetailService(catalog));
        NewsBrief brief = new NewsBrief(
                "n1",
                "Anthropic launches Claude Haiku 5.5",
                "https://example.com",
                "example.com",
                null,
                List.of("Anthropic", "Claude Haiku 5.5"),
                List.of(
                        new NewsBrief.Fact("f1", "Anthropic launched Claude Haiku 5.5", "Anthropic launched Claude Haiku 5.5", "high"),
                        new NewsBrief.Fact("f2", "It is fast and free", "It is fast and free", "high"),
                        new NewsBrief.Fact("f3", "Students benefit", "Students benefit", "medium")),
                List.of("developers"),
                null);
        Map<String, Object> plan = composer.compose(
                brief,
                Map.of("id", "a1", "oneLine", "Explain the launch", "forWhom", "developers", "whyNow", "fresh", "test", "100 views in 48 h"),
                null,
                240,
                null,
                false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) plan.get("steps");
        for (Map<String, Object> step : steps) {
            assertFalse(blank(step.get("id")));
            assertFalse(blank(step.get("title")));
            assertFalse(blank(step.get("action")));
            assertFalse(blank(step.get("input")));
            assertFalse(blank(step.get("output")));
            assertFalse(blank(step.get("doneWhen")));
            assertNotNull(step.get("tool"));
            assertFalse(blank(step.get("ifStuck")));
            assertNotNull(step.get("startMin"));
            assertNotNull(step.get("durationMin"));
            @SuppressWarnings("unchecked")
            List<String> micro = (List<String>) step.get("microSteps");
            assertNotNull(micro);
            assertFalse(micro.isEmpty(), "step " + step.get("id") + " needs micro-steps");
        }
        @SuppressWarnings("unchecked")
        List<String> s1 = (List<String>) steps.get(0).get("microSteps");
        assertTrue(s1.stream().anyMatch(line -> line.contains("https://example.com")));
        assertTrue(s1.stream().anyMatch(line -> line.toLowerCase().contains("story summary")));
        assertTrue(s1.stream().anyMatch(line -> line.contains("facts.md")));
        assertTrue(s1.stream().anyMatch(line -> line.toLowerCase().contains("hook fact")));
    }

    @Test
    void youtubeLongStepsHaveMicroSteps() {
        KnowledgeCatalog catalog = new KnowledgeCatalog();
        FormatRegistry formats = new FormatRegistry();
        StepComposer composer = new StepComposer(formats, catalog, new StepDetailService(catalog));
        NewsBrief brief = new NewsBrief(
                "n1",
                "Anthropic launches Claude Haiku 5.5",
                "https://example.com",
                "example.com",
                null,
                List.of("Anthropic", "Claude Haiku 5.5"),
                List.of(
                        new NewsBrief.Fact("f1", "Anthropic launched Claude Haiku 5.5", "Anthropic launched Claude Haiku 5.5", "high"),
                        new NewsBrief.Fact("f2", "It is fast and free", "It is fast and free", "high"),
                        new NewsBrief.Fact("f3", "Students benefit", "Students benefit", "medium")),
                List.of("developers"),
                null);
        Map<String, Object> plan = composer.compose(
                brief,
                Map.of("id", "a1", "oneLine", "Explain the launch", "forWhom", "developers", "whyNow", "fresh", "test", "100 views in 48 h"),
                formats.require("youtube_long"),
                480,
                null,
                true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) plan.get("steps");
        for (Map<String, Object> step : steps) {
            @SuppressWarnings("unchecked")
            List<String> micro = (List<String>) step.get("microSteps");
            assertNotNull(micro);
            assertFalse(micro.isEmpty(), "step " + step.get("id") + " needs micro-steps");
            @SuppressWarnings("unchecked")
            List<String> paid = (List<String>) step.get("paidMicroSteps");
            assertNotNull(paid);
            assertFalse(paid.isEmpty(), "step " + step.get("id") + " needs paid micro-steps");
        }
        @SuppressWarnings("unchecked")
        List<String> s1 = (List<String>) steps.stream()
                .filter(step -> "s1".equals(step.get("id")))
                .findFirst()
                .orElseThrow()
                .get("microSteps");
        assertTrue(s1.stream().anyMatch(line -> line.toLowerCase().contains("story summary")));
        assertTrue(s1.stream().anyMatch(line -> line.contains("Anthropic launched") || line.contains("Claude Haiku")));
        assertTrue(s1.stream().anyMatch(line -> line.contains("outline.md")));
        assertTrue(s1.stream().anyMatch(line -> line.toLowerCase().contains("thumbnail")));
        assertTrue(s1.stream().anyMatch(line -> line.contains("Title") && line.contains("#")));
        assertTrue(s1.stream().anyMatch(line -> line.contains("WHAT NOT TO DO") || line.contains("What not to do")));
        assertTrue(s1.stream().noneMatch(line -> line.startsWith("{") && line.contains("=")));
        assertTrue(s1.stream().noneMatch(line -> line.contains("{{")));
        assertEquals(6, steps.size());
        assertTrue(steps.stream().noneMatch(step -> "Thumbnail and title".equals(step.get("title"))));
        assertTrue(steps.stream().anyMatch(step -> String.valueOf(step.get("title")).toLowerCase().contains("thumbnail")));
        @SuppressWarnings("unchecked")
        List<String> s2 = (List<String>) steps.stream()
                .filter(step -> "s2".equals(step.get("id")))
                .findFirst()
                .orElseThrow()
                .get("microSteps");
        assertTrue(s2.stream().anyMatch(line -> line.contains("[0:00–1:00]")));
        assertTrue(s2.stream().anyMatch(line -> line.contains("[8:00–10:00]")));
    }

    private static boolean blank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }
}
