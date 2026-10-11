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
import com.gyanwire.ideas.pipeline.AngleType;
import com.gyanwire.ideas.pipeline.IdeaPipelineProperties;
import com.gyanwire.ideas.pipeline.OpportunityPipeline;
import com.gyanwire.ideas.pipeline.SocialMediaIdea;
import com.gyanwire.news.SignalRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class IdeaService {

    private static final Logger log = LoggerFactory.getLogger(IdeaService.class);

    static final String PROMPT_VERSION = "idea.v2";

    private final FlowStore store;
    private final ProfileService profiles;
    private final UsageService usage;
    private final LlmClient llmClient;
    private final LlmService llmService;
    private final PatternMatcher patterns;
    private final ObjectMapper mapper;
    private final IdeaPipelineProperties pipelineProperties;
    private final OpportunityPipeline opportunityPipeline;

    public IdeaService(
            FlowStore store,
            ProfileService profiles,
            UsageService usage,
            LlmClient llmClient,
            LlmService llmService,
            PatternMatcher patterns,
            ObjectMapper mapper,
            IdeaPipelineProperties pipelineProperties,
            OpportunityPipeline opportunityPipeline
    ) {
        this.store = store;
        this.profiles = profiles;
        this.usage = usage;
        this.llmClient = llmClient;
        this.llmService = llmService;
        this.patterns = patterns;
        this.mapper = mapper;
        this.pipelineProperties = pipelineProperties;
        this.opportunityPipeline = opportunityPipeline;
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
        Map<String, Object> profile = profiles.ensureStarter(userId, industry);
        if (isFyiNews(signalType, title, description)) {
            log.info("ideas.fromNews path=fyi title={}", shortTitle(title));
            return fyiResponse();
        }
        String urlHash = DigestDeduper.urlHash(url);
        if (pipelineProperties.isPipelineEnabled()) {
            log.info("ideas.fromNews path=pipeline force={} title={}", force, shortTitle(title));
            return opportunityPipeline.fromNews(userId, body, profile);
        }
        String persona = String.valueOf(profile.getOrDefault("persona", "working"));
        String key = userId + ":" + urlHash + ":" + PROMPT_VERSION + ":" + persona
                + ":" + profile.getOrDefault("incomeBand", "0")
                + ":" + profile.getOrDefault("hoursPerWeek", 10);
        List<String> formats = formatsForNews(title, description, industry);
        UUID existing = force ? null : store.findIdeaRun(key);
        if (existing != null) {
            List<Map<String, Object>> cached = withSocialIdea(
                    userId, existing, title, description, industry,
                    dedupeByPatternId(new ArrayList<>(store.listIdeasForRun(userId, existing))));
            cached.sort((a, b) -> Integer.compare(ideaScore(b), ideaScore(a)));
            log.info(
                    "ideas.fromNews path=classic cached=true runId={} ideas={} title={}",
                    existing, cached.size(), shortTitle(title));
            return response(existing, cached, true, null, null);
        }
        log.info(
                "ideas.fromNews path=classic cached=false force={} title={}",
                force, shortTitle(title));
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
        DraftResult drafted = draftIdeas(userId, title, description, industry, profile, signal, matched, formats);
        List<Map<String, Object>> drafts = dedupeByPatternId(drafted.ideas());
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
        Set<String> knownPatternIds = new LinkedHashSet<>();
        for (PatternMatcher.Pattern pattern : matched) {
            knownPatternIds.add(pattern.id());
        }
        for (Map<String, Object> draft : drafts) {
            String text = String.valueOf(draft.getOrDefault("offer", "")) + " " + draft.getOrDefault("whyNow", "") + " " + draft.getOrDefault("title", "");
            int capitalNeeded = draft.get("capitalNeededInr") instanceof Number c ? c.intValue() : 0;
            int hoursNeeded = draft.get("hoursPerWeek") instanceof Number h ? h.intValue() : 5;
            boolean legal = Boolean.TRUE.equals(draft.get("legalityFlag"));
            FitFilter.Decision decision = FitFilter.decide(industry, persona, text, capitalNeeded, capitalAvailable, raised, hoursNeeded, hoursAvailable, legal);
            if (!decision.keep()) {
                log.info(
                        "ideas.fromNews FitFilter drop reason={} title={}",
                        decision.reason(), shortTitle(String.valueOf(draft.getOrDefault("title", ""))));
                continue;
            }
            IdeaScorer.Result scored = IdeaScorer.score(persona, inputs(signal, profile, draft));
            UUID ideaId = UUID.randomUUID();
            Map<String, Object> bodyJson = new LinkedHashMap<>(draft);
            bodyJson.put("drivers", scored.drivers());
            bodyJson.put("disclaimer", disclaimer(industry));
            String[] patternIds = patternIdsForInsert(draft, knownPatternIds, matched);
            try {
                store.insertIdea(
                        ideaId, runId, userId, scored.score(), mapper.writeValueAsString(scored.parts()),
                        String.valueOf(draft.get("title")), String.valueOf(draft.get("whyNow")),
                        mapper.writeValueAsString(bodyJson),
                        patternIds);
            } catch (Exception ex) {
                log.warn(
                        "ideas.fromNews insertIdea failed title={} error={}",
                        shortTitle(String.valueOf(draft.getOrDefault("title", ""))),
                        shortText(ex.getMessage(), 160));
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>(bodyJson);
            row.put("id", ideaId.toString());
            row.put("score", scored.score());
            row.put("drivers", scored.drivers());
            saved.add(row);
        }
        usage.recordUsage(userId, null, "idea", java.math.BigDecimal.ZERO);
        List<Map<String, Object>> withSocial = withSocialIdea(userId, runId, title, description, industry, saved);
        withSocial.sort((a, b) -> Integer.compare(ideaScore(b), ideaScore(a)));
        log.info(
                "ideas.fromNews path=classic cached=false runId={} ideas={} formats={} title={}",
                runId, withSocial.size(), formats.size(), shortTitle(title));
        return response(runId, withSocial, false, null, drafted.raw());
    }

    /**
     * Classic path has no angle pipeline. Always put a social media idea first so
     * Share Market and other industries still get YouTube / Instagram / LinkedIn.
     */
    private List<Map<String, Object>> withSocialIdea(
            UUID userId,
            UUID runId,
            String title,
            String description,
            String industry,
            List<Map<String, Object>> ideas
    ) {
        List<Map<String, Object>> out = ideas == null ? new ArrayList<>() : new ArrayList<>(ideas);
        if (SocialMediaIdea.presentRows(out)) {
            return out;
        }
        Map<String, Object> social = SocialMediaIdea.asRow(title, description, industry);
        UUID ideaId = UUID.randomUUID();
        try {
            store.insertIdea(
                    ideaId,
                    runId,
                    userId,
                    ideaScore(social),
                    "{}",
                    String.valueOf(social.get("title")),
                    String.valueOf(social.get("whyNow")),
                    mapper.writeValueAsString(social),
                    new String[]{AngleType.comparison_content.name()});
            social.put("id", ideaId.toString());
            out.add(0, social);
        } catch (Exception ex) {
            log.warn(
                    "ideas.fromNews social insert failed title={} error={}",
                    shortTitle(title), shortText(ex.getMessage(), 160));
            social.put("id", ideaId.toString());
            out.add(0, social);
        }
        return out;
    }

    private Map<String, Object> response(
            UUID runId,
            List<Map<String, Object>> ideas,
            boolean cached,
            String emptyReason,
            Object rawLlm
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("runId", runId == null ? "" : runId.toString());
        body.put("ideas", ideas);
        body.put("cached", cached);
        if (emptyReason != null && !emptyReason.isBlank()) {
            body.put("emptyReason", emptyReason);
        }
        if (rawLlm != null) {
            body.put("rawLlm", rawLlm);
        }
        return body;
    }

    /**
     * Keep the first idea for each pattern_id. Ideas without a pattern_id are kept as-is.
     * Never rewrites title, offer, or price.
     */
    static List<Map<String, Object>> dedupeByPatternId(List<Map<String, Object>> drafts) {
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (drafts == null) {
            return out;
        }
        for (Map<String, Object> draft : drafts) {
            if (draft == null) {
                continue;
            }
            String patternId = patternIdOf(draft);
            if (patternId != null && !patternId.isBlank()) {
                draft.put("pattern_id", patternId);
                if (!seen.add(patternId)) {
                    continue;
                }
            }
            out.add(draft);
        }
        return out;
    }

    private static String patternIdOf(Map<String, Object> draft) {
        Object value = draft.get("pattern_id");
        if (value == null || String.valueOf(value).isBlank() || "null".equalsIgnoreCase(String.valueOf(value))) {
            value = draft.get("patternId");
        }
        if (value == null) {
            return "";
        }
        String id = String.valueOf(value).trim();
        return "null".equalsIgnoreCase(id) ? "" : id;
    }

    private static String[] patternIdsForInsert(
            Map<String, Object> draft,
            Set<String> knownPatternIds,
            List<PatternMatcher.Pattern> matched
    ) {
        String patternId = patternIdOf(draft);
        if (!patternId.isBlank() && (knownPatternIds.isEmpty() || knownPatternIds.contains(patternId))) {
            return new String[]{patternId};
        }
        return matched.stream().map(PatternMatcher.Pattern::id).toArray(String[]::new);
    }

    private static String shortTitle(String title) {
        return shortText(title, 80);
    }

    private static String shortText(String text, int max) {
        if (text == null || text.isBlank()) {
            return "-";
        }
        String cleaned = text.replace('\n', ' ').trim();
        return cleaned.length() > max ? cleaned.substring(0, max) + "…" : cleaned;
    }

    private static int ideaScore(Map<String, Object> idea) {
        Object value = idea == null ? null : idea.get("score");
        return value instanceof Number n ? n.intValue() : 0;
    }

    public List<Map<String, Object>> list(UUID userId) {
        return store.listIdeas(userId);
    }

    public void feedback(UUID userId, UUID ideaId, Integer feedback, Boolean tried) {
        store.feedbackIdea(userId, ideaId, feedback, tried);
    }

    public Map<String, Object> launchPlan(UUID userId, UUID ideaId, boolean force) {
        return opportunityPipeline.launchPlan(userId, ideaId, force);
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

    private static boolean isFyiNews(String signalType, String title, String description) {
        if (SignalRules.isFyi(signalType)) {
            return true;
        }
        String blob = ((title == null ? "" : title) + " " + (description == null ? "" : description))
                .toLowerCase(Locale.ROOT);
        return SignalRules.isFyi(SignalRules.detect(blob));
    }

    private static Map<String, Object> fyiResponse() {
        Map<String, Object> gate = new LinkedHashMap<>();
        gate.put("proceed", false);
        gate.put("reason", "This news is FYI only — it does not create a specific buyer or buying moment for a small team.");
        gate.put("confidence", 0.9);
        gate.put("suggestions", List.of(
                "A product launch that names who must adopt it",
                "A price change, shortage, approval, or rule with a clear compliance date"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ideas", List.of());
        body.put("cached", false);
        body.put("gate", gate);
        body.put("emptyReason", "FYI only — no business idea from this item.");
        return body;
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

    private DraftResult draftIdeas(
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
                    LlmService.prompt("idea.v2.txt"),
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
                    String patternId = idea.path("pattern_id").asText("").trim();
                    if (patternId.isBlank()) {
                        patternId = idea.path("patternId").asText("").trim();
                    }
                    if (!patternId.isBlank()) {
                        row.put("pattern_id", patternId);
                    }
                    rows.add(row);
                }
                return new DraftResult(rows, "", node);
            }
            log.warn("ideas.fromNews LLM returned no ideas title={}", shortTitle(title));
            return new DraftResult(List.of(), "The model did not return usable ideas for this article.", node);
        } catch (Exception ex) {
            log.warn(
                    "ideas.fromNews LLM failed title={} error={}",
                    shortTitle(title), shortText(ex.getMessage(), 160));
            return new DraftResult(List.of(), "Could not generate ideas from the model.", null);
        }
    }

    private record DraftResult(List<Map<String, Object>> ideas, String emptyReason, Object raw) {
    }

    private static IdeaScorer.Inputs inputs(Map<String, Object> signal, Map<String, Object> profile, Map<String, Object> draft) {
        String industry = String.valueOf(signal.getOrDefault("industry", profile.getOrDefault("industry", "")));
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
