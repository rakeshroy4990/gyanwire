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
        String hook = firstWords(subject, 6);
        return new BusinessIdea(
                "comparison_content",
                "Buyer brief for operators reacting to " + hook,
                oneLiner(hook),
                new Customer("Operators and builders who must act on this headline", "LinkedIn and email"),
                trigger(subject),
                "They pay for a clear buyer, wedge, and first test — not a restated headline.",
                "\"" + hook + "\" is current, so the window to help that buyer is this week.",
                "Ship a one-page brief: who buys, what changes, and a 48-hour test they can run.",
                new RevenueModel(
                        "paid_brief",
                        "",
                        "Charge for a short actionable brief or checklist tied to this news, not for ad views alone."),
                List.of(
                        "Name the buyer and the buying moment from the story.",
                        "Write a one-page brief with wedge and first test.",
                        "DM or email 10 people in that buyer group today."),
                new ValidationTest("Send the brief to 10 buyers", "3 replies asking for the next step"),
                List.of("A social rewrite of the headline is not an idea."),
                "hours",
                false);
    }

    private static String oneLiner(String hook) {
        String line = "A buyer brief and first test for people who must react to " + hook + ".";
        String[] words = line.trim().split("\\s+");
        if (words.length <= 22) {
            return line;
        }
        return "A buyer brief for people reacting to " + firstWords(hook, 5) + ".";
    }

    private static String trigger(String subject) {
        String fact = subject.length() > 140 ? subject.substring(0, 140).trim() : subject;
        return "The story says " + fact + " — that is the buying moment, not the content hook.";
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
