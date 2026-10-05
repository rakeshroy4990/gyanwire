package com.gyanwire.research.engine;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PointersService {

    private static final Map<String, Double> WEIGHTS = Map.of(
            "thoughtOverlap", 22.0,
            "titleOverlap", 14.0,
            "categoryAffinity", 14.0,
            "researchSignal", 14.0,
            "indiaSignal", 16.0,
            "contentDepth", 8.0,
            "trustedDomain", 8.0,
            "discoveryRank", 4.0
    );

    private static final List<String> RESEARCH_TERMS = List.of(
            "research", "r&d", "study", "clinical trial", "peer reviewed", "journal",
            "preprint", "whitepaper", "patent", "laboratory", "experiment", "methodology",
            "dataset", "findings", "innovation", "prototype"
    );

    private static final List<String> TRUSTED = List.of(
            "edu", "gov", "wikipedia.org", "arxiv.org", "nih.gov", "who.int", "nasa.gov",
            "isro.gov.in", "ieee.org", "nature.com", "pubmed.ncbi.nlm.nih.gov",
            "moneycontrol.com", "economictimes.indiatimes.com", "livemint.com", "github.com"
    );

    private static final List<String> LOW = List.of("pinterest.com", "quora.com", "scribd.com");

    private static final Map<String, List<String>> CATEGORY = Map.of(
            "IT", List.of("software", "computing", "artificial intelligence", "semiconductor", "cybersecurity"),
            "Medical", List.of("clinical", "biomedical", "pharma", "therapy", "diagnostics", "trial"),
            "Space", List.of("aerospace", "satellite", "orbital", "launch", "nasa", "isro"),
            "Share Market", List.of("equity research", "stock", "share", "nifty", "sensex", "ipo"),
            "Social Media", List.of("instagram", "tiktok", "whatsapp", "creator", "platform"),
            "Gaming", List.of("game", "esports", "console", "steam", "multiplayer"),
            "Astrology", List.of("horoscope", "zodiac", "vedic", "jyotish", "tarot", "vastu")
    );

    public Map<String, Object> score(Map<String, Object> page, List<String> categories, String thoughts, String query, int discoveryIndex) {
        String haystack = normalize(join(page, "title", "description", "snippet", "text"));
        String title = normalize(String.valueOf(page.getOrDefault("title", "")));
        List<String> thoughtTokens = tokenize(thoughts + " " + query);
        List<String> categoryTerms = new ArrayList<>();
        if (categories != null) {
            for (String c : categories) categoryTerms.addAll(CATEGORY.getOrDefault(c, List.of(c.toLowerCase(Locale.ROOT))));
        }
        Map<String, Double> breakdown = new HashMap<>();
        breakdown.put("thoughtOverlap", overlap(thoughtTokens, haystack) * WEIGHTS.get("thoughtOverlap"));
        breakdown.put("titleOverlap", overlap(thoughtTokens, title) * WEIGHTS.get("titleOverlap"));
        breakdown.put("categoryAffinity", categoryScore(categoryTerms, haystack) * WEIGHTS.get("categoryAffinity"));
        breakdown.put("researchSignal", researchScore(haystack) * WEIGHTS.get("researchSignal"));
        breakdown.put("indiaSignal", (IndiaSupport.indiaAffinity(host(String.valueOf(page.get("url")))) / 24.0) * WEIGHTS.get("indiaSignal"));
        breakdown.put("contentDepth", depth(page) * WEIGHTS.get("contentDepth"));
        breakdown.put("trustedDomain", domain(String.valueOf(page.get("url"))) * WEIGHTS.get("trustedDomain"));
        breakdown.put("discoveryRank", Math.max(0.2, 1 - discoveryIndex * 0.08) * WEIGHTS.get("discoveryRank"));

        double raw = breakdown.values().stream().mapToDouble(Double::doubleValue).sum();
        double max = WEIGHTS.values().stream().mapToDouble(Double::doubleValue).sum();
        int score = (int) Math.round(Math.min(100, Math.max(0, (raw / max) * 100)));
        String top = breakdown.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
        Map<String, Object> out = new HashMap<>();
        out.put("score", score);
        out.put("why", why(top, categories, score, String.valueOf(page.get("url"))));
        out.put("breakdown", breakdown);
        return out;
    }

    private static String why(String top, List<String> categories, int score, String url) {
        return switch (top) {
            case "thoughtOverlap" -> "Matches the language in your notes closely.";
            case "titleOverlap" -> "The page title lines up with what you wrote.";
            case "categoryAffinity" -> "Strong fit for " + (categories == null || categories.isEmpty() ? "your topics" : String.join(" / ", categories.subList(0, Math.min(2, categories.size())))) + ".";
            case "researchSignal" -> "Looks like R&D or research-grade material.";
            case "indiaSignal" -> "India-first source or India-focused coverage.";
            case "contentDepth" -> "Has enough depth for research reading.";
            case "trustedDomain" -> "Comes from a solid source (" + host(url) + ").";
            default -> score >= 70 ? "Strong R&D fit across your research pointers." : "Partial research match across your pointers.";
        };
    }

    private static double categoryScore(List<String> terms, String haystack) {
        if (terms.isEmpty()) return 0.35;
        long hits = terms.stream().filter(t -> haystack.contains(normalize(t))).count();
        return Math.min(1, hits / (double) Math.min(4, terms.size()));
    }

    private static double researchScore(String haystack) {
        long hits = RESEARCH_TERMS.stream().filter(haystack::contains).count();
        return Math.min(1, hits / 4.0);
    }

    private static double depth(Map<String, Object> page) {
        String text = String.valueOf(page.getOrDefault("text", page.getOrDefault("snippet", page.getOrDefault("description", ""))));
        int length = text.length();
        if (length > 2400) return 1;
        if (length > 1200) return 0.8;
        if (length > 500) return 0.55;
        if (length > 160) return 0.35;
        return 0.15;
    }

    private static double domain(String url) {
        String h = host(url).toLowerCase(Locale.ROOT);
        if (LOW.stream().anyMatch(h::contains)) return 0.05;
        if (TRUSTED.stream().anyMatch(d -> h.equals(d) || h.endsWith("." + d) || h.endsWith(d))) return 1;
        if (h.endsWith(".edu") || h.endsWith(".gov")) return 1;
        return 0.4;
    }

    private static double overlap(List<String> tokens, String text) {
        if (tokens.isEmpty() || text.isBlank()) return 0;
        long hits = tokens.stream().filter(text::contains).count();
        return Math.min(1, hits / (double) Math.min(tokens.size(), 10));
    }

    private static List<String> tokenize(String value) {
        return List.of(normalize(value).split(" ")).stream()
                .filter(t -> t.length() > 2).distinct().limit(24).collect(Collectors.toList());
    }

    private static String normalize(String value) {
        return String.valueOf(value).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s'-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String join(Map<String, Object> page, String... keys) {
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            Object v = page.get(k);
            if (v != null) sb.append(v).append(' ');
        }
        return sb.toString();
    }

    private static String host(String url) {
        try { return URI.create(url).getHost().replaceFirst("^www\\.", ""); }
        catch (Exception e) { return "this site"; }
    }
}
