package com.gyanwire.ideas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.config.PlanLimitException;
import com.gyanwire.digests.DigestDeduper;
import com.gyanwire.llm.LlmClient;
import com.gyanwire.persistence.FlowStore;
import com.gyanwire.profile.ProfileService;
import com.gyanwire.research.brief.LanguageDetector;
import com.gyanwire.research.engine.LlmService;
import com.gyanwire.usage.UsageService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IdeaService {

    static final String PROMPT_VERSION = "idea.v1";

    private final FlowStore store;
    private final ProfileService profiles;
    private final UsageService usage;
    private final LlmClient llmClient;
    private final LlmService llmService;
    private final PatternMatcher patterns;
    private final ObjectMapper mapper;

    public IdeaService(
            FlowStore store,
            ProfileService profiles,
            UsageService usage,
            LlmClient llmClient,
            LlmService llmService,
            PatternMatcher patterns,
            ObjectMapper mapper
    ) {
        this.store = store;
        this.profiles = profiles;
        this.usage = usage;
        this.llmClient = llmClient;
        this.llmService = llmService;
        this.patterns = patterns;
        this.mapper = mapper;
    }

    public Map<String, Object> fromNews(UUID userId, Map<String, Object> body) {
        Map<String, Object> plan = usage.resolveUserPlan(userId);
        int limit = ((Number) plan.getOrDefault("dailyIdeaLimit", 3)).intValue();
        long used = usage.countToday(userId, null, "idea");
        if (limit > 0 && limit < UsageService.UNLIMITED && used >= limit) {
            throw new PlanLimitException("Daily idea limit reached.", "LIMIT_REACHED", Map.of("upgradeUrl", "/pricing", "used", used, "limit", limit));
        }
        String url = String.valueOf(body.getOrDefault("url", "")).trim();
        String title = String.valueOf(body.getOrDefault("title", "News")).trim();
        String description = String.valueOf(body.getOrDefault("description", "")).trim();
        String industry = String.valueOf(body.getOrDefault("industry", "")).trim();
        String signalType = String.valueOf(body.getOrDefault("signalType", "")).trim();
        String whyIdea = String.valueOf(body.getOrDefault("whyIdea", "")).trim();
        if ("null".equals(signalType)) {
            signalType = "";
        }
        if ("null".equals(whyIdea)) {
            whyIdea = "";
        }
        if (!whyIdea.isBlank()) {
            description = whyIdea + "\n" + description;
        }
        boolean force = Boolean.TRUE.equals(body.get("force")) || "true".equalsIgnoreCase(String.valueOf(body.get("force")));
        String urlHash = DigestDeduper.urlHash(url);
        Map<String, Object> profile = profiles.ensureStarter(userId, industry);
        String persona = String.valueOf(profile.getOrDefault("persona", "working"));
        String key = userId + ":" + urlHash + ":" + PROMPT_VERSION + ":" + persona
                + ":" + profile.getOrDefault("incomeBand", "0")
                + ":" + profile.getOrDefault("hoursPerWeek", 10);
        List<String> formats = formatsForNews(title, description, industry);
        UUID existing = force ? null : store.findIdeaRun(key);
        if (existing != null) {
            List<Map<String, Object>> cached = IdeaVariety.diversify(
                    new ArrayList<>(store.listIdeasForRun(userId, existing)), title, industry, formats);
            persistVariety(userId, existing, title, industry, profile, cached);
            cached.sort((a, b) -> Integer.compare(ideaScore(b), ideaScore(a)));
            return Map.of("runId", existing.toString(), "ideas", cached, "cached", true);
        }
        Map<String, Object> signal = signalFor(userId, urlHash, title + "\n" + description, industry);
        if (!signalType.isBlank()) {
            signal.put("event_type", eventForNews(signalType));
            signal.put("news_signal", signalType);
        }
        if (!whyIdea.isBlank()) {
            signal.put("why_idea", whyIdea);
            signal.put("new_capability", whyIdea);
        }
        List<PatternMatcher.Pattern> matched = patterns.match(String.valueOf(signal.getOrDefault("event_type", "other")));
        List<Map<String, Object>> drafts = IdeaVariety.diversify(
                draftIdeas(userId, title, description, industry, profile, signal, matched, formats),
                title, industry, formats);
        UUID runId = UUID.randomUUID();
        try {
            store.insertIdeaRun(runId, userId, url, urlHash, title, industry, mapper.writeValueAsString(signal), PROMPT_VERSION, key);
        } catch (Exception e) {
            store.insertIdeaRun(runId, userId, url, urlHash, title, industry, "{}", PROMPT_VERSION, key);
        }
        List<Map<String, Object>> saved = new ArrayList<>();
        int capitalAvailable = FitFilter.capitalCeiling(String.valueOf(profile.getOrDefault("capitalBand", "0")));
        boolean raised = FitFilter.capitalRaised(persona, String.valueOf(profile.get("capitalBand")));
        int hoursAvailable = profile.get("hoursPerWeek") instanceof Number n ? n.intValue() : 10;
        for (Map<String, Object> draft : drafts) {
            draft.remove("_formatRewritten");
            String text = String.valueOf(draft.getOrDefault("offer", "")) + " " + draft.getOrDefault("whyNow", "") + " " + draft.getOrDefault("title", "");
            int capitalNeeded = draft.get("capitalNeededInr") instanceof Number c ? c.intValue() : 0;
            int hoursNeeded = draft.get("hoursPerWeek") instanceof Number h ? h.intValue() : 5;
            boolean legal = Boolean.TRUE.equals(draft.get("legalityFlag"));
            FitFilter.Decision decision = FitFilter.decide(industry, persona, text, capitalNeeded, capitalAvailable, raised, hoursNeeded, hoursAvailable, legal);
            if (!decision.keep()) {
                continue;
            }
            IdeaScorer.Result scored = IdeaScorer.score(persona, inputs(signal, profile, draft));
            UUID ideaId = UUID.randomUUID();
            Map<String, Object> bodyJson = new LinkedHashMap<>(draft);
            bodyJson.put("drivers", scored.drivers());
            bodyJson.put("disclaimer", disclaimer(industry));
            try {
                store.insertIdea(
                        ideaId, runId, userId, scored.score(), mapper.writeValueAsString(scored.parts()),
                        String.valueOf(draft.get("title")), String.valueOf(draft.get("whyNow")),
                        mapper.writeValueAsString(bodyJson),
                        matched.stream().map(PatternMatcher.Pattern::id).toArray(String[]::new));
            } catch (Exception ignored) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>(bodyJson);
            row.put("id", ideaId.toString());
            row.put("score", scored.score());
            row.put("drivers", scored.drivers());
            saved.add(row);
        }
        usage.recordUsage(userId, null, "idea", java.math.BigDecimal.ZERO);
        saved.sort((a, b) -> Integer.compare(ideaScore(b), ideaScore(a)));
        return Map.of("runId", runId.toString(), "ideas", saved, "cached", false);
    }

    private static int ideaScore(Map<String, Object> idea) {
        Object value = idea == null ? null : idea.get("score");
        return value instanceof Number n ? n.intValue() : 0;
    }

    /**
     * Writes format rewrites back onto a cached run so a later plan uses the varied title.
     */
    private void persistVariety(
            UUID userId,
            UUID runId,
            String newsTitle,
            String industry,
            Map<String, Object> profile,
            List<Map<String, Object>> ideas
    ) {
        Map<String, Object> signal = SignalSchemas.fallback(newsTitle, industry);
        List<PatternMatcher.Pattern> matched = patterns.match(String.valueOf(signal.getOrDefault("event_type", "other")));
        String[] patternIds = matched.stream().map(PatternMatcher.Pattern::id).toArray(String[]::new);
        for (Map<String, Object> row : ideas) {
            boolean rewritten = Boolean.TRUE.equals(row.remove("_formatRewritten"));
            Object id = row.get("id");
            if (id == null || String.valueOf(id).isBlank()) {
                insertVariedIdea(userId, runId, industry, profile, signal, row, patternIds);
                continue;
            }
            if (!rewritten) {
                continue;
            }
            IdeaScorer.Result scored = IdeaScorer.score(String.valueOf(profile.getOrDefault("persona", "working")), inputs(signal, profile, row));
            row.put("score", scored.score());
            row.put("drivers", scored.drivers());
            row.putIfAbsent("disclaimer", disclaimer(industry));
            String why = String.valueOf(row.getOrDefault("whyNow", row.getOrDefault("why", "")));
            row.put("why", why);
            try {
                store.updateIdeaContent(
                        userId, UUID.fromString(String.valueOf(id)), scored.score(),
                        mapper.writeValueAsString(scored.parts()),
                        String.valueOf(row.get("title")), why, bodyJson(row));
            } catch (Exception ignored) {
                // The response still shows the varied idea if the write fails.
            }
        }
    }

    private void insertVariedIdea(
            UUID userId,
            UUID runId,
            String industry,
            Map<String, Object> profile,
            Map<String, Object> signal,
            Map<String, Object> row,
            String[] patternIds
    ) {
        IdeaScorer.Result scored = IdeaScorer.score(String.valueOf(profile.getOrDefault("persona", "working")), inputs(signal, profile, row));
        UUID ideaId = UUID.randomUUID();
        row.put("drivers", scored.drivers());
        row.put("disclaimer", disclaimer(industry));
        row.put("score", scored.score());
        String why = String.valueOf(row.getOrDefault("whyNow", row.getOrDefault("why", "")));
        row.put("why", why);
        try {
            store.insertIdea(
                    ideaId, runId, userId, scored.score(), mapper.writeValueAsString(scored.parts()),
                    String.valueOf(row.get("title")), why, bodyJson(row), patternIds);
            row.put("id", ideaId.toString());
        } catch (Exception ignored) {
            row.remove("id");
        }
    }

    private String bodyJson(Map<String, Object> row) throws Exception {
        Map<String, Object> copy = new LinkedHashMap<>(row);
        copy.remove("id");
        copy.remove("score");
        copy.remove("why");
        copy.remove("_formatRewritten");
        return mapper.writeValueAsString(copy);
    }

    public List<Map<String, Object>> list(UUID userId) {
        return store.listIdeas(userId);
    }

    public void feedback(UUID userId, UUID ideaId, Integer feedback, Boolean tried) {
        store.feedbackIdea(userId, ideaId, feedback, tried);
    }

    private static String eventForNews(String signalType) {
        return switch (signalType) {
            case "launch" -> "tech_release";
            case "funding" -> "funding";
            case "approval", "regulation" -> "regulation";
            case "pricing" -> "price_move";
            case "shortage" -> "supply_disruption";
            default -> "other";
        };
    }

    private Map<String, Object> signalFor(UUID userId, String urlHash, String text, String industry) {
        String cached = null;
        try {
            cached = store.findSignal(urlHash);
        } catch (Exception ignored) {
            cached = null;
        }
        if (cached != null) {
            try {
                return mapper.readValue(cached, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            } catch (Exception ignored) {
                // fall through
            }
        }
        JsonNode node = llmService.completeValidated(
                userId, "signal.v1", "signal", LlmService.prompt("signal.v1.txt"),
                "Industry: " + industry + "\nText:\n" + text,
                SignalSchemas::validate);
        Map<String, Object> signal = node == null ? SignalSchemas.fallback(text, industry) : mapper.convertValue(node, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        signal.put("industry", industry);
        try {
            store.saveSignal(urlHash, mapper.writeValueAsString(signal));
        } catch (Exception ignored) {
            // cache is best effort
        }
        return signal;
    }

    /**
     * Same inputs the findings list uses, so the badge and this page offer the same count.
     */
    private List<String> formatsForNews(String title, String description, String industry) {
        FindingIdeaPotential.Result potential = FindingIdeaPotential.score(
                title, description, industry, "working", patterns.all());
        return IdeaVariety.formatsFor(
                potential.eventType(),
                patterns.shapesFor(potential.patternIds()),
                title + "\n" + description,
                potential.score());
    }

    private List<Map<String, Object>> draftIdeas(
            UUID userId,
            String title,
            String description,
            String industry,
            Map<String, Object> profile,
            Map<String, Object> signal,
            List<PatternMatcher.Pattern> matched,
            List<String> formats
    ) {
        String language = LanguageDetector.detect(title + description);
        try {
            String user = mapper.writeValueAsString(Map.of(
                    "language", language,
                    "news", Map.of("title", title, "description", description, "industry", industry),
                    "signal", signal,
                    "profile", profile,
                    "formats", formats.stream().map(kind -> Map.of(
                            "id", kind,
                            "label", IdeaVariety.formatLabel(kind)
                    )).toList(),
                    "patterns", matched.stream().map(pattern -> Map.of(
                            "id", pattern.id(),
                            "name", pattern.name(),
                            "ideaShape", pattern.ideaShape()
                    )).toList()
            ));
            JsonNode node = llmService.completeValidated(
                    userId,
                    PROMPT_VERSION,
                    "idea",
                    LlmService.prompt("idea.v1.txt"),
                    user,
                    json -> json != null && json.path("ideas").isArray() && !json.path("ideas").isEmpty()
                            ? null
                            : "ideas required"
            );
            if (node != null && node.path("ideas").isArray() && !node.path("ideas").isEmpty()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (JsonNode idea : node.path("ideas")) {
                    Map<String, Object> row = mapper.convertValue(idea, new com.fasterxml.jackson.core.type.TypeReference<>() {});
                    row.putIfAbsent("whyNow", idea.path("why_now").asText(title));
                    row.putIfAbsent("capitalNeededInr", idea.path("capital_needed_inr").asInt(0));
                    row.putIfAbsent("hoursPerWeek", idea.path("hours_per_week").asInt(5));
                    rows.add(row);
                }
                return rows;
            }
        } catch (Exception ignored) {
            // template below
        }
        List<Map<String, Object>> fallback = new ArrayList<>();
        List<PatternMatcher.Pattern> use = matched.isEmpty()
                ? List.of(new PatternMatcher.Pattern("P6", "Consumer shift", List.of("other"), "A small service"))
                : matched;
        for (PatternMatcher.Pattern pattern : use) {
            Map<String, Object> row = new LinkedHashMap<>();
            String whyIdea = String.valueOf(signal.getOrDefault("why_idea", "")).trim();
            if ("null".equals(whyIdea)) {
                whyIdea = "";
            }
            row.put("title", pattern.name() + " for " + industry);
            row.put("whyNow", whyIdea.isBlank() ? "Why now: " + title : whyIdea);
            row.put("offer", whyIdea.isBlank()
                    ? pattern.ideaShape() + ". Education and tools only where the industry requires it."
                    : whyIdea);
            row.put("segment", "People already following this news");
            row.put("capitalNeededInr", 0);
            row.put("hoursPerWeek", 5);
            row.put("confidence", 0.5);
            row.put("confidenceNote", "estimate");
            fallback.add(row);
        }
        return fallback;
    }

    private static IdeaScorer.Inputs inputs(Map<String, Object> signal, Map<String, Object> profile, Map<String, Object> draft) {
        String industry = String.valueOf(signal.getOrDefault("industry", profile.getOrDefault("industry", "")));
        String persona = String.valueOf(profile.getOrDefault("persona", "working"));
        String ideaText = String.valueOf(draft.getOrDefault("title", "")) + " "
                + draft.getOrDefault("offer", "") + " "
                + draft.getOrDefault("whyNow", "") + " "
                + draft.getOrDefault("business_model", "");

        double magnitude = signal.get("magnitude") instanceof Number n ? n.doubleValue() / 5.0 : 0.4;
        double evidence = signal.get("evidence_quality") instanceof Number n ? n.doubleValue() : 0.6;
        if (draft.get("confidence") instanceof Number c) {
            evidence = IdeaScorer.clamp(0.5, 1.0, 0.55 * evidence + 0.45 * c.doubleValue());
        }

        int horizon = signal.get("time_horizon_days") instanceof Number n ? n.intValue() : 90;
        double urgency = IdeaScorer.clamp01(horizon <= 30 ? 0.85 : horizon <= 90 ? 0.65 : 0.45);
        String event = String.valueOf(signal.getOrDefault("event_type", "other"));
        if (List.of("regulation", "price_move", "govt_scheme", "supply_disruption").contains(event)) {
            urgency = IdeaScorer.clamp01(urgency + 0.1);
        }

        double market = num01(draft, "market_size", "marketSize", defaultMarket(event));
        double competition = num01(draft, "competition_gap", "competitionGap", 0.5);
        double novelty = signal.get("new_capability") != null && !String.valueOf(signal.get("new_capability")).isBlank()
                ? 0.75
                : (List.of("tech_release", "data_release", "infrastructure").contains(event) ? 0.65 : 0.45);

        double fit = profile.get("skills") instanceof List<?> skills && !skills.isEmpty() ? 0.65 : 0.4;
        String skillsBlob = profile.get("skills") instanceof List<?> skills
                ? skills.stream().map(String::valueOf).reduce("", (a, b) -> a + " " + b).toLowerCase()
                : "";
        if (!skillsBlob.isBlank()) {
            String hay = ideaText.toLowerCase();
            long hits = java.util.Arrays.stream(skillsBlob.trim().split("\\s+"))
                    .filter(s -> s.length() > 2 && hay.contains(s))
                    .count();
            fit = IdeaScorer.clamp01(fit + Math.min(0.25, hits * 0.08));
        }
        if (ideaText.toLowerCase().contains("education") || ideaText.toLowerCase().contains("tool")) {
            fit = IdeaScorer.clamp01(fit + 0.08);
        }

        int capitalNeeded = draft.get("capitalNeededInr") instanceof Number c ? c.intValue()
                : draft.get("capital_needed_inr") instanceof Number c2 ? c2.intValue() : 0;
        int capitalAvailable = FitFilter.capitalCeiling(String.valueOf(profile.getOrDefault("capitalBand", "0")));
        double capitalExcess = capitalAvailable <= 0
                ? (capitalNeeded <= 0 ? 0.05 : 0.45)
                : IdeaScorer.clamp01((double) capitalNeeded / Math.max(capitalAvailable, 1) / 3.0);

        int hoursNeeded = draft.get("hoursPerWeek") instanceof Number h ? h.intValue()
                : draft.get("hours_per_week") instanceof Number h2 ? h2.intValue() : 5;
        int hoursAvailable = profile.get("hoursPerWeek") instanceof Number n ? n.intValue() : 10;
        double speed = IdeaScorer.clamp01(1.0 - (hoursNeeded / (double) Math.max(hoursAvailable + 5, 1)) * 0.5);
        if (hoursNeeded <= 5) {
            speed = IdeaScorer.clamp01(speed + 0.15);
        }

        double risk = num01(draft, "regulatory_risk", "regulatoryRisk",
                "Share Market".equals(industry) || "Medical".equals(industry) ? 0.35 : 0.15);
        if (Boolean.TRUE.equals(draft.get("disclaimer")) || "Share Market".equals(industry)) {
            risk = IdeaScorer.clamp01(risk);
        }

        // Stronger business drafts from stronger news rise: magnitude tracks signal, market/competition from idea.
        return new IdeaScorer.Inputs(
                magnitude, urgency, market, competition, fit, capitalExcess, speed, risk, novelty, evidence);
    }

    private static double defaultMarket(String event) {
        return switch (event) {
            case "funding", "consumer_trend", "skills_gap", "govt_scheme" -> 0.7;
            case "tech_release", "infrastructure", "data_release" -> 0.6;
            case "regulation" -> 0.55;
            default -> 0.45;
        };
    }

    private static double num01(Map<String, Object> draft, String snake, String camel, double fallback) {
        Object value = draft.get(snake);
        if (!(value instanceof Number)) {
            value = draft.get(camel);
        }
        if (value instanceof Number n) {
            return IdeaScorer.clamp01(n.doubleValue());
        }
        return fallback;
    }

    private static String disclaimer(String industry) {
        return switch (industry) {
            case "Share Market" -> "Informational only. Not investment advice.";
            case "Medical" -> "Research aid, not a diagnosis or treatment.";
            case "Astrology", "Gaming" -> "Ideas here are cultural or entertainment context, not predictions.";
            default -> "";
        };
    }
}
