package com.gyanwire.actionplan.news;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NewsBriefFromResearchTest {

    private final NewsBriefService service = new NewsBriefService(null, null, null, null);

    @Test
    void fromVideoResearchBuildsUsableBriefAcrossSources() {
        Map<String, Object> research = Map.of(
                "whatHappened", "Regulators published a draft AI disclosure rule for labs.",
                "whyItMatters", "Product teams need a checklist before shipping model demos.",
                "facts", List.of(
                        Map.of("id", "vr1", "text", "The draft requires public model cards for frontier systems."),
                        Map.of("id", "vr2", "text", "Comments close in thirty days according to the notice."),
                        Map.of("id", "vr3", "text", "Two Senate offices asked labs for evaluation summaries.")
                )
        );

        NewsBrief brief = service.fromVideoResearch(
                "Senate draft on AI disclosure",
                "https://example.com/blocked",
                "example.com",
                research);

        assertThat(brief.usable()).isTrue();
        assertThat(brief.facts()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(brief.facts().get(0).text()).contains("model cards");
    }

    @Test
    void fromMapRestoresCachedIdeaBrief() {
        NewsBrief original = service.localBrief(
                "Anthropic launches Claude Haiku",
                "https://example.com/haiku",
                "example.com",
                "Anthropic launches Claude Haiku. The model answers faster than Sonnet. "
                        + "Builders can try it in the API console today. Short tasks finish quicker.",
                "AI");

        NewsBrief restored = NewsBrief.fromMap(original.toMap());

        assertThat(restored.usable()).isTrue();
        assertThat(restored.headline()).isEqualTo(original.headline());
        assertThat(restored.facts()).hasSize(original.facts().size());
    }
}
