package com.gyanwire.llm;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WhatIfNumbers {

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private WhatIfNumbers() {
    }

    public static boolean onlyKnownNumbers(String text, Set<String> allowed) {
        if (text == null || text.isBlank()) {
            return true;
        }
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            if (!allowed.contains(strip(matcher.group()))) {
                return false;
            }
        }
        return true;
    }

    public static Set<String> allowed(int... numbers) {
        Set<String> out = new HashSet<>();
        for (int number : numbers) {
            out.add(Integer.toString(Math.abs(number)));
        }
        return out;
    }

    private static String strip(String raw) {
        if (raw.endsWith(".0")) {
            return raw.substring(0, raw.length() - 2);
        }
        int dot = raw.indexOf('.');
        if (dot > 0 && raw.substring(dot + 1).chars().allMatch(ch -> ch == '0')) {
            return raw.substring(0, dot);
        }
        return raw;
    }
}
