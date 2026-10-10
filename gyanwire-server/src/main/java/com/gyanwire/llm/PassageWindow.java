package com.gyanwire.llm;

public final class PassageWindow {

    public static final int MAX_CHARS = 2400;

    private PassageWindow() {
    }

    public static String trim(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= MAX_CHARS) {
            return cleaned;
        }
        return cleaned.substring(0, MAX_CHARS);
    }
}
