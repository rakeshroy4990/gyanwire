package com.gyanwire.actionplan.research;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VideoResearchServiceTest {

    @Test
    void verifyKeepsFactOnlyWhenSourceSpanIsInPageText() {
        Map<String, VideoResearchService.Page> pages = Map.of(
                VideoResearchService.normalizeUrl("https://a.example/1"),
                new VideoResearchService.Page(
                        "https://a.example/1",
                        "Report",
                        "Anthropic asked US lawmakers to regulate frontier AI models this week.")
        );
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("whatHappened", "Anthropic proposed AI rules.");
        draft.put("whyItMatters", "Rules would change how labs ship.");
        draft.put("context", "");
        draft.put("unconfirmed", List.of());
        draft.put("facts", List.of(
                Map.of(
                        "id", "vr1",
                        "text", "Anthropic asked lawmakers to regulate frontier models.",
                        "sourceUrl", "https://a.example/1",
                        "sourceTitle", "Report",
                        "sourceSpan", "Anthropic asked US lawmakers to regulate frontier AI models this week."
                ),
                Map.of(
                        "id", "vr2",
                        "text", "Made up claim about 900% growth.",
                        "sourceUrl", "https://a.example/1",
                        "sourceTitle", "Report",
                        "sourceSpan", "Made up claim about 900% growth."
                )
        ));
        draft.put("reactions", List.of());

        Map<String, Object> verified = VideoResearchService.verify(draft, pages);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> facts = (List<Map<String, Object>>) verified.get("facts");
        assertThat(facts).hasSize(1);
        assertThat(facts.get(0).get("id")).isEqualTo("vr1");
    }

    @Test
    void verifyDropsFactWhenSourceUrlWasNotScraped() {
        Map<String, VideoResearchService.Page> pages = Map.of(
                VideoResearchService.normalizeUrl("https://a.example/1"),
                new VideoResearchService.Page("https://a.example/1", "Report", "Real sentence about Anthropic.")
        );
        Map<String, Object> draft = Map.of(
                "whatHappened", "x",
                "whyItMatters", "y",
                "context", "",
                "unconfirmed", List.of(),
                "facts", List.of(Map.of(
                        "id", "vr1",
                        "text", "Claim from elsewhere",
                        "sourceUrl", "https://other.example/2",
                        "sourceTitle", "Other",
                        "sourceSpan", "Real sentence about Anthropic."
                )),
                "reactions", List.of()
        );

        Map<String, Object> verified = VideoResearchService.verify(draft, pages);
        assertThat((List<?>) verified.get("facts")).isEmpty();
    }

    @Test
    void buildQueryUsesHeadlineAndEntities() {
        String query = VideoResearchService.buildQuery(
                "Anthropic proposes law regulating AI",
                List.of("Anthropic", "US lawmakers", "Claude"));
        assertThat(query).contains("Anthropic proposes law regulating AI");
        assertThat(query).contains("US lawmakers");
    }
}
