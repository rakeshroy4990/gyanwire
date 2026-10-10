package com.gyanwire.ideas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Keeps an online course as one idea at most. Extra course-shaped drafts are
 * rewritten into other small-business formats tied to the same news.
 * A finding offers only the formats its news can support, so the count is not fixed.
 */
public final class IdeaVariety {

    /** Full catalog. A finding uses a subset; eight is the ceiling, not the default. */
    static final List<String> OPTIONS = List.of(
            "course", "checklist", "tracker", "briefing", "template", "session", "service", "alert");

    private static final Pattern COURSE_PREFIX = Pattern.compile(
            "(?i)^(?:an?\\s+)?(?:online\\s+|video\\s+|self-paced\\s+|recorded\\s+)*(?:e-?)?courses?\\s+(?:on|about|for|covering)\\s+");
    private static final Pattern COURSE_WORD = Pattern.compile("(?i)\\b(?:online\\s+course|e-?course|video\\s+course|courses?)\\b");

    private IdeaVariety() {
    }

    /**
     * Formats this finding can support. The event type sets a small base, and the
     * headline adds formats only when its own words support them. Eight is the ceiling.
     * Pattern shapes are not applied to every article of that event, or a whole feed
     * would show the same count.
     */
    public static List<String> formatsFor(String eventType, List<String> ideaShapes, String text, int score) {
        String event = eventType == null || eventType.isBlank() ? "other" : eventType;
        String blob = text == null ? "" : text.toLowerCase(Locale.ROOT);
        Set<String> fitted = new LinkedHashSet<>();
        fitted.add("briefing");
        addEventFormats(fitted, event);
        if (contains(blob, "checklist", "compliance", "audit", "circular", "filing", "approval", "approved", "patent", "duty", "tax")) {
            fitted.add("checklist");
        }
        if (contains(blob, "tracker", "calculator", "price", "prices", "cost", "margin", "expensive")
                || blob.matches("(?s).*\\b(?:billion|crore|lakh)\\b.*")) {
            fitted.add("tracker");
        }
        if (contains(blob, "template", "launch", "releases", "released")) {
            fitted.add("template");
        }
        if (contains(blob, "course", "training", "education", "what is", "how to", "why does", "all you need to know")) {
            fitted.add("course");
        }
        if (contains(blob, "workshop", "mentoring", "cohort", "this week", "live session")) {
            fitted.add("session");
        }
        if (contains(blob, "orders", "ordered", "setup", "onboarding", "cluster")) {
            fitted.add("service");
        }
        if (contains(blob, "deadline", "approval", "approved", "patent", "tax", "duty")) {
            fitted.add("alert");
        }

        List<String> ordered = new ArrayList<>();
        ordered.add("briefing");
        for (String kind : OPTIONS) {
            if (fitted.contains(kind) && !"briefing".equals(kind)) {
                ordered.add(kind);
            }
        }
        int budget = budgetFor(score);
        if (ordered.size() > budget) {
            ordered = new ArrayList<>(ordered.subList(0, budget));
        }
        if (ordered.size() < 2) {
            String fromShape = formatNamedIn(ideaShapes);
            if (fromShape != null && !ordered.contains(fromShape)) {
                ordered.add(fromShape);
            }
        }
        if (ordered.size() < 2) {
            for (String kind : List.of("checklist", "tracker", "template", "alert")) {
                if (!ordered.contains(kind)) {
                    ordered.add(kind);
                }
                if (ordered.size() >= 2) {
                    break;
                }
            }
        }
        return List.copyOf(ordered);
    }

    private static void addEventFormats(Set<String> fitted, String event) {
        switch (event) {
            case "regulation" -> {
                fitted.add("checklist");
                fitted.add("template");
                fitted.add("alert");
            }
            case "govt_scheme" -> {
                fitted.add("checklist");
                fitted.add("template");
                fitted.add("service");
            }
            case "price_move" -> {
                fitted.add("tracker");
                fitted.add("alert");
            }
            case "data_release" -> fitted.add("tracker");
            case "tech_release" -> {
                fitted.add("template");
                fitted.add("service");
            }
            case "skills_gap" -> {
                fitted.add("course");
                fitted.add("session");
            }
            case "supply_disruption" -> {
                fitted.add("alert");
                fitted.add("service");
            }
            case "infrastructure" -> {
                fitted.add("service");
                fitted.add("template");
            }
            case "funding", "exit_failure" -> fitted.add("service");
            case "event_season" -> fitted.add("session");
            default -> {
            }
        }
    }

