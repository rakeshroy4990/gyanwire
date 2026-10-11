package com.gyanwire.ideas.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.gyanwire.ideas.pipeline.IdeaModels.BusinessIdea;
import static com.gyanwire.ideas.pipeline.IdeaModels.Customer;
import static com.gyanwire.ideas.pipeline.IdeaModels.IdeaScore;
import static com.gyanwire.ideas.pipeline.IdeaModels.NewsSignals;
import static com.gyanwire.ideas.pipeline.IdeaModels.RevenueModel;
import static com.gyanwire.ideas.pipeline.IdeaModels.ScoredIdea;
import static com.gyanwire.ideas.pipeline.IdeaModels.ValidationTest;

/**
 * Non-FYI news always includes a social media idea. Market commentary stays FYI and is not an idea.
 */
public final class SocialMediaIdea {

    private SocialMediaIdea() {
    }

    public static boolean present(List<ScoredIdea> scored) {
        if (scored == null) {
            return false;
        }
        for (ScoredIdea item : scored) {
            if (item != null && item.idea() != null && "comparison_content".equals(item.idea().angleType())) {
                return true;
            }
        }
        return false;
    }

    public static boolean presentRows(List<Map<String, Object>> rows) {
        if (rows == null) {
            return false;
        }
        for (Map<String, Object> row : rows) {
            if (row != null && "comparison_content".equals(String.valueOf(row.get("angleType")))) {
                return true;
            }
        }
        return false;
    }

    public static List<ScoredIdea> ensure(
            List<ScoredIdea> scored,
            IdeaScoreCalculator calculator,
            String title,
            String description,
            NewsSignals signals
    ) {
        if (present(scored)) {
            return scored;
        }
        BusinessIdea idea = fromHeadline(title, description);
        List<ScoredIdea> out = new ArrayList<>();
        out.add(new ScoredIdea(idea, calculator.scoreOne(idea, signals, Map.of())));
        if (scored != null) {
            out.addAll(scored);
        }
        return out;
    }

    /** Flat API row for idea cards when the classic path must still show social media. */
    public static Map<String, Object> asRow(String title, String description, String industry) {
        BusinessIdea idea = fromHeadline(title, description);
        IdeaScore score = new IdeaScoreCalculator(IdeaScoreWeights.load()).scoreOne(idea, null, Map.of());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("angleType", idea.angleType());
        row.put("format", AngleType.labelOf(idea.angleType()));
        row.put("title", idea.title());
        row.put("oneLiner", idea.oneLiner());
        row.put("offer", idea.oneLiner());
        row.put("customer", Map.of("who", idea.customer().who(), "where", idea.customer().where()));
        row.put("trigger", idea.trigger());
        row.put("whyTheyPay", idea.whyTheyPay());
        row.put("whyNow", idea.whyNow());
        row.put("product", idea.product());
        row.put("revenueModel", Map.of(
                "type", idea.revenueModel().type(),
                "price", idea.revenueModel().price() == null ? "" : idea.revenueModel().price(),
                "rationale", idea.revenueModel().rationale()));
        row.put("firstWeek", idea.firstWeek());
        row.put("validationTest", Map.of(
                "action", idea.validationTest().action(),
                "successMetric", idea.validationTest().successMetric()));
        row.put("risks", idea.risks());
        row.put("buildEffort", idea.buildEffort());
        row.put("dependsOnUnconfirmed", idea.dependsOnUnconfirmed());
        row.put("score", score.stored());
        Map<String, Object> scores = new LinkedHashMap<>();
        scores.put("weighted", score.weighted());
        scores.put("label", "estimate");
        List<Map<String, Object>> dimensions = new ArrayList<>();
        for (var dim : score.dimensions()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", dim.key());
            item.put("score", dim.score());
            item.put("reason", dim.reason());
            item.put("assumed", dim.assumed());
            dimensions.add(item);
        }
        scores.put("dimensions", dimensions);
        row.put("scores", scores);
        if ("Share Market".equals(industry)) {
            row.put("disclaimer", "Informational only. Not investment advice.");
        } else if ("Medical".equals(industry)) {
            row.put("disclaimer", "Research aid, not a diagnosis or treatment.");
        }
        return row;
    }

    public static BusinessIdea fromHeadline(String title, String description) {
        String subject = subject(title, description);
        String hook = firstWords(subject, 5);
        return new BusinessIdea(
                "comparison_content",
                "Post today: " + hook,
                oneLiner(hook),
                new Customer("Viewers who follow this headline on YouTube", "YouTube"),
                trigger(subject),
                "Viewers want a plain read of what this headline changes before it goes stale.",
                "\"" + hook + "\" is current, so the post has to go out today or tomorrow.",
                "Record one short that quotes \"" + hook + "\" and says who should care. "
                        + "Publish that cut on YouTube, Instagram, and LinkedIn the same day.",
                new RevenueModel("ads", "", "Display ads on a short can pay once the clip is up."),
                List.of(
                        "Write the hook from the headline.",
                        "Cut the clip with on-screen words.",
                        "Schedule YouTube, Instagram, and LinkedIn."),
                new ValidationTest("Post the short today", "100 views in two days"),
                List.of("The hook is vague if the headline fact is left out."),
                "hours",
                false);
    }

    private static String oneLiner(String hook) {
        String line = "One short for YouTube, Instagram, and LinkedIn on " + hook + ".";
        String[] words = line.trim().split("\\s+");
        if (words.length <= 18) {
            return line;
        }
        return "One short for YouTube, Instagram, and LinkedIn on " + firstWords(hook, 4) + ".";
    }

    private static String trigger(String subject) {
        String fact = subject.length() > 140 ? subject.substring(0, 140).trim() : subject;
        String line = "The headline says " + fact + ", and that fact is the hook.";
        if (line.length() >= 24) {
            return line;
        }
        return line + " Viewers need that fact in the first line.";
    }

    private static String subject(String title, String description) {
        String value = title == null ? "" : title.trim();
        if (value.isBlank() || "news".equalsIgnoreCase(value)) {
            value = description == null ? "" : description.trim();
        }
        value = value.replaceAll("\\s+", " ").trim();
        return value.isBlank() ? "this story" : value;
    }

    private static String firstWords(String text, int max) {
        String[] parts = text.trim().split("\\s+");
        int count = Math.min(max, parts.length);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(parts[i].replaceAll("[.!?]+$", ""));
        }
        return out.toString();
    }
}
