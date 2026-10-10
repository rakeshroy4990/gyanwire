package com.gyanwire.news;

import java.util.Locale;
import java.util.regex.Pattern;

public final class SignalRules {

    private static final Pattern LAUNCH = Pattern.compile("\\b(launch(?:es|ed)?|introduc(?:es|ed)|unveil(?:s|ed)?|rolls out|rolled out)\\b");
    private static final Pattern FUNDING = Pattern.compile("\\b(raises|raised|raising|funding|seed round|series [a-d])\\b");
    private static final Pattern APPROVAL = Pattern.compile("\\b(approv(?:ed|es|al)|clears|cleared|clearance)\\b");
    private static final Pattern REGULATION = Pattern.compile("\\b(regulation|regulatory|notification|guidelines|circular|ban(?:ned)?|compliance)\\b");
    private static final Pattern SHORTAGE = Pattern.compile("\\b(shortage|recall(?:ed)?|stockout|out of stock)\\b");
    private static final Pattern PRICING = Pattern.compile("\\b(price cut|prices|pricing|fee cut|discount)\\b");
    private static final Pattern RESEARCH = Pattern.compile("\\b(study|trial|researchers|clinical trial)\\b");
    private static final Pattern OPINION = Pattern.compile("\\b(opinion|editorial|column|analysis:)\\b");

    private SignalRules() {
    }

    public static String detect(String title) {
        String text = title == null ? "" : title.toLowerCase(Locale.ROOT);
        if (FUNDING.matcher(text).find()) {
            return "funding";
        }
        if (APPROVAL.matcher(text).find()) {
            return "approval";
        }
        if (REGULATION.matcher(text).find()) {
            return "regulation";
        }
        if (SHORTAGE.matcher(text).find()) {
            return "shortage";
        }
        if (PRICING.matcher(text).find()) {
            return "pricing";
        }
        if (LAUNCH.matcher(text).find()) {
            return "launch";
        }
        if (OPINION.matcher(text).find()) {
            return "opinion";
        }
        if (RESEARCH.matcher(text).find()) {
            return "research";
        }
        return "other";
    }

    public static int specificity(String title) {
        String text = title == null ? "" : title;
        int score = 30;
        if (text.matches(".*\\d+.*")) {
            score += 20;
        }
        if (text.matches(".*\\b[A-Z][a-z]+(?:\\s+[A-Z][a-z]+)+\\b.*")) {
            score += 25;
        }
        if (text.toLowerCase(Locale.ROOT).matches(".*(sebi|rbi|cdsco|isro|icmr|meity|nse|bse).*")) {
            score += 15;
        }
        return Math.min(100, score);
    }

    public static int indiaRelevance(String title) {
        String text = title == null ? "" : title.toLowerCase(Locale.ROOT);
        if (text.matches(".*(india|indian|delhi|mumbai|bengaluru|hyderabad|chennai|pune|kolkata|sebi|rbi|cdsco|isro|icmr|meity|nse|bse|in-space).*")) {
            return 80;
        }
        return 20;
    }

    public static int actionability(String signal) {
        return switch (signal == null ? "other" : signal) {
            case "launch", "funding", "approval", "pricing", "shortage" -> 70;
            case "regulation" -> 60;
            case "research" -> 30;
            case "opinion" -> 15;
            default -> 25;
        };
    }

    public static String whyIdea(String signal, String industry) {
        String lane = lane(industry);
        return switch (signal == null ? "other" : signal) {
            case "launch" -> "A clinic, shop, or team that must adopt the new offer could pay for a setup checklist. " + lane;
            case "funding" -> "Operators near the funded company could pay for a short watchlist of who is buying. " + lane;
            case "approval" -> "Teams waiting on the approval could pay for a plain-language checklist of what changed. " + lane;
            case "regulation" -> "Compliance leads could pay for a one-page brief of the new rule. " + lane;
            case "pricing" -> "Buyers comparing cost could pay for a simple price tracker. " + lane;
            case "shortage" -> "Ops leads could pay for a substitute and stock alert. " + lane;
            case "research" -> "Trainers could turn the finding into a short lesson. " + lane;
            default -> "A narrow service could help people act on this headline. " + lane;
        };
    }

    private static String lane(String industry) {
        if ("Medical".equals(industry)) {
            return "Education, admin, or logistics only.";
        }
        if ("Share Market".equals(industry)) {
            return "Education or tools only.";
        }
        return "";
    }
}