    /** A pattern shape can fill a thin story. It does not stamp every sibling headline. */
    private static String formatNamedIn(List<String> ideaShapes) {
        String shapes = ideaShapes == null ? "" : String.join(" ", ideaShapes).toLowerCase(Locale.ROOT);
        if (shapes.contains("checklist")) {
            return "checklist";
        }
        if (shapes.contains("tracker") || shapes.contains("dashboard")) {
            return "tracker";
        }
        if (shapes.contains("template")) {
            return "template";
        }
        if (shapes.contains("training") || shapes.contains("education") || shapes.contains("course")) {
            return "course";
        }
        if (shapes.contains("service") || shapes.contains("onboarding")) {
            return "service";
        }
        return null;
    }

    public static List<Map<String, Object>> diversify(List<Map<String, Object>> drafts, String newsTitle, String industry) {
        return diversify(drafts, newsTitle, industry, null);
    }

    /**
     * @param formats formats this news can support; null keeps the full catalog for older callers
     */
    public static List<Map<String, Object>> diversify(
            List<Map<String, Object>> drafts,
            String newsTitle,
            String industry,
            List<String> formats
    ) {
        boolean limited = formats != null;
        List<String> target = limited ? cleanTarget(formats) : OPTIONS;
        List<Map<String, Object>> source = drafts == null ? List.of() : drafts;
        String topic = topic(newsTitle, source);
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        boolean keptCourse = false;

        for (Map<String, Object> draft : source) {
            if (draft == null) {
                continue;
            }
            draft.remove("_formatRewritten");
            String kind = kind(draft);
            boolean inTarget = target.contains(kind);
            if ("course".equals(kind)) {
                if (!inTarget && limited) {
                    continue;
                }
                if (keptCourse || !inTarget) {
                    kind = nextUnused(used, target);
                    if (kind == null) {
                        continue;
                    }
                    rewrite(draft, kind, topic, newsTitle, industry);
                } else {
                    keptCourse = true;
                }
            } else if ("other".equals(kind)) {
                if (limited) {
                    continue;
                }
            } else if (!inTarget || used.contains(kind)) {
                if (limited && !inTarget) {
                    continue;
                }
                kind = nextUnused(used, target);
                if (kind == null) {
                    continue;
                }
                rewrite(draft, kind, topic, newsTitle, industry);
            }
            draft.put("format", formatLabel(kind));
            used.add(kind);
            out.add(draft);
        }

        for (String kind : target) {
            if (used.contains(kind)) {
                continue;
            }
            out.add(fresh(kind, topic, newsTitle, industry));
            used.add(kind);
        }
        return out;
    }

    private static List<String> cleanTarget(List<String> formats) {
        List<String> target = new ArrayList<>();
        for (String kind : OPTIONS) {
            if (formats.contains(kind) && !target.contains(kind)) {
                target.add(kind);
            }
        }
        if (target.size() < 2) {
            return formatsFor("other", List.of(), "", 0);
        }
        return target;
    }

    private static int budgetFor(int score) {
        if (score >= 70) {
            return 8;
        }
        if (score >= 50) {
            return 6;
        }
        if (score >= 35) {
            return 5;
        }
        if (score >= 15) {
            return 4;
        }
        return 2;
    }

    static String formatLabel(String kind) {
        return switch (kind) {
            case "course" -> "Online course";
            case "checklist" -> "Checklist";
            case "tracker" -> "Tracker";
            case "briefing" -> "Briefing";
            case "template" -> "Template pack";
            case "session" -> "Live session";
            case "service" -> "Setup help";
            case "alert" -> "Alerts";
            default -> "Idea";
        };
    }

    public static String formatKind(Map<String, Object> idea) {
        return kind(idea);
    }

    static String kind(Map<String, Object> idea) {
        String fromTitle = match(String.valueOf(idea.getOrDefault("title", "")).toLowerCase(Locale.ROOT));
        if (fromTitle != null) {
            return fromTitle;
        }
        String rest = (String.valueOf(idea.getOrDefault("offer", "")) + " "
                + idea.getOrDefault("business_model", "") + " "
                + idea.getOrDefault("businessModel", "")).toLowerCase(Locale.ROOT);
        String fromRest = match(rest);
        return fromRest == null ? "other" : fromRest;
    }

    private static String match(String blob) {
        if (COURSE_WORD.matcher(blob).find()) {
            return "course";
        }
        if (contains(blob, "checklist", "audit pack")) {
            return "checklist";
        }
        if (contains(blob, "tracker", "calculator", "dashboard")) {
            return "tracker";
        }
        if (contains(blob, "briefing", "newsletter", "explainer")) {
            return "briefing";
        }
        if (blob.contains("template")) {
            return "template";
        }
        if (blob.contains("alert")) {
            return "alert";
        }
        if (contains(blob, "live working session", "live session", "workshop")) {
            return "session";
        }
        if (contains(blob, "setup help", "done-for-you", "done for you", "filing help")) {
            return "service";
        }
        return null;
    }

