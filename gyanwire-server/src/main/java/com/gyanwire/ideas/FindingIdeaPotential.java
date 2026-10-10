package com.gyanwire.ideas;

import com.gyanwire.research.engine.CuriosityRank;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ranks a news finding by how strong a business idea it can support — not by
 * research-pointer overlap alone. Uses the same IdeaScorer formula as Get an Idea.
 */
public final class FindingIdeaPotential {

    public record Result(int score, List<String> drivers, String why, String eventType, List<String> patternIds) {
    }

    private FindingIdeaPotential() {
    }

    public static Result score(
            String title,
            String description,
            String industry,
            String persona,
            List<PatternMatcher.Pattern> library
    ) {
        String blob = normalize(title) + " " + normalize(description);
        Map<String, Object> signal = SignalSchemas.fallback(title + "\n" + description, industry);
        String eventType = String.valueOf(signal.getOrDefault("event_type", "other"));
        List<PatternMatcher.Pattern> matched = matchPatterns(eventType, library);

        if (shouldDrop(industry, blob)) {
            return new Result(0, List.of(), "", eventType, List.of());
        }

        double magnitude = signal.get("magnitude") instanceof Number n ? n.doubleValue() / 5.0 : 0.4;
        double evidence = signal.get("evidence_quality") instanceof Number n ? n.doubleValue() : 0.4;
        evidence = IdeaScorer.clamp(0.5, 1.0, evidence + evidenceBoost(blob));

        double urgency = urgencyFor(eventType, blob);
        double market = marketFor(eventType, blob);
        double competition = competitionFor(blob);
        double fit = fitFor(industry, persona, blob);
        double capital = capitalDrag(blob);
        double speed = speedFor(matched, blob);
        double risk = regulatoryRisk(industry, blob);
        double novelty = noveltyFor(eventType, blob);

        // Stronger pattern + actionable idea language lift the combination; generic wraps crush it.
        double patternLift = Math.min(0.24, matched.size() * 0.08);
        double hook = CuriosityRank.hookScore(blob);
        double generic = CuriosityRank.genericScore(blob);
        boolean actionable = containsAny(blob,
                "education", "course", "training", "tool", "platform", "checklist", "tracker", "saas", "freelance");
        if (actionable) {
            magnitude = IdeaScorer.clamp01(magnitude + 0.12);
            fit = IdeaScorer.clamp01(fit + 0.12);
            evidence = IdeaScorer.clamp(0.5, 1.0, evidence + 0.1);
        }
        magnitude = IdeaScorer.clamp01(magnitude + patternLift + 0.12 * hook - 0.25 * generic);
        novelty = IdeaScorer.clamp01(novelty + 0.15 * hook - 0.2 * generic);
        urgency = IdeaScorer.clamp01(urgency + 0.1 * hook);

        IdeaScorer.Result scored = IdeaScorer.score(
                persona == null || persona.isBlank() ? "working" : persona,
                new IdeaScorer.Inputs(magnitude, urgency, market, competition, fit, capital, speed, risk, novelty, evidence)
        );

        String why = whyLine(scored.score(), matched, eventType, industry, generic);
        List<String> patternIds = matched.stream().map(PatternMatcher.Pattern::id).toList();
        return new Result(scored.score(), new ArrayList<>(scored.drivers()), why, eventType, patternIds);
    }

    private static boolean shouldDrop(String industry, String blob) {
        if (CuriosityRank.looksGeneric(blob)) {
            return true;
        }
        FitFilter.Decision decision = FitFilter.decide(
                industry, "working", blob + " education tool course", 0, 25_000, true, 5, 10, false);
        // Share Market / Medical must still look actionable as education/tools — bare price news drops.
        if ("Share Market".equals(industry) && !containsAny(blob,
                "education", "course", "learn", "tool", "tracker", "explainer", "training",
                "sebi", "regulation", "circular", "compliance", "broker", "fintech", "app", "platform",
                "scheme", "tax", "ipo", "mutual fund", "sip", "investor education")) {
            return true;
        }
        if ("Medical".equals(industry) && !containsAny(blob,
                "education", "admin", "logistics", "records", "training", "clinic", "hospital ops",
                "tele", "device", "software", "platform", "app")) {
            return true;
        }
        return !decision.keep() && "investment-advice".equals(decision.reason());
    }

    private static double evidenceBoost(String blob) {
        int len = blob.length();
        if (len > 400) return 0.15;
        if (len > 180) return 0.08;
        return 0;
    }

    private static double urgencyFor(String eventType, String blob) {
        double base = switch (eventType) {
            case "regulation", "price_move", "supply_disruption", "govt_scheme" -> 0.75;
            case "funding", "tech_release", "skills_gap" -> 0.65;
            case "consumer_trend", "event_season" -> 0.55;
            default -> 0.45;
        };
        if (containsAny(blob, "today", "this week", "deadline", "from monday", "immediate", "urgent")) {
            base += 0.15;
        }
        return IdeaScorer.clamp01(base);
    }

