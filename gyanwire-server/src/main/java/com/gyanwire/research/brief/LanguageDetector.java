package com.gyanwire.research.brief;

public final class LanguageDetector {

    private LanguageDetector() {
    }

    public static String detect(String text) {
        if (text == null) {
            return "en";
        }
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(text.charAt(i));
            if (block == Character.UnicodeBlock.DEVANAGARI) {
                return "hi";
            }
        }
        return "en";
    }
}
