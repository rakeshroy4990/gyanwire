package com.gyanwire.news;

import java.util.Locale;
import java.util.regex.Pattern;

public final class SignalRules {

    private static final Pattern LAUNCH = Pattern.compile("\\b(launch(?:es|ed)?|introduc(?:es|ed)|unveil(?:s|ed)?|rolls out|rolled out)\\b");
    private static final Pattern FUNDING = Pattern.compile("\\b(raises|raised|raising|funding|seed round|series [a-d])\\b");
    private static final Pattern APPROVAL = Pattern.compile("\\b(approv(?:ed|es|al)|clears|cleared|clearance)\\b");
    private static final Pattern APPROVAL_NEGATION = Pattern.compile(
            "\\b(not|never|yet to be|awaiting|without)\\s+approv|\\bunapprov|\\bnot\\s+cleared\\b");
    private static final Pattern REGULATION = Pattern.compile("\\b(regulation|regulatory|notification|guidelines|circular|ban(?:ned)?|compliance)\\b");
    private static final Pattern SHORTAGE = Pattern.compile("\\b(shortage|recall(?:s|ed)?|stockout|out of stock)\\b");
    private static final Pattern PRICING = Pattern.compile("\\b(price cut|prices|pricing|fee cut|discount)\\b");
    private static final Pattern RESEARCH = Pattern.compile("\\b(study|trial|researchers|clinical trial)\\b");
    private static final Pattern OPINION = Pattern.compile("\\b(opinion|editorial|column|analysis:)\\b");

    /**
     * Market commentary, market plumbing, process chatter, or other FYI with no buyer
     * a small team can serve. Checked before launch/regulation so "unveils" / "notifies"
     * in a settlement note does not look like a product signal.
     */
    private static final Pattern FYI = Pattern.compile(
            "\\b(fii|fpi|dii)s?\\b.{0,40}\\b(dump|sell(?:ing|s|off)?|outflow|withdraw|pull(?:ing)?\\s+out|buying|bought)\\b"
                    + "|\\b(foreign institutional|foreign portfolio).{0,40}\\b(dump|sell|outflow)\\b"
                    + "|\\b(more )?pain ahead\\b"
                    + "|\\breversal on the cards\\b"
                    + "|\\bis a reversal\\b"
                    + "|\\bstocks? for (\\w+ )?(straight|consecutive) weeks\\b"
                    + "|\\bseeks?\\s+(an?\\s+)?(explanation|clarification|response)\\b"
                    + "|\\basks?\\s+.{0,40}\\b(to explain|for clarification|for an explanation)\\b"
                    + "|\\blists?\\b.{0,80}\\bnot approved\\b"
                    + "|\\bfor your information\\b|\\bjust for fyi\\b"
                    + "|\\bcheck details\\b"
                    + "|\\bsettlement (rule|rules|cycle|norms|framework|mechanism|process)\\b"
                    + "|\\b(notifies|notify|notified|notification)\\b.{0,80}\\b(settlement|formula|fast[- ]?track)\\b"
                    + "|\\b(settlement|formula|fast[- ]?track)\\b.{0,80}\\b(notifies|notify|notified|notification|unveils?)\\b"
                    + "|\\bunveils? formula\\b"
                    + "|\\bfast[- ]?track route\\b"
                    + "|\\b(trading holiday|market holiday|circuit breaker|stock split|bonus issue|"
                    + "dividend declared|record date|ex[- ]date)\\b"
                    + "|\\b(nifty|sensex|d-street|wall street|dow jones|nasdaq)\\b.{0,60}\\b"
                    + "(bulls?|bears?|week ahead|today|outlook|momentum|losing run|weekly)\\b"
                    + "|\\b(bonds?|stocks?|gold|oil|euro|rupee|bitcoin)\\b.{0,60}\\b"
                    + "(slide|rally|hits?|falls?|rises?|weekly gains?|weekly drops?|weekly slide)\\b"
                    + "|\\btoday['’]?s?\\s+(gold|silver)\\s+rate\\b"
                    + "|\\b(gold|silver)\\s+rate\\s+in\\s+india\\b"
                    + "|\\b(gold|silver)\\s+prices?\\s+(steady|unchanged|flat|stable|hold|holding)\\b"
                    + "|\\b(gold|silver)\\s+(rate|price|prices)\\s+today\\b"
                    + "|\\bmarket wrap\\b|\\bbull market\\b|\\bmarket commentary\\b"
                    + "|\\blikely within a week\\b"
                    + "|\\bwhat changes for\\b"
                    + "|\\bwar premium\\b|\\brisk premium\\b"
                    + "|\\bcommands a\\b.{0,40}\\bpremium\\b"
                    + "|\\b(brent|wti)\\b.{0,90}\\b(premium|crude|oil)\\b"
                    + "|\\bsupply risks?\\b.{0,60}\\b(keep|keeps|holding|holds|oil|brent|crude|gold)\\b");

