package com.gyanwire.ideas.pipeline;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class TextCheck {

    static final List<String> BANNED = List.of("low monthly fee", "₹0 to start", "rs 0 to start", "because of this news");

    private static final Set<String> GENERIC_WHO = Set.of(
            "people", "everyone", "users", "customers", "consumers", "indians", "indian people",
            "people in india", "users in india", "customers in india", "audience", "the public",
            "general public", "indian consumers", "online users");

    private static final Pattern FILLER = Pattern.compile(
            "\\b(in|and|the|a|an|of|for|on|with|who|their|they|india|indian|online|people|users|customers|consumers|everyone|audience|public)\\b");

    private TextCheck() {
    }

    static int words(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    static String banned(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        for (String phrase : BANNED) {
            if (lower.contains(phrase)) {
                return phrase;
            }
        }
        return null;
    }

    static boolean genericWho(String who) {
        if (who == null || who.isBlank()) {
            return true;
        }
        String norm = who.toLowerCase(Locale.ROOT).replaceAll("[^a-z\\s]", " ").replaceAll("\\s+", " ").trim();
        if (norm.length() < 12 || GENERIC_WHO.contains(norm)) {
            return true;
        }
        String stripped = FILLER.matcher(norm).replaceAll(" ").replaceAll("\\s+", " ").trim();
        return stripped.length() < 4;
    }

    static boolean genericTrigger(String trigger) {
        if (trigger == null || trigger.isBlank()) {
            return true;
        }
        String norm = trigger.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        if (norm.length() < 24) {
            return true;
        }
        return norm.contains("because of this news") || norm.equals("this news") || norm.equals("the news");
    }
}
