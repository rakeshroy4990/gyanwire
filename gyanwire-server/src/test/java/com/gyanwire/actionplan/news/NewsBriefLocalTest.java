package com.gyanwire.actionplan.news;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NewsBriefLocalTest {

    private final NewsBriefService service = new NewsBriefService(null, null, null, null);

    @Test
    void localBriefBuildsUsableFactsFromTitleAndSummary() {
        String summary = "Anthropic announced Claude Haiku for product teams. "
                + "The model answers faster than Sonnet on short tasks. "
                + "Builders can try it in the API console today.";
        NewsBrief brief = service.localBrief(
                "Anthropic launches Claude Haiku",
                "https://example.com/haiku",
                "example.com",
                "Anthropic launches Claude Haiku. " + summary,
                "AI");

        assertThat(brief.usable()).isTrue();
        assertThat(brief.facts()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(brief.whoItAffects()).contains("AI");
        assertThat(brief.headline()).contains("Claude Haiku");
    }
}
