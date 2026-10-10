package com.gyanwire.ideas;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FindingIdeaPotentialTest {

    private final PatternMatcher patterns = new PatternMatcher();

    @Test
    void strongerBusinessComboOutranksGenericMarketWrap() {
        FindingIdeaPotential.Result education = FindingIdeaPotential.score(
                "SEBI investor education circular for new brokers",
                "New compliance training rules create demand for checklist tools and courses for retail investors.",
                "Share Market",
                "working",
                patterns.all()
        );
        FindingIdeaPotential.Result wrap = FindingIdeaPotential.score(
                "Sensex today Nifty closes market wrap live updates",
                "Stock market live Sensex ends higher Nifty closes points higher mid-day wrap.",
                "Share Market",
                "working",
                patterns.all()
        );

        assertThat(education.score()).isGreaterThan(wrap.score());
        assertThat(wrap.score()).isZero();
        assertThat(education.score()).isGreaterThan(35);
        assertThat(education.patternIds()).isNotEmpty();
        assertThat(education.why()).containsIgnoringCase("idea fit");
    }

    @Test
    void regulationPatternBeatsVagueOtherNews() {
        FindingIdeaPotential.Result regulation = FindingIdeaPotential.score(
                "RBI digital lending guidelines update for fintech apps",
                "Banks and NBFCs must add borrower education screens and audit trails under the new circular.",
                "Share Market",
                "working",
                patterns.all()
        );
        FindingIdeaPotential.Result vague = FindingIdeaPotential.score(
                "Markets mixed as traders wait",
                "Analysts remain divided ahead of data.",
                "Share Market",
                "working",
                patterns.all()
        );

        assertThat(regulation.score()).isGreaterThan(vague.score());
    }

    @Test
    void ideaServiceInputsLiftWhenDraftIsStrong() throws Exception {
        var method = IdeaService.class.getDeclaredMethod(
                "inputs", java.util.Map.class, java.util.Map.class, java.util.Map.class);
        method.setAccessible(true);

        java.util.Map<String, Object> weakSignal = java.util.Map.of(
                "magnitude", 2,
                "evidence_quality", 0.5,
                "event_type", "other",
                "time_horizon_days", 180,
                "industry", "Share Market"
        );
        java.util.Map<String, Object> strongSignal = java.util.Map.of(
                "magnitude", 5,
                "evidence_quality", 0.9,
                "event_type", "regulation",
                "time_horizon_days", 21,
                "industry", "Share Market",
                "new_capability", "compliance training APIs"
        );
        java.util.Map<String, Object> profile = java.util.Map.of(
                "persona", "working",
                "skills", List.of("teaching", "excel", "finance"),
                "capitalBand", "5_25k",
                "hoursPerWeek", 10
        );
        java.util.Map<String, Object> weakDraft = java.util.Map.of(
                "title", "A tip sheet",
                "offer", "share notes",
                "whyNow", "news happened",
                "capitalNeededInr", 20000,
                "hoursPerWeek", 12,
                "market_size", 0.3,
                "competition_gap", 0.3,
                "regulatory_risk", 0.5,
                "confidence", 0.4
        );
        java.util.Map<String, Object> strongDraft = java.util.Map.of(
                "title", "SEBI compliance education platform",
                "offer", "education course and tracker tool for brokers",
                "whyNow", "circular forces training",
                "capitalNeededInr", 2000,
                "hoursPerWeek", 5,
                "market_size", 0.8,
                "competition_gap", 0.75,
                "regulatory_risk", 0.2,
                "confidence", 0.85
        );

        IdeaScorer.Inputs weakIn = (IdeaScorer.Inputs) method.invoke(null, weakSignal, profile, weakDraft);
        IdeaScorer.Inputs strongIn = (IdeaScorer.Inputs) method.invoke(null, strongSignal, profile, strongDraft);
        int weak = IdeaScorer.score("working", weakIn).score();
        int strong = IdeaScorer.score("working", strongIn).score();

        assertThat(strong).isGreaterThan(weak);
        assertThat(strong).isGreaterThan(50);
    }
}
