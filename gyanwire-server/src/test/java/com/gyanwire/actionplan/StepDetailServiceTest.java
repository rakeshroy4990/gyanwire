package com.gyanwire.actionplan;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepDetailServiceTest {

    @Test
    void buildsOpenSourceAndPaidTabs() {
        StepDetailService service = new StepDetailService(new KnowledgeCatalog());
        Map<String, Object> plan = Map.of(
                "format", "short_vertical",
                "slots", Map.of("publisherUrl", "https://news.example/a"),
                "newsBrief", Map.of(
                        "publisherUrl", "https://news.example/a",
                        "headline", "Example headline"),
                "steps", List.of(Map.of(
                        "id", "s1",
                        "order", 1,
                        "title", "Pick 3 facts",
                        "durationMin", 15,
                        "paidDurationMin", 10,
                        "input", "NewsBrief",
                        "output", "facts.md",
                        "doneWhen", "3 facts",
                        "ifStuck", "Copy three sentences")));
        Map<String, Object> detail = service.build(plan, "s1", "open_source");
        assertEquals("s1", detail.get("stepKey"));
        assertTrue(detail.get("openSource") instanceof Map<?, ?>);
        assertTrue(detail.get("paid") instanceof Map<?, ?>);
        @SuppressWarnings("unchecked")
        Map<String, Object> gain = (Map<String, Object>) detail.get("gain");
        assertEquals("estimate", gain.get("basis"));
        @SuppressWarnings("unchecked")
        List<String> micro = (List<String>) ((Map<?, ?>) detail.get("openSource")).get("microSteps");
        assertFalse(micro.isEmpty());
        assertTrue(micro.stream().anyMatch(line -> line.contains("https://news.example/a")));
        assertTrue(micro.stream().anyMatch(line -> line.toLowerCase().contains("story summary")));
        assertTrue(micro.stream().anyMatch(line -> line.contains("facts.md")));
    }

    @Test
    void youtubeLongOutlineStepUsesResearchSummary() {
        StepDetailService service = new StepDetailService(new KnowledgeCatalog());
        Map<String, Object> plan = Map.of(
                "format", "youtube_long",
                "slots", Map.of(
                        "publisherUrl", "https://news.example/a",
                        "headline", "Example headline",
                        "entity", "Karnataka Medical Council"),
                "videoResearch", Map.of(
                        "whatHappened", "Health Department referred a medical negligence case to the council.",
                        "whyItMatters", "Parents need a clear path when a toddler is harmed in care.",
                        "facts", List.of(
                                Map.of("id", "r1", "text", "Toddler suffered brain damage after treatment."),
                                Map.of("id", "r2", "text", "Case sent to Karnataka Medical Council."),
                                Map.of("id", "r3", "text", "Hospital faces a negligence referral."))),
                "steps", List.of(Map.of(
                        "id", "s1",
                        "order", 1,
                        "title", "Outline the demo chapters",
                        "durationMin", 30,
                        "paidDurationMin", 15,
                        "input", "NewsBrief + video research sources + product",
                        "output", "outline.md",
                        "doneWhen", "5 chapter titles",
                        "ifStuck", "Use: hook / why / demo / tips / CTA.")));
        Map<String, Object> detail = service.build(plan, "s1", "open_source");
        @SuppressWarnings("unchecked")
        List<String> micro = (List<String>) ((Map<?, ?>) detail.get("openSource")).get("microSteps");
        assertFalse(micro.isEmpty());
        assertTrue(micro.stream().anyMatch(line -> line.contains("medical negligence case")));
        assertTrue(micro.stream().anyMatch(line -> line.contains("Parents need a clear path")));
        assertTrue(micro.stream().anyMatch(line -> line.contains("Karnataka Medical Council")));
        assertTrue(micro.stream().anyMatch(line -> line.contains("outline.md")));
        assertTrue(micro.stream().noneMatch(line -> line.startsWith("{") && line.contains("=")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) ((Map<?, ?>) detail.get("openSource")).get("tools");
        assertFalse(tools.isEmpty());
        assertTrue(tools.stream().anyMatch(t -> "notes_app".equals(t.get("id"))));
        @SuppressWarnings("unchecked")
        List<String> paidMicro = (List<String>) ((Map<?, ?>) detail.get("paid")).get("microSteps");
        assertFalse(paidMicro.isEmpty());
        assertTrue(paidMicro.stream().anyMatch(line -> line.contains("medical negligence case")));
    }

    @Test
    void shortVerticalCaptionStepLinksWhisperCpp() {
        StepDetailService service = new StepDetailService(new KnowledgeCatalog());
        Map<String, Object> plan = Map.of(
                "format", "short_vertical",
                "steps", List.of(Map.of(
                        "id", "s6",
                        "order", 6,
                        "title", "Assemble and cut",
                        "durationMin", 40,
                        "paidDurationMin", 25)));
        Map<String, Object> detail = service.build(plan, "s6", "open_source");
        @SuppressWarnings("unchecked")
        Map<String, Object> openSource = (Map<String, Object>) detail.get("openSource");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) openSource.get("tools");
        Map<String, Object> whisper = tools.stream()
                .filter(tool -> "whisper_cpp".equals(tool.get("id")))
                .findFirst()
                .orElseThrow();
        assertEquals("Whisper.cpp", whisper.get("name"));
        assertEquals("open_source", whisper.get("tier"));
        assertEquals("MIT", whisper.get("license"));
        assertEquals(true, whisper.get("openSource"));
        assertTrue(String.valueOf(whisper.get("url")).contains("whisper.cpp"));
    }
}
