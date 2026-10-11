package com.gyanwire.ideas.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.gyanwire.ideas.pipeline.IdeaModels.BusinessIdea;
import static com.gyanwire.ideas.pipeline.IdeaModels.DimensionScore;
import static com.gyanwire.ideas.pipeline.IdeaModels.IdeaScore;
import static com.gyanwire.ideas.pipeline.IdeaModels.NewsSignals;
import static com.gyanwire.ideas.pipeline.IdeaModels.ScoredIdea;

/**
 * Deterministic 1–5 scores. Demand stays marked assumed until a search check replaces it.
 */
public final class IdeaScoreCalculator {

    private final IdeaScoreWeights weights;

    public IdeaScoreCalculator(IdeaScoreWeights weights) {
        this.weights = weights;
    }

    public List<ScoredIdea> score(List<BusinessIdea> ideas, NewsSignals signals, Map<String, Object> profile) {
        List<ScoredIdea> scored = new ArrayList<>();
        if (ideas == null) {
            return scored;
        }
        for (BusinessIdea idea : ideas) {
            scored.add(new ScoredIdea(idea, scoreOne(idea, signals, profile)));
        }
        scored.sort((a, b) -> {
            int priority = Integer.compare(
                    AngleType.displayPriority(a.idea().angleType()),
                    AngleType.displayPriority(b.idea().angleType()));
            if (priority != 0) {
                return priority;
            }
            return Double.compare(b.score().weighted(), a.score().weighted());
        });
        return scored;
    }

    public IdeaScore scoreOne(BusinessIdea idea, NewsSignals signals, Map<String, Object> profile) {
        String urgency = signals != null && signals.timeWindow() != null ? signals.timeWindow().urgency() : "evergreen";
        String relevance = signals == null ? "low" : signals.indiaRelevance();
        Map<String, Integer> values = new LinkedHashMap<>();
        List<DimensionScore> dimensions = new ArrayList<>();

        int demand = switch (relevance == null ? "" : relevance) {
            case "high" -> 4;
            case "medium" -> 3;
            default -> 2;
        };
        if (idea.dependsOnUnconfirmed()) {
            demand = Math.max(1, demand - 1);
        }
        values.put("demandEvidence", demand);
        dimensions.add(new DimensionScore(
                "demandEvidence",
                demand,
                "No search-volume check yet, so demand is an estimate from India relevance"
                        + (idea.dependsOnUnconfirmed() ? " and is discounted because specs are still unconfirmed." : "."),
                true));

        int urgencyScore = switch (urgency == null ? "" : urgency) {
            case "hours" -> 5;
            case "days" -> 4;
            case "weeks" -> 3;
            default -> 2;
        };
        values.put("urgency", urgencyScore);
        dimensions.add(new DimensionScore(
                "urgency",
                urgencyScore,
                "Window is " + (urgency == null ? "unknown" : urgency) + ".",
                false));

        int effort = switch (idea.buildEffort() == null ? "" : idea.buildEffort()) {
            case "hours" -> 5;
            case "days" -> 4;
            case "weeks" -> 2;
            default -> 3;
        };
        values.put("buildEffort", effort);
        dimensions.add(new DimensionScore(
                "buildEffort",
                effort,
                "Build effort is " + idea.buildEffort() + ", so easier starts score higher.",
                false));

        int competition = switch (idea.angleType() == null ? "" : idea.angleType()) {
            case "buying_guide_or_alert" -> 2;
            case "comparison_content", "education_or_training" -> 3;
            case "data_or_tool", "b2b_service" -> 4;
            default -> 3;
        };
        values.put("competition", competition);
        dimensions.add(new DimensionScore(
                "competition",
                competition,
                competition >= 4
                        ? "This angle is less crowded than public social or alert pages."
                        : competition <= 2
                        ? "Alert pages already compete for the same search."
                        : "Social and education angles compete, but a news-specific hook still wins.",
                true));

        String revenueType = idea.revenueModel() == null ? "" : idea.revenueModel().type();
        int revenue = switch (revenueType) {
            case "ads" -> 2;
            case "subscription", "service" -> 4;
            default -> 3;
        };
        String price = idea.revenueModel() == null || idea.revenueModel().price() == null ? "" : idea.revenueModel().price();
        if (price.chars().anyMatch(Character::isDigit)) {
            revenue = Math.min(5, revenue + 1);
        }
        values.put("revenuePotential", revenue);
        dimensions.add(new DimensionScore(
                "revenuePotential",
                revenue,
                "Model is " + revenueType + (price.isBlank() ? " with no numeric price." : " at " + price + "."),
                false));

        int fit = founderFit(idea, profile);
        values.put("founderFit", fit);
        dimensions.add(new DimensionScore(
                "founderFit",
                fit,
                fitReason(idea, profile, fit),
                profile == null || !(profile.get("skills") instanceof List<?> skills) || skills.isEmpty()));

        double weighted = weights.weighted(values);
        int stored = (int) Math.round(weighted / 5.0 * 100.0);
        return new IdeaScore(List.copyOf(dimensions), Math.round(weighted * 10.0) / 10.0, stored);
    }

    private static int founderFit(BusinessIdea idea, Map<String, Object> profile) {
        int hours = profile != null && profile.get("hoursPerWeek") instanceof Number n ? n.intValue() : 10;
        if (hours <= 5 && "weeks".equals(idea.buildEffort())) {
            return 2;
        }
        String skills = skillsBlob(profile);
        if (skills.isBlank()) {
            return 3;
        }
        String hay = idea.blob().toLowerCase(Locale.ROOT);
        int hits = 0;
        for (String skill : skills.split("\\s+")) {
            if (skill.length() > 2 && hay.contains(skill)) {
                hits++;
            }
        }
        if (hits >= 2) {
            return 5;
        }
        if (hits == 1) {
            return 4;
        }
        return 3;
    }

    private static String fitReason(BusinessIdea idea, Map<String, Object> profile, int fit) {
        if (fit == 2) {
            return "The profile has little weekly time for a multi-week build.";
        }
        String skills = skillsBlob(profile);
        if (skills.isBlank()) {
            return "No skills listed, so founder fit is an estimate.";
        }
        if (fit >= 4) {
            return "A listed skill shows up in the offer.";
        }
        return "Listed skills do not show up in this offer, so fit stays neutral.";
    }

    private static String skillsBlob(Map<String, Object> profile) {
        if (profile == null || !(profile.get("skills") instanceof List<?> skills)) {
            return "";
        }
        StringBuilder blob = new StringBuilder();
        for (Object skill : skills) {
            blob.append(String.valueOf(skill).toLowerCase(Locale.ROOT)).append(' ');
        }
        return blob.toString().trim();
    }
}
