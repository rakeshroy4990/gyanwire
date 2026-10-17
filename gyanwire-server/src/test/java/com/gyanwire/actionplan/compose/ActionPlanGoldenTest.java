package com.gyanwire.actionplan.compose;

import com.gyanwire.actionplan.KnowledgeCatalog;
import com.gyanwire.actionplan.StepDetailService;
import com.gyanwire.actionplan.format.FormatRegistry;
import com.gyanwire.actionplan.format.FormatSpec;
import com.gyanwire.actionplan.lint.PlanLinter;
import com.gyanwire.actionplan.news.NewsBrief;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionPlanGoldenTest {

    @Test
    void haikuShortVerticalIsTimeBoxedAndClean() {
        FormatRegistry formats = new FormatRegistry();
        KnowledgeCatalog catalog = new KnowledgeCatalog();
        StepComposer composer = new StepComposer(formats, catalog, new StepDetailService(catalog));
        PlanLinter linter = new PlanLinter(catalog);

        NewsBrief brief = new NewsBrief(
                "n-haiku",
                "Anthropic launches fast and free Claude Haiku 5.5",
                "https://example.com/anthropic-haiku",
                "example.com",
                "2026-10-16",
                List.of("Anthropic", "Claude Haiku 5.5"),
                List.of(
                        new NewsBrief.Fact(
                                "f1",
                                "Anthropic launched Claude Haiku 5.5",
                                "Anthropic today launched Claude Haiku 5.5, a fast and free model aimed at developers and students who need quick answers.",
                                "high"),
                        new NewsBrief.Fact(
                                "f2",
                                "Claude Haiku 5.5 is available at no cost for standard usage limits",
                                "Claude Haiku 5.5 is available at no cost for standard usage limits, according to the company announcement.",
                                "high"),
                        new NewsBrief.Fact(
                                "f3",
                                "Students are listed among early audiences",
                                "Students are also listed among the early audiences who benefit from free access while learning to build with language models.",
                                "medium")),
                List.of("developers", "students"),
                6);

        FormatSpec format = formats.require("short_vertical");
        Map<String, Object> angle = Map.of(
                "id", "a1",
                "oneLine", "Explain Claude Haiku 5.5 for developers today",
                "forWhom", "developers",
                "whyNow", "The launch is hours old",
                "test", "100 views in 48 h");

        Map<String, String> slots = StepComposer.fallbackSlots(brief);
        slots.put("hook", "Claude Haiku 5.5: Anthropic launched a fast free model for developers.");
        slots.put("point_1", "Anthropic launched Claude Haiku 5.5 as a fast free option for everyday prompts.");
        slots.put("point_2", "Claude Haiku 5.5 is available at no cost for standard usage limits today.");
        slots.put("point_3", "Students and developers get a plain read before the story goes stale this week.");
        slots.put("cta", "Follow for the next product brief. Link in comments.");

        Map<String, Object> plan = composer.compose(brief, angle, format, 240, slots, false);
        PlanLinter.LintResult lint = linter.lint(plan, brief, format);
        plan.put("lint", lint.toMap());

        assertEquals("short_vertical", plan.get("format"));
        assertEquals(240, plan.get("budgetMinutes"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) plan.get("steps");
        assertEquals(10, steps.size());
        int sum = steps.stream().mapToInt(s -> ((Number) s.get("durationMin")).intValue()).sum();
        assertEquals(240, sum);

        String script = String.valueOf(plan.get("script"));
        int words = script.trim().split("\\s+").length;
        assertTrue(words >= 90 && words <= 130, "words=" + words);
        assertTrue(String.valueOf(plan.get("hook")).contains("Haiku 5.5")
                || String.valueOf(plan.get("hook")).contains("Claude Haiku 5.5"));

        String blob = String.valueOf(plan).toLowerCase(Locale.ROOT);
        assertFalse(blob.contains("post today:"));
        assertFalse(blob.contains("6–10 minute") || blob.contains("6-10 minute"));
        assertFalse(defaultToolNames(steps).contains("thiefmd"));
        assertFalse(defaultToolNames(steps).contains("fountain"));
        assertFalse(defaultToolNames(steps).contains("jasper"));
        @SuppressWarnings("unchecked")
        Map<String, Object> caption = (Map<String, Object>) steps.stream()
                .filter(step -> "s6".equals(step.get("id")))
                .findFirst()
                .orElseThrow()
                .get("captionTool");
        assertEquals("whisper_cpp", caption.get("id"));
        assertEquals("open_source", caption.get("tier"));
        assertEquals(true, caption.get("openSource"));

        @SuppressWarnings("unchecked")
        List<String> s1Micro = (List<String>) steps.stream()
                .filter(step -> "s1".equals(step.get("id")))
                .findFirst()
                .orElseThrow()
                .get("microSteps");
        assertTrue(s1Micro.get(0).contains("https://example.com/anthropic-haiku"));
        assertTrue(s1Micro.get(0).contains("publisher article"));
        assertTrue(s1Micro.get(1).contains("name, number or date"));
        assertTrue(s1Micro.get(2).contains("facts.md"));
        assertTrue(s1Micro.get(3).toLowerCase(Locale.ROOT).contains("hook fact"));

        @SuppressWarnings("unchecked")
        Map<String, Object> money = (Map<String, Object>) plan.get("money");
        assertEquals("yt_shorts_ypp", money.get("ruleId"));
        assertTrue(String.valueOf(money.get("claim")).toLowerCase(Locale.ROOT).contains("not instant"));

        assertTrue(lint.passed(), String.valueOf(lint.errors()));
    }

    @Test
    void youtubeEditStepLinksWhisperCpp() {
        FormatRegistry formats = new FormatRegistry();
        KnowledgeCatalog catalog = new KnowledgeCatalog();
        StepComposer composer = new StepComposer(formats, catalog, new StepDetailService(catalog));
        NewsBrief brief = new NewsBrief(
                "n-yt",
                "Anthropic launches Claude Haiku 5.5",
                "https://example.com",
                "example.com",
                null,
                List.of("Anthropic"),
                List.of(new NewsBrief.Fact("f1", "Anthropic launched Claude Haiku 5.5", "Anthropic launched Claude Haiku 5.5", "high")),
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
        @SuppressWarnings("unchecked")
        Map<String, Object> caption = (Map<String, Object>) steps.stream()
                .filter(step -> "s4".equals(step.get("id")))
                .findFirst()
                .orElseThrow()
                .get("captionTool");
        assertEquals("whisper_cpp", caption.get("id"));
        assertEquals("Whisper.cpp", caption.get("name"));
    }

    private static String defaultToolNames(List<Map<String, Object>> steps) {
        StringBuilder out = new StringBuilder();
        for (Map<String, Object> step : steps) {
            Object tool = step.get("tool");
            if (tool instanceof Map<?, ?> map) {
                out.append(String.valueOf(map.get("id")).toLowerCase(Locale.ROOT)).append(' ');
                out.append(String.valueOf(map.get("name")).toLowerCase(Locale.ROOT)).append(' ');
            }
        }
        return out.toString();
    }
}