    /**
     * Plant inspection status: "receives N observations", Form 483, or an inspection
     * that only reports a visit. A recall, ban, approval, or launch in the same title stays actionable.
     */
    private static final Pattern INSPECTION_STATUS = Pattern.compile(
            "\\breceiv(?:es|ed)\\b.{0,50}\\bobservations?\\b"
                    + "|\\b(gets?|got|issued|issues)\\b.{0,40}\\bobservations?\\b"
                    + "|\\bobservations?\\b.{0,40}\\b(facilit(?:y|ies)|plants?|sites?|units?|inspections?)\\b"
                    + "|\\b(facilit(?:y|ies)|plants?|sites?|units?)\\b.{0,40}\\bobservations?\\b"
                    + "|\\bform[- ]?483\\b"
                    + "|\\b(zero|no|nil)\\s+observations?\\b"
                    + "|\\b(usfda|fda|cdsco)\\b.{0,50}\\b(observations?|inspections?)\\b"
                    + "|\\binspections?\\b.{0,40}\\b(observations?|complet(?:es|ed)|conclud(?:es|ed)|clos(?:es|ed))\\b");

    private static final Pattern INSPECTION_OVERRIDE = Pattern.compile(
            "\\b(approv(?:ed|es|al)|ban(?:ned)?|recall(?:s|ed)?|launch(?:es|ed)?|shortage|stockout)\\b");

    private SignalRules() {
    }

    public static String detect(String title) {
        String text = title == null ? "" : title.toLowerCase(Locale.ROOT);
        if (FYI.matcher(text).find() || inspectionStatus(text)) {
            return "fyi";
        }
        if (FUNDING.matcher(text).find()) {
            return "funding";
        }
        if (APPROVAL.matcher(text).find() && !APPROVAL_NEGATION.matcher(text).find()) {
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

    /** True for an inspection-status headline that does not also name a ban, recall, approval, or launch. */
    static boolean inspectionStatus(String text) {
        return text != null
                && INSPECTION_STATUS.matcher(text).find()
                && !INSPECTION_OVERRIDE.matcher(text).find();
    }

    /** True when the item is informational only and should not appear as idea news. */
    public static boolean isFyi(String signal) {
        return "fyi".equalsIgnoreCase(signal == null ? "" : signal.trim());
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
            case "fyi" -> 0;
            default -> 25;
        };
    }

    /** True when the story names a buyer a small team can serve. */
    public static boolean worthIdea(String signal) {
        return switch (signal == null ? "" : signal.toLowerCase(Locale.ROOT)) {
            case "launch", "funding", "approval", "regulation", "pricing", "shortage",
                    "stars", "tool", "tech" -> true;
            default -> false;
        };
    }

    public static String whyIdea(String signal, String industry) {
        return whyIdea(signal, industry, null);
    }

    public static String whyIdea(String signal, String industry, String title) {
        String lane = lane(industry);
        String tip = entityTip(title);
        return switch (signal == null ? "other" : signal) {
            case "launch" -> "Someone adopting " + tip + " could pay for a setup checklist. " + lane;
            case "funding" -> "Operators near " + tip + " could pay for a short watchlist of who is buying. " + lane;
            case "approval" -> "Teams waiting on " + tip + " could pay for a plain-language checklist of what changed. " + lane;
            case "regulation" -> "Compliance leads covering " + tip + " could pay for a one-page brief of the new rule. " + lane;
            case "pricing" -> "Buyers comparing cost around " + tip + " could pay for a simple price tracker. " + lane;
            case "shortage" -> "Ops leads hit by " + tip + " could pay for a substitute and stock alert. " + lane;
            case "stars", "tool", "tech" -> "Builders could use " + tip + " as the base for a product. " + lane;
            case "research" -> "News only — a study about " + tip + ", with no buyer who has to act today.";
            case "opinion" -> "News only — commentary on " + tip + ", with no buyer who has to act.";
            case "fyi" -> "News only — no specific buyer or buying moment around " + tip + ".";
            default -> "News only — no specific buyer or buying moment around " + tip + ".";
        };
    }

    /** Short entity/topic from the headline so blurbs are not identical across cards. */
    static String entityTip(String title) {
        if (title == null || title.isBlank()) {
            return "this story";
        }
        String cleaned = title.replaceAll("\\s+", " ").trim();
        String[] parts = cleaned.split("\\s+");
        int n = Math.min(4, parts.length);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(parts[i].replaceAll("[,:;]+$", ""));
        }
        String tip = out.toString().trim();
        return tip.isEmpty() ? "this story" : tip;
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
