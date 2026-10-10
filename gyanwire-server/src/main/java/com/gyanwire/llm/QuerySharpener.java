package com.gyanwire.llm;

import java.util.regex.Pattern;

public final class QuerySharpener {

    private static final Pattern DEVANAGARI = Pattern.compile("\\p{IsDevanagari}");
    private static final Pattern LATIN = Pattern.compile("[A-Za-z]");

    private QuerySharpener() {
    }

    public static boolean messy(String thoughts) {
        if (thoughts == null || thoughts.isBlank()) {
            return false;
        }
        String text = thoughts.trim();
        if (text.length() > 80) {
            return true;
        }
        boolean dev = DEVANAGARI.matcher(text).find();
        boolean lat = LATIN.matcher(text).find();
        if (dev && lat) {
            return true;
        }
        int longWords = 0;
        for (String word : text.split("\\s+")) {
            if (word.length() > 3) {
                longWords++;
            }
        }
        return longWords < 2;
    }
}
