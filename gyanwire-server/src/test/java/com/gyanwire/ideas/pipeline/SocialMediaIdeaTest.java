package com.gyanwire.ideas.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialMediaIdeaTest {

    @Test
    void nonFyiNewsAlwaysIncludesSocialMedia() {
        var weights = IdeaScoreWeights.load();
        var calculator = new IdeaScoreCalculator(weights);
        var ensured = SocialMediaIdea.ensure(
                List.of(),
                calculator,
                "HPCL launches a prepaid fuel card for truck fleets",
                "The card is live for fleet owners.",
                null);
        assertEquals(1, ensured.size());
        assertEquals("comparison_content", ensured.get(0).idea().angleType());
        assertTrue(SocialMediaIdea.present(ensured));

        var again = SocialMediaIdea.ensure(ensured, calculator, "HPCL launches a prepaid fuel card", "", null);
        assertEquals(1, again.size());
    }

    @Test
    void asRowMarksSocialForTheIdeaCard() {
        var row = SocialMediaIdea.asRow(
                "Broker cuts fees on equity delivery",
                "Retail brokers cut delivery fees.",
                "Share Market");
        assertEquals("comparison_content", row.get("angleType"));
        assertEquals("Social media", row.get("format"));
        assertTrue(String.valueOf(row.get("disclaimer")).contains("investment"));
        assertTrue(SocialMediaIdea.presentRows(List.of(row)));
    }

    @Test
    void criticKeepsTheSocialFallback() {
        var idea = SocialMediaIdea.fromHeadline(
                "Clinic labs must upload calibration logs by 1 November",
                "The notice names the date.");
        var reviewed = IdeaCriticRules.review(List.of(idea), null);
        assertTrue(reviewed.removed().isEmpty());
        assertEquals("comparison_content", reviewed.kept().get(0).angleType());
        assertFalse(idea.oneLiner().isBlank());
    }
}