    private static String nextUnused(Set<String> used, List<String> target) {
        for (String kind : target) {
            if (!"course".equals(kind) && !used.contains(kind)) {
                return kind;
            }
        }
        return null;
    }

    private static Map<String, Object> fresh(String kind, String topic, String newsTitle, String industry) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("capitalNeededInr", 0);
        row.put("hoursPerWeek", 4);
        row.put("confidence", 0.45);
        row.put("confidenceNote", "estimate");
        rewrite(row, kind, topic, newsTitle, industry);
        row.put("format", formatLabel(kind));
        return row;
    }

    private static void rewrite(Map<String, Object> draft, String kind, String topic, String newsTitle, String industry) {
        draft.put("title", titleFor(kind, topic));
        draft.put("offer", offerFor(kind, industry));
        draft.put("business_model", modelFor(kind));
        String why = String.valueOf(draft.getOrDefault("whyNow", draft.getOrDefault("why", ""))).trim();
        if (why.isBlank() || COURSE_WORD.matcher(why).find()) {
            why = "Why now: " + (newsTitle == null || newsTitle.isBlank() ? topic : newsTitle.trim()) + ".";
        }
        draft.put("whyNow", why);
        draft.put("why", why);
        draft.putIfAbsent("segment", "People who have to act on " + topic);
        draft.putIfAbsent("capitalNeededInr", 0);
        draft.putIfAbsent("hoursPerWeek", 4);
        draft.put("_formatRewritten", true);
    }

    private static String titleFor(String kind, String topic) {
        return switch (kind) {
            case "course" -> "Online course on " + topic;
            case "checklist" -> topic + " checklist";
            case "tracker" -> topic + " tracker";
            case "briefing" -> topic + " briefing";
            case "template" -> topic + " template pack";
            case "session" -> "Live working session on " + topic;
            case "service" -> "Setup help for " + topic;
            case "alert" -> topic + " change alerts";
            default -> topic + " starter kit";
        };
    }

    private static String offerFor(String kind, String industry) {
        String base = switch (kind) {
            case "course" -> "A short online course that teaches what this news changes and what to do next.";
            case "checklist" -> "A one-page checklist the buyer completes because of this news.";
            case "tracker" -> "A tracker tool that shows what changed and what is still open.";
            case "briefing" -> "A short explainer briefing on what this news changes for the buyer.";
            case "template" -> "A template pack: forms, emails, and a spreadsheet the buyer fills in. You make the pack once from this news and sell each copy as a one-time download.";
            case "session" -> "A live working session with a small group applying this news.";
            case "service" -> "Done-for-you setup help for the first workflow this news creates.";
            case "alert" -> "An alert when the rule, price, or date in this news changes again.";
            default -> "A small offer tied to this news.";
        };
        if ("Share Market".equals(industry)) {
            return base + " Education or a tool only. Not investment advice.";
        }
        if ("Medical".equals(industry)) {
            return base + " Admin, logistics, records, or education only. Not a diagnosis or treatment.";
        }
        return base;
    }

    private static String modelFor(String kind) {
        return switch (kind) {
            case "course" -> "Course fee";
            case "tracker", "briefing", "alert" -> "Low monthly fee";
            case "service" -> "Project fee";
            case "session" -> "Seat fee";
            default -> "One-time download";
        };
    }

    private static String topic(String newsTitle, List<Map<String, Object>> drafts) {
        for (Map<String, Object> draft : drafts) {
            if (draft == null) {
                continue;
            }
            String title = String.valueOf(draft.getOrDefault("title", "")).trim();
            String stripped = COURSE_PREFIX.matcher(title).replaceFirst("").trim();
            if (!stripped.equalsIgnoreCase(title) && stripped.length() >= 8) {
                return cleanTopic(stripped);
            }
        }
        return cleanTopic(newsTitle);
    }

    private static String cleanTopic(String raw) {
        String topic = raw == null ? "" : raw.trim();
        int pipe = topic.indexOf('|');
        if (pipe > 0) {
            topic = topic.substring(0, pipe).trim();
        }
        topic = topic.replaceAll("\\s+", " ").replaceAll("[.]+$", "").trim();
        if (topic.isBlank()) {
            return "This news";
        }
        if (topic.length() <= 72) {
            return topic;
        }
        int cut = topic.lastIndexOf(' ', 72);
        return (cut > 24 ? topic.substring(0, cut) : topic.substring(0, 72)).trim();
    }

    private static boolean contains(String blob, String... needles) {
        for (String needle : needles) {
            if (blob.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
