package com.gyanwire.actionplan.compose;

import com.gyanwire.actionplan.KnowledgeCatalog;
import com.gyanwire.actionplan.format.FormatRegistry;
import com.gyanwire.actionplan.news.NewsBrief;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StepContractTest {

    @Test
    void everyStepHasMandatoryFields() {
        StepComposer composer = new StepComposer(new FormatRegistry(), new KnowledgeCatalog());
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
        }
    }

    private static boolean blank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }
}
