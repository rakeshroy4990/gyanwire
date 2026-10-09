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
        if (used >= limit) {
            throw new PlanLimitException("Daily idea limit reached.", "LIMIT_REACHED", Map.of("upgradeUrl", "/pricing", "used", used, "limit", limit));
        }
        String url = String.valueOf(body.getOrDefault("url", "")).trim();
        String title = String.valueOf(body.getOrDefault("title", "News")).trim();
        String description = String.valueOf(body.getOrDefault("description", "")).trim();
        String industry = String.valueOf(body.getOrDefault("industry", "")).trim();
        boolean force = Boolean.TRUE.equals(body.get("force")) || "true".equalsIgnoreCase(String.valueOf(body.get("force")));
        String urlHash = DigestDeduper.urlHash(url);
        Map<String, Object> profile = profiles.ensureStarter(userId, industry);
        String persona = String.valueOf(profile.getOrDefault("persona", "working"));
        String key = userId + ":" + urlHash + ":" + PROMPT_VERSION + ":" + persona
                + ":" + profile.getOrDefault("incomeBand", "0")
                + ":" + profile.getOrDefault("hoursPerWeek", 10);
        UUID existing = force ? null : store.findIdeaRun(key);
        if (existing != null) {
            return Map.of("runId", existing.toString(), "ideas", store.listIdeas(userId), "cached", true);
        }
        Map<String, Object> signal = signalFor(userId, urlHash, title + "\n" + description, industry);
        List<PatternMatcher.Pattern> matched = patterns.match(String.valueOf(signal.getOrDefault("event_type", "other")));
        List<Map<String, Object>> drafts = draftIdeas(userId, title, description, industry, profile, signal, matched);
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
        return Map.of("runId", runId.toString(), "ideas", saved, "cached", false);
    }

    public List<Map<String, Object>> list(UUID userId) {
        return store.listIdeas(userId);
    }

    public void feedback(UUID userId, UUID ideaId, Integer feedback, Boolean tried) {
        store.feedbackIdea(userId, ideaId, feedback, tried);
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

    private List<Map<String, Object>> draftIdeas(
            UUID userId,
            String title,
            String description,
            String industry,
            Map<String, Object> profile,
            Map<String, Object> signal,
            List<PatternMatcher.Pattern> matched
    ) {
        String language = LanguageDetector.detect(title + description);
        try {
            String user = mapper.writeValueAsString(Map.of(
                    "language", language,
                    "news", Map.of("title", title, "description", description, "industry", industry),
                    "signal", signal,
                    "profile", profile,
                    "patterns", matched.stream().map(PatternMatcher.Pattern::id).toList()
            ));
            JsonNode node = llmClient.complete(userId, "idea", PROMPT_VERSION, LlmService.prompt("idea.v1.txt"), user);
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
            row.put("title", pattern.name() + " for " + industry);
            row.put("whyNow", "Why now: " + title);
            row.put("offer", pattern.ideaShape() + ". Education and tools only where the industry requires it.");
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
        double magnitude = signal.get("magnitude") instanceof Number n ? n.doubleValue() / 5.0 : 0.4;
        double evidence = signal.get("evidence_quality") instanceof Number n ? n.doubleValue() : 0.6;
        double fit = profile.get("skills") instanceof List<?> skills && !skills.isEmpty() ? 0.7 : 0.4;
        return new IdeaScorer.Inputs(magnitude, 0.6, 0.5, 0.5, fit, 0.1, 0.7, 0.2, 0.6, evidence);
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
