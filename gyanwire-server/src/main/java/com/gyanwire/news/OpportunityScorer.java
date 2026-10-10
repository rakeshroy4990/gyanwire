package com.gyanwire.news;

import java.time.Duration;
import java.time.Instant;

public final class OpportunityScorer {

    public record Parts(int recency, int signal, int specificity, int india, int actionability, int total, String scoredBy) {
    }

    private OpportunityScorer() {
    }

    public static Parts score(
            NewsProperties.Rank weights,
            String signal,
            int specificity,
            int india,
            int actionability,
            Instant published,
            Instant now,
            boolean rulesOnly
    ) {
        int recencyMax = weights.getRecency();
        int signalMax = weights.getSignal();
        int specMax = weights.getSpecificity();
        int indiaMax = weights.getIndia();
        int actionMax = weights.getActionability();
        int recency = recencyPoints(published, now, weights.getFreshFullDays(), 14, recencyMax);
        int signalPoints = signalPoints(signal, signalMax);
        int specPoints = scale(specificity, specMax);
        int indiaPoints = scale(india, indiaMax);
        int actionPoints = scale(actionability, actionMax);
        int total = recency + signalPoints + specPoints + indiaPoints + actionPoints;
        if (rulesOnly) {
            total = Math.min(total, weights.getRulesCap());
        }
        total = Math.max(0, Math.min(100, total));
        return new Parts(recency, signalPoints, specPoints, indiaPoints, actionPoints, total, rulesOnly ? "rules" : "llm");
    }

    public static int recencyPoints(Instant published, Instant now, int fullDays, int windowDays, int max) {
        if (published == null || now == null || max <= 0) {
            return 0;
        }
        long days = Math.max(0, Duration.between(published, now).toDays());
        if (days <= Math.max(fullDays, 0)) {
            return max;
        }
        int span = Math.max(windowDays - fullDays, 1);
        double fade = Math.max(0, span - (days - fullDays)) / (double) span;
        return (int) Math.round(max * fade);
    }

    static int signalPoints(String signal, int max) {
        double factor = switch (signal == null ? "other" : signal) {
            case "launch", "funding", "approval", "pricing", "shortage" -> 1.0;
            case "regulation" -> 0.9;
            case "research" -> 0.45;
            case "opinion" -> 0.2;
            default -> 0.35;
        };
        return (int) Math.round(max * factor);
    }

    private static int scale(int value, int max) {
        int clamped = Math.max(0, Math.min(100, value));
        return (int) Math.round(max * (clamped / 100.0));
    }
}