    private static double marketFor(String eventType, String blob) {
        double base = switch (eventType) {
            case "funding", "consumer_trend", "skills_gap", "govt_scheme" -> 0.7;
            case "infrastructure", "tech_release", "data_release" -> 0.6;
            case "regulation" -> 0.55;
            default -> 0.45;
        };
        if (containsAny(blob, "million", "crore", "users", "demand", "shortage", "india", "sme", "startup")) {
            base += 0.12;
        }
        return IdeaScorer.clamp01(base);
    }

    private static double competitionFor(String blob) {
        double gap = 0.45;
        if (containsAny(blob, "gap", "lack", "shortage", "underserved", "missing", "no tool", "still manual")) {
            gap += 0.25;
        }
        if (containsAny(blob, "crowded", "saturated", "many apps", "already dominated")) {
            gap -= 0.2;
        }
        return IdeaScorer.clamp01(gap);
    }

    private static double fitFor(String industry, String persona, String blob) {
        double fit = "founder".equals(persona) ? 0.55 : 0.5;
        if (containsAny(blob, "education", "course", "training", "checklist", "template", "tool", "saas", "freelance", "service")) {
            fit += 0.2;
        }
        if ("student".equals(persona) || "fresher".equals(persona)) {
            fit += containsAny(blob, "learn", "beginner", "course", "internship") ? 0.1 : -0.05;
        }
        if ("Share Market".equals(industry) && containsAny(blob, "education", "explainer", "tracker", "tool")) {
            fit += 0.1;
        }
        return IdeaScorer.clamp01(fit);
    }

    private static double capitalDrag(String blob) {
        if (containsAny(blob, "education", "course", "newsletter", "template", "checklist", "freelance", "no-code")) {
            return 0.05;
        }
        if (containsAny(blob, "factory", "warehouse", "hardware", "clinic build", "manufacturing")) {
            return 0.55;
        }
        return 0.2;
    }

    private static double speedFor(List<PatternMatcher.Pattern> matched, String blob) {
        double speed = 0.55;
        for (PatternMatcher.Pattern pattern : matched) {
            String shape = normalize(pattern.ideaShape());
            if (shape.contains("training") || shape.contains("checklist") || shape.contains("education") || shape.contains("tool")) {
                speed += 0.12;
            }
        }
        if (containsAny(blob, "weekend", "side", "remote", "online", "template")) {
            speed += 0.1;
        }
        return IdeaScorer.clamp01(speed);
    }

    private static double regulatoryRisk(String industry, String blob) {
        if ("Share Market".equals(industry)) {
            return containsAny(blob, "education", "explainer", "tool", "tracker") ? 0.25 : 0.55;
        }
        if ("Medical".equals(industry)) {
            return containsAny(blob, "education", "admin", "logistics") ? 0.3 : 0.6;
        }
        if ("Gaming".equals(industry) || "Astrology".equals(industry)) {
            return 0.25;
        }
        return 0.15;
    }

    private static double noveltyFor(String eventType, String blob) {
        double n = switch (eventType) {
            case "tech_release", "data_release", "infrastructure" -> 0.7;
            case "funding", "exit_failure" -> 0.6;
            case "regulation", "govt_scheme" -> 0.5;
            default -> 0.4;
        };
        if (containsAny(blob, "first", "launch", "new", "breakthrough", "open source", "pilot")) {
            n += 0.15;
        }
        return IdeaScorer.clamp01(n);
    }

    private static String whyLine(
            int score,
            List<PatternMatcher.Pattern> matched,
            String eventType,
            String industry,
            double generic
    ) {
        if (generic >= 0.5) {
            return "Routine wrap — weak business-idea signal.";
        }
        if (!matched.isEmpty()) {
            PatternMatcher.Pattern top = matched.get(0);
            return "Idea fit " + score + "% · " + top.name() + " → " + top.ideaShape()
                    + ("Share Market".equals(industry) ? " (education/tools only)." : ".");
        }
        return "Idea fit " + score + "% from " + eventType.replace('_', ' ') + " news.";
    }

    private static List<PatternMatcher.Pattern> matchPatterns(String eventType, List<PatternMatcher.Pattern> library) {
        if (library == null || library.isEmpty()) {
            return List.of();
        }
        List<PatternMatcher.Pattern> hits = new ArrayList<>();
        for (PatternMatcher.Pattern pattern : library) {
            if (pattern.eventTypes().contains(eventType)) {
                hits.add(pattern);
            }
        }
        if (hits.isEmpty()) {
            hits.add(library.get(0));
        }
        return hits.stream().limit(3).toList();
    }

    private static boolean containsAny(String blob, String... needles) {
        for (String needle : needles) {
            if (blob.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return String.valueOf(value == null ? "" : value).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s'-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
