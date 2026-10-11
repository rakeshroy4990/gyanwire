package com.gyanwire.ideas.pipeline;

import java.util.List;

/**
 * Typed inputs and outputs for each idea-pipeline stage.
 */
public final class IdeaModels {

    private IdeaModels() {
    }

    public record TimeWindow(String startsAt, String urgency) {
    }

    public record NewsSignals(
            String headline,
            String eventType,
            List<String> entities,
            List<String> confirmedFacts,
            List<String> unconfirmedClaims,
            String indiaRelevance,
            TimeWindow timeWindow,
            List<String> affectedGroups
    ) {
    }

    public record OpportunityGate(
            boolean proceed,
            String reason,
            double confidence,
            List<String> suggestions
    ) {
    }

    public record Customer(String who, String where) {
    }

    public record RevenueModel(String type, String price, String rationale) {
    }

    public record ValidationTest(String action, String successMetric) {
    }

    public record BusinessIdea(
            String angleType,
            String title,
            String oneLiner,
            Customer customer,
            String trigger,
            String whyTheyPay,
            String whyNow,
            String product,
            RevenueModel revenueModel,
            List<String> firstWeek,
            ValidationTest validationTest,
            List<String> risks,
            String buildEffort,
            boolean dependsOnUnconfirmed
    ) {
        public BusinessIdea withUnconfirmed(boolean value) {
            if (value == dependsOnUnconfirmed) {
                return this;
            }
            return new BusinessIdea(
                    angleType, title, oneLiner, customer, trigger, whyTheyPay, whyNow, product,
                    revenueModel, firstWeek, validationTest, risks, buildEffort, value);
        }

        public String blob() {
            return title + " " + oneLiner + " " + trigger + " " + whyTheyPay + " " + whyNow + " " + product;
        }
    }

    public record Removal(String angleType, String title, String reason) {
    }

    public record DimensionScore(String key, int score, String reason, boolean assumed) {
    }

    public record IdeaScore(List<DimensionScore> dimensions, double weighted, int stored) {
    }

    public record ScoredIdea(BusinessIdea idea, IdeaScore score) {
    }

    public record CriticResult(List<BusinessIdea> kept, List<Removal> removed) {
    }

    public record ParsedIdeas(List<BusinessIdea> valid, List<Removal> rejected) {
    }
}
