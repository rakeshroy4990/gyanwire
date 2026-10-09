package com.gyanwire.research.engine;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Prefer findings that spark curiosity over generic market or daily wrap news.
 */
public final class CuriosityRank {

    private static final List<String> HOOKS = List.of(
            "first", "secret", "leak", "leaked", "exclusive", "unexpected", "surprising", "surprise",
            "weird", "unusual", "rare", "breakthrough", "never before", "banned", "lawsuit",
            "controversy", "behind the scenes", "how ", "why ", "demo", "teaser", "prototype",
            "counterintuitive", "unexplained", "viral", "cheaper", "fails", "works", "hack",
            "playbook", "case study", "deep dive", "odd", "strange", "mystery", "hidden",
            "loophole", "workaround", "mistake", "wrong", "myth", "underestimated", "overhyped",
            "quietly", "suddenly", "turns out", "nobody", "little-known", "under the radar",
            "experiment", "pilot", "beta", "open-sourced", "open sourced", "free forever",
            "10x", "overnight", "shock", "caught off guard", "plot twist"
    );

    private static final List<String> GENERIC = List.of(
            "share market live", "stock market today", "stock market live", "market today",
            "nifty closes", "sensex ends", "sensex closes", "nifty ends", "closing bell",
            "opens higher", "opens lower", "ends higher", "ends lower", "points higher",
            "points lower", "mid-day", "midday wrap", "morning wrap", "evening wrap",
            "top headlines", "top news", "news roundup", "daily digest", "what happened today",
            "in pictures", "photos:", "live blog", "live updates", "market wrap",
            "stocks to watch today", "things that will decide", "prediction for today",
            "sensex today", "nifty today", "market highlights", "closing market"
    );

    private CuriosityRank() {}

    public static double hookScore(String text) {
        String hay = normalize(text);
        if (hay.isBlank()) {
            return 0;
        }
        long hits = HOOKS.stream().filter(hay::contains).count();
        return Math.min(1.0, hits / 3.0);
    }

    public static double genericScore(String text) {
        String hay = normalize(text);
        if (hay.isBlank()) {
            return 0;
        }
        long hits = GENERIC.stream().filter(hay::contains).count();
        return Math.min(1.0, hits / 2.0);
    }

    public static boolean looksGeneric(String text) {
        return genericScore(text) >= 0.5;
    }

    public static int boost(Map<String, Object> item) {
        String blob = String.valueOf(item.getOrDefault("title", "")) + " "
                + item.getOrDefault("description", "") + " "
                + item.getOrDefault("why", "") + " "
                + item.getOrDefault("snippet", "");
        int base = item.get("score") instanceof Number n ? n.intValue() : 60;
        int adjusted = (int) Math.round(base + 18 * hookScore(blob) - 28 * genericScore(blob));
        return Math.max(0, Math.min(100, adjusted));
    }

    public static String why(String text) {
        if (looksGeneric(text)) {
            return "Looks like a routine wrap, so it ranks lower.";
        }
        if (hookScore(text) >= 0.34) {
            return "Curious angle that invites a closer read.";
        }
        return "";
    }

    private static String normalize(String value) {
        return String.valueOf(value).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s'-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
