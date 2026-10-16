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
                "steps", List.of(Map.of(
                        "id", "s4",
                        "order", 4,
                        "title", "Record voice-over",
                        "durationMin", 30,
                        "paidDurationMin", 15,
                        "input", "script_final.md",
                        "output", "voiceover.wav",
                        "doneWhen", "Clean take",
                        "ifStuck", "Use phone recorder")));
        Map<String, Object> detail = service.build(plan, "s4", "open_source");
        assertEquals("s4", detail.get("stepKey"));
        assertTrue(detail.get("openSource") instanceof Map<?, ?>);
        assertTrue(detail.get("paid") instanceof Map<?, ?>);
        @SuppressWarnings("unchecked")
        Map<String, Object> gain = (Map<String, Object>) detail.get("gain");
        assertEquals("estimate", gain.get("basis"));
        assertFalse(((List<?>) ((Map<?, ?>) detail.get("openSource")).get("microSteps")).isEmpty());
    }
}
