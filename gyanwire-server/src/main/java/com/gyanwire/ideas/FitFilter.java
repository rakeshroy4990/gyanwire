package com.gyanwire.ideas;

import java.util.Locale;
import java.util.Set;

public final class FitFilter {

    public record Decision(boolean keep, String reason) {
    }

    private FitFilter() {
    }

    public static Decision decide(
            String industry,
            String persona,
            String text,
            int capitalNeeded,
            int capitalAvailable,
            boolean capitalRaised,
            int hoursNeeded,
            int hoursAvailable,
            boolean legalityFlag
    ) {
        String blob = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (legalityFlag || containsAny(blob, "unregistered investment advice", "ponzi", "scam")) {
            return new Decision(false, "legality");
        }
        if (containsAny(blob, "stock tip", "buy the stock", "sell the stock", "guaranteed returns", "trading signal")) {
            return new Decision(false, "investment-advice");
        }
        if (containsAny(blob, "diagnosis", "prescribe", "dosage", "treatment claim", "cure for")) {
            return new Decision(false, "medical-claim");
        }
        if (containsAny(blob, "gambling", "casino", "betting odds")) {
            return new Decision(false, "gambling");
        }
        if ("Share Market".equals(industry) && !containsAny(blob,
                "education", "course", "tool", "explainer", "tracker", "learn",
                "checklist", "template", "briefing", "newsletter", "workshop",
                "calculator", "guide", "dashboard", "alert", "training")) {
            return new Decision(false, "share-market-scope");
        }
        if ("Medical".equals(industry) && !containsAny(blob,
                "education", "admin", "logistics", "records", "training", "clinic ops",
                "checklist", "template", "scheduling", "intake")) {
            return new Decision(false, "medical-scope");
        }
        if (capitalAvailable >= 0 && capitalNeeded > 3L * capitalAvailable) {
            return new Decision(false, "capital");
        }
        if (("student".equals(persona) || "fresher".equals(persona)) && capitalNeeded > 5000 && !capitalRaised) {
            return new Decision(false, "student-capital");
        }
        if (hoursNeeded > hoursAvailable + 5) {
            return new Decision(false, "hours");
        }
        return new Decision(true, "ok");
    }

    public static int capitalCeiling(String band) {
        if (band == null) {
            return 0;
        }
        return switch (band) {
            case "0" -> 0;
            case "under_5k" -> 5000;
            case "5_25k" -> 25000;
            case "25k_1l" -> 100000;
            case "over_1l" -> 300000;
            default -> 0;
        };
    }

    public static boolean capitalRaised(String persona, String band) {
        if (!"student".equals(persona) && !"fresher".equals(persona)) {
            return true;
        }
        return band != null && !Set.of("0", "under_5k").contains(band);
    }

    private static boolean containsAny(String blob, String... needles) {
        for (String needle : needles) {
            if (blob.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
