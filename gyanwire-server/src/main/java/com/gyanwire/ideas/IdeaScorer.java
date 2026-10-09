package com.gyanwire.ideas;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IdeaScorer {

    public record Inputs(
            double trendStrength,
            double urgency,
            double marketSize,
            double competitionGap,
            double profileFit,
            double capitalExcess,
            double speed,
            double regulatoryRisk,
            double novelty,
            double evidence
    ) {
    }

    private IdeaScorer() {
    }

    public static Result score(String persona, Inputs in) {
        double t = 0.18;
        double u = 0.12;
        double m = 0.14;
        double c = 0.14;
        double f = 0.22;
        double s = 0.10;
        double n = 0.10;
        String p = persona == null ? "working" : persona;
        switch (p) {
            case "student", "fresher" -> {
                f += 0.05;
                s += 0.05;
                m -= 0.05;
                c -= 0.05;
            }
            case "founder" -> {
                m += 0.06;
                c += 0.04;
                s -= 0.05;
                f -= 0.05;
            }
            case "working", "self_employed" -> {
                f += 0.04;
                s += 0.03;
                n -= 0.07;
            }
            default -> {
            }
        }
        double trend = clamp01(in.trendStrength());
        double urgency = clamp01(in.urgency());
        double market = clamp01(in.marketSize());
        double gap = clamp01(in.competitionGap());
        double fit = clamp01(in.profileFit());
        double speed = clamp01(in.speed());
        double novelty = clamp01(in.novelty());
        double evidence = clamp(0.5, 1, in.evidence());
        double capital = clamp01(in.capitalExcess());
        double risk = clamp01(in.regulatoryRisk());
        double base = t * trend + u * urgency + m * market + c * gap + f * fit + s * speed + n * novelty;
        double raw = 100 * evidence * base - 15 * capital - 10 * risk;
        int score = (int) Math.round(clamp(0, 100, raw));
        Map<String, Double> parts = new LinkedHashMap<>();
        parts.put("trend", t * trend);
        parts.put("urgency", u * urgency);
        parts.put("market", m * market);
        parts.put("competition", c * gap);
        parts.put("fit", f * fit);
        parts.put("speed", s * speed);
        parts.put("novelty", n * novelty);
        List<String> drivers = parts.entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<String, Double> e) -> e.getValue()).reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();
        return new Result(score, parts, new ArrayList<>(drivers));
    }

    public record Result(int score, Map<String, Double> parts, List<String> drivers) {
    }

    public static double clamp01(double value) {
        return clamp(0, 1, value);
    }

    public static double clamp(double min, double max, double value) {
        if (Double.isNaN(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
