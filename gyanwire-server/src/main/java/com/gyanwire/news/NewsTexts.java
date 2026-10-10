package com.gyanwire.news;

import org.jsoup.Jsoup;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class NewsTexts {

    public static final Set<String> SIGNALS = Set.of(
            "launch", "funding", "approval", "regulation", "pricing", "shortage", "research", "opinion", "other"
    );

    private NewsTexts() {
    }

    public static String cleanTitle(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String text = Jsoup.parse(raw).text().replaceAll("\\s+", " ").trim();
        return text.replaceFirst("\\s+[-|–—]\\s+[^|–—]{1,80}$", "").trim();
    }

    public static String titleKey(String title) {
        return cleanTitle(title).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    public static String titleHash(String title) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(titleKey(title).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return titleKey(title);
        }
    }

    public static long simhash(String title) {
        int[] bits = new int[64];
        List<String> tokens = tokens(titleKey(title));
        if (tokens.isEmpty()) {
            return 0L;
        }
        List<String> grams = new ArrayList<>(tokens);
        for (int i = 0; i < tokens.size() - 2; i++) {
            grams.add(tokens.get(i) + "_" + tokens.get(i + 1) + "_" + tokens.get(i + 2));
        }
        for (String token : grams) {
            long hash = fnv(token);
            for (int i = 0; i < 64; i++) {
                if (((hash >>> i) & 1L) == 1L) {
                    bits[i]++;
                } else {
                    bits[i]--;
                }
            }
        }
        long out = 0L;
        for (int i = 0; i < 64; i++) {
            if (bits[i] > 0) {
                out |= (1L << i);
            }
        }
        return out;
    }

    public static int hamming(long left, long right) {
        return Long.bitCount(left ^ right);
    }

    public static boolean nearDuplicate(String left, String right) {
        String a = titleKey(left);
        String b = titleKey(right);
        if (a.isBlank() || b.isBlank()) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        double jaccard = jaccard(tokens(a), tokens(b));
        if (jaccard >= 0.72) {
            return true;
        }
        return jaccard >= 0.45 && hamming(simhash(a), simhash(b)) <= 8;
    }

    public static String languageOf(String title) {
        int letters = 0;
        int allowed = 0;
        for (int i = 0; i < title.length(); ) {
            int cp = title.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isLetter(cp)) {
                continue;
            }
            letters++;
            if (isKeptScript(cp)) {
                allowed++;
            }
        }
        if (letters == 0) {
            return "en";
        }
        if (allowed * 100 / letters < 60) {
            return "other";
        }
        return "en";
    }

    public static boolean keepLanguage(String title) {
        return !"other".equals(languageOf(title));
    }

    public static String domainOf(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return "";
            }
            String lower = host.toLowerCase(Locale.ROOT);
            return lower.startsWith("www.") ? lower.substring(4) : lower;
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean aggregator(String url) {
        String domain = domainOf(url);
        return domain.contains("news.google.com") || domain.contains("feedproxy") || domain.equals("t.co");
    }

    public static String ageLabel(Instant published, Instant now) {
        if (published == null || now == null) {
            return "";
        }
        long days = Math.max(0, ChronoUnit.DAYS.between(published, now));
        if (days <= 0) {
            return "today";
        }
        if (days == 1) {
            return "1 day ago";
        }
        return days + " days ago";
    }

    public static String ownSummary(String signal, String domain) {
        String kind = switch (signal == null ? "other" : signal) {
            case "launch" -> "A product launch";
            case "funding" -> "A funding report";
            case "approval" -> "An approval notice";
            case "regulation" -> "A regulation notice";
            case "pricing" -> "A pricing change";
            case "shortage" -> "A shortage or recall";
            case "research" -> "A research note";
            case "opinion" -> "An opinion piece";
            default -> "A news item";
        };
        String host = domain == null || domain.isBlank() ? "the publisher" : domain;
        return kind + " from " + host + ".";
    }

    public static int windowToken(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return fallback;
        }
        return Integer.parseInt(digits);
    }

    private static boolean isKeptScript(int cp) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
        if (block == null) {
            return false;
        }
        return block == Character.UnicodeBlock.BASIC_LATIN
                || block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT
                || block == Character.UnicodeBlock.LATIN_EXTENDED_A
                || block == Character.UnicodeBlock.DEVANAGARI
                || block == Character.UnicodeBlock.BENGALI
                || block == Character.UnicodeBlock.GURMUKHI
                || block == Character.UnicodeBlock.GUJARATI
                || block == Character.UnicodeBlock.ORIYA
                || block == Character.UnicodeBlock.TAMIL
                || block == Character.UnicodeBlock.TELUGU
                || block == Character.UnicodeBlock.KANNADA
                || block == Character.UnicodeBlock.MALAYALAM;
    }

    private static List<String> tokens(String key) {
        if (key == null || key.isBlank()) {
            return List.of();
        }
        String[] parts = key.split(" ");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            if (part.length() > 1) {
                out.add(part);
            }
        }
        return out;
    }

    private static double jaccard(List<String> left, List<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        int shared = 0;
        for (String token : left) {
            if (right.contains(token)) {
                shared++;
            }
        }
        int union = left.size() + right.size() - shared;
        return union == 0 ? 0 : shared / (double) union;
    }

    private static long fnv(String token) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < token.length(); i++) {
            hash ^= token.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
