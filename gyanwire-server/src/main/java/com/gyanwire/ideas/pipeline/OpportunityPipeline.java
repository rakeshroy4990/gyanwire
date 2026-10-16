package com.gyanwire.ideas.pipeline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.actionplan.ActionPlanPipeline;
import com.gyanwire.digests.DigestDeduper;
import com.gyanwire.news.SignalRules;
import com.gyanwire.persistence.FlowStore;
import com.gyanwire.research.ResearchException;
import com.gyanwire.usage.UsageService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.gyanwire.ideas.pipeline.IdeaModels.BusinessIdea;
import static com.gyanwire.ideas.pipeline.IdeaModels.CriticResult;
import static com.gyanwire.ideas.pipeline.IdeaModels.Customer;
import static com.gyanwire.ideas.pipeline.IdeaModels.DimensionScore;
import static com.gyanwire.ideas.pipeline.IdeaModels.NewsSignals;
import static com.gyanwire.ideas.pipeline.IdeaModels.OpportunityGate;
import static com.gyanwire.ideas.pipeline.IdeaModels.ParsedIdeas;
import static com.gyanwire.ideas.pipeline.IdeaModels.Removal;
import static com.gyanwire.ideas.pipeline.IdeaModels.RevenueModel;
import static com.gyanwire.ideas.pipeline.IdeaModels.ScoredIdea;
import static com.gyanwire.ideas.pipeline.IdeaModels.ValidationTest;

@Service
public class OpportunityPipeline {

    static final String VERSION = "idea-pipeline.v2";

    private final FlowStore store;
    private final UsageService usage;
    private final IdeaStageClient client;
    private final ObjectMapper mapper;
    private final IdeaScoreWeights weights;
    private final ActionPlanPipeline actionPlans;

    public OpportunityPipeline(
            FlowStore store,
            UsageService usage,
            IdeaStageClient client,
            ObjectMapper mapper,
            ActionPlanPipeline actionPlans
    ) {
        this.store = store;
        this.usage = usage;
        this.client = client;
        this.actionPlans = actionPlans;
        this.mapper = mapper;
        this.weights = IdeaScoreWeights.load();
    }

    public Map<String, Object> fromNews(UUID userId, Map<String, Object> body, Map<String, Object> profile) {
        String url = String.valueOf(body.getOrDefault("url", "")).trim();
        String title = String.valueOf(body.getOrDefault("title", "News")).trim();
        String description = String.valueOf(body.getOrDefault("description", "")).trim();
        String industry = String.valueOf(body.getOrDefault("industry", "")).trim();
        String whyIdea = String.valueOf(body.getOrDefault("whyIdea", "")).trim();
        if ("null".equals(whyIdea)) {
            whyIdea = "";
        }
        if (!whyIdea.isBlank()) {
            description = whyIdea + "\n" + description;
        }
        boolean force = Boolean.TRUE.equals(body.get("force")) || "true".equalsIgnoreCase(String.valueOf(body.get("force")));
        PipelineLog.start(userId, title, industry, force);
        String signalType = String.valueOf(body.getOrDefault("signalType", "")).trim();
        if ("null".equalsIgnoreCase(signalType)) {
            signalType = "";
        }
        if (isFyiNews(signalType, title, description)) {
            PipelineLog.gate(fyiGate());
            return fyiResponse();
        }
        String urlHash = DigestDeduper.urlHash(url);
        String persona = String.valueOf(profile.getOrDefault("persona", "working"));
        String key = userId + ":" + urlHash + ":" + VERSION + ":" + persona
                + ":" + profile.getOrDefault("incomeBand", "0")
                + ":" + profile.getOrDefault("hoursPerWeek", 10);
        UUID existing = store.findIdeaRun(key);
        if (existing != null && !force) {
            return cached(userId, existing, title, description, industry);
        }

        NewsSignals signals = new ExtractSignalsStage().run(client, userId, title, description);
        PipelineLog.signals(signals);
        if (signals == null) {
            PipelineLog.unread("Could not read a usable signal from this article.");
            return saveRun(
                    userId, existing, url, urlHash, title, description, industry, key,
                    null, socialGate(), List.of(), List.of());
        }
        OpportunityGate gate = new GateStage(mapper).run(client, userId, signals);
        PipelineLog.gate(gate);
        if (gate == null) {
            PipelineLog.unread("Could not read a usable signal from this article.");
            return saveRun(
                    userId, existing, url, urlHash, title, description, industry, key,
                    signals, socialGate(), List.of(), List.of());
        }

        List<ScoredIdea> scored = List.of();
        List<Removal> removed = new ArrayList<>();
        if (gate.proceed()) {
            List<String> angles = new AngleStage(mapper).run(client, userId, signals);
            PipelineLog.angles(angles);
            if (!angles.isEmpty()) {
                ParsedIdeas built = new IdeaBuilderStage(mapper).run(client, userId, signals, angles, profile);
                removed.addAll(built.rejected());
                CriticResult critic = new CriticStage(mapper).run(client, userId, signals, built.valid());
                removed.addAll(critic.removed());
                scored = new ScoreStage(weights).run(critic.kept(), signals, profile);
            }
        }
        if (!gate.proceed()) {
            gate = socialGate();
        }
        return saveRun(userId, existing, url, urlHash, title, description, industry, key, signals, gate, removed, scored);
    }

    private Map<String, Object> saveRun(
            UUID userId,
            UUID existing,
            String url,
            String urlHash,
            String title,
            String description,
            String industry,
            String key,
            NewsSignals signals,
            OpportunityGate gate,
            List<Removal> removed,
            List<ScoredIdea> scored
    ) {
        scored = SocialMediaIdea.ensure(scored, new IdeaScoreCalculator(weights), title, description, signals);
        String emptyReason = scored.isEmpty() ? "No idea was specific enough to show." : "";
        UUID runId = existing == null ? UUID.randomUUID() : existing;
        String payload = writePayload(signals, gate, removed);
        if (existing != null) {
            store.deleteIdeasForRun(userId, existing);
            store.updateIdeaRunSignals(userId, existing, payload);
        } else {
            store.insertIdeaRun(runId, userId, url, urlHash, title, industry, payload, VERSION, key);
        }
        List<Map<String, Object>> rows = persist(userId, runId, industry, signals, scored);
        usage.recordUsage(userId, null, "idea", java.math.BigDecimal.ZERO);
        PipelineLog.done(runId, rows.size(), false, emptyReason);
        return response(runId, rows, false, gate, removed, emptyReason);
    }

    private static OpportunityGate socialGate() {
        return new OpportunityGate(true, "Same-day social post from this headline.", 0.6, List.of());
    }

    public Map<String, Object> launchPlan(UUID userId, UUID ideaId, boolean force) {
        return launchPlan(userId, ideaId, force, null, false, null);
    }

    public Map<String, Object> launchPlan(
            UUID userId,
            UUID ideaId,
            boolean force,
            Integer budgetMinutes,
            boolean demo,
            String format
    ) {
        Map<String, Object> found = store.findIdea(userId, ideaId);
        if (found == null) {
            throw new ResearchException("That idea is not on this account.", "NOT_FOUND", 404);
        }
        Map<String, Object> body = asMap(found.get("body"));
        BusinessIdea idea = readIdea(body, found);
        boolean social = idea != null && "comparison_content".equals(idea.angleType());
        boolean optionsSet = budgetMinutes != null || demo || (format != null && !format.isBlank());
        if (!force && !optionsSet && body.get("launchPlan") instanceof Map<?, ?> saved) {
            boolean savedAction = ActionPlanPipeline.isActionPlan(saved);
            boolean savedLegacySocial = "social".equals(String.valueOf(saved.get("kind")));
            if (social && savedAction && ActionPlanPipeline.hasValidShape(saved)) {
                Map<String, Object> plan = copyMap(saved);
                plan.put("ideaId", ideaId.toString());
                PipelineLog.launchPlan(ideaId, true, false);
                return plan;
            }
            if (!social && !savedAction && !savedLegacySocial) {
                Map<String, Object> plan = copyMap(saved);
                plan.put("ideaId", ideaId.toString());
                PipelineLog.launchPlan(ideaId, true, false);
                return plan;
            }
        }
        Map<String, Object> plan;
        if (social) {
            String newsUrl = String.valueOf(found.getOrDefault("newsUrl", ""));
            String newsTitle = String.valueOf(found.getOrDefault("newsTitle", ""));
            String description = idea.oneLiner() == null ? newsTitle : idea.oneLiner();
            int budget = budgetMinutes == null || budgetMinutes <= 0 ? 240 : budgetMinutes;
            try {
                plan = actionPlans.generateForIdea(userId, newsUrl, newsTitle, demo, budget, format);
            } catch (Exception ex) {
                plan = actionPlans.generate(
                        userId,
                        null,
                        newsUrl,
                        newsTitle,
                        "",
                        budget,
                        padArticle(newsTitle, description),
                        demo,
                        userId != null);
            }
        } else {
            plan = new LaunchPlanStage(mapper).run(
                    client, userId, idea, String.valueOf(found.getOrDefault("newsTitle", "")));
        }
        body.put("launchPlan", plan);
        try {
            store.updateIdeaBody(userId, ideaId, mapper.writeValueAsString(body));
        } catch (Exception ignored) {
            // The response still shows the plan if the write fails.
        }
        plan.put("ideaId", ideaId.toString());
        PipelineLog.launchPlan(ideaId, false, true);
        return plan;
    }

    private Map<String, Object> cached(UUID userId, UUID runId, String title, String description, String industry) {
        List<Map<String, Object>> ideas = new ArrayList<>(store.listIdeasForRun(userId, runId));
        if (!SocialMediaIdea.presentRows(ideas)) {
            List<ScoredIdea> added = SocialMediaIdea.ensure(
                    List.of(), new IdeaScoreCalculator(weights), title, description, null);
            ideas.addAll(0, persist(userId, runId, industry, null, added));
        }
        OpportunityGate gate = null;
        List<Map<String, Object>> removed = List.of();
        try {
            Map<String, Object> payload = mapper.readValue(
                    store.ideaRunSignals(userId, runId),
                    new TypeReference<>() {});
            if (payload.get("gate") != null) {
                gate = mapper.convertValue(payload.get("gate"), OpportunityGate.class);
            }
            if (payload.get("removed") instanceof List<?> list) {
                removed = copyRemovals(list);
            }
        } catch (Exception ignored) {
            gate = null;
        }
        String emptyReason = ideas.isEmpty() && gate != null && gate.proceed()
                ? "No idea was specific enough to show."
                : "";
        PipelineLog.cacheHit(runId, ideas.size());
        PipelineLog.done(runId, ideas.size(), true, emptyReason);
        return response(runId, ideas, true, gate, removed, emptyReason);
    }

    private List<Map<String, Object>> persist(UUID userId, UUID runId, String industry, NewsSignals signals, List<ScoredIdea> scored) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ScoredIdea item : scored) {
            UUID ideaId = UUID.randomUUID();
            Map<String, Object> row = toRow(item, signals, industry);
            try {
                store.insertIdea(
                        ideaId,
                        runId,
                        userId,
                        item.score().stored(),
                        mapper.writeValueAsString(item.score()),
                        item.idea().title(),
                        item.idea().whyNow(),
                        mapper.writeValueAsString(row),
                        new String[]{item.idea().angleType()});
            } catch (Exception ignored) {
                continue;
            }
            row.put("id", ideaId.toString());
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> toRow(ScoredIdea item, NewsSignals signals, String industry) {
        Map<String, Object> row = mapper.convertValue(item.idea(), new TypeReference<LinkedHashMap<String, Object>>() {});
        row.put("format", AngleType.labelOf(item.idea().angleType()));
        row.put("offer", item.idea().oneLiner());
        row.put("whyNow", item.idea().whyNow());
        row.put("timeWindow", windowMap(signals));
        row.put("scores", scoreMap(item));
        row.put("score", item.score().stored());
        String disclaimer = disclaimer(industry);
        if (!disclaimer.isBlank()) {
            row.put("disclaimer", disclaimer);
        }
        return row;
    }

    private static Map<String, Object> scoreMap(ScoredIdea item) {
        List<Map<String, Object>> dimensions = new ArrayList<>();
        for (DimensionScore dimension : item.score().dimensions()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", dimension.key());
            row.put("score", dimension.score());
            row.put("reason", dimension.reason());
            row.put("assumed", dimension.assumed());
            dimensions.add(row);
        }
        Map<String, Object> scores = new LinkedHashMap<>();
        scores.put("weighted", item.score().weighted());
        scores.put("label", "estimate");
        scores.put("dimensions", dimensions);
        return scores;
    }

    private static Map<String, Object> windowMap(NewsSignals signals) {
        Map<String, Object> window = new LinkedHashMap<>();
        if (signals != null && signals.timeWindow() != null) {
            if (signals.timeWindow().startsAt() != null) {
                window.put("startsAt", signals.timeWindow().startsAt());
            }
            window.put("urgency", signals.timeWindow().urgency());
        }
        return window;
    }

    private String writePayload(NewsSignals signals, OpportunityGate gate, List<Removal> removed) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pipeline", VERSION);
        payload.put("signals", signals);
        payload.put("gate", gate);
        payload.put("removed", removed);
        try {
            return mapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Map<String, Object> response(
            UUID runId,
            List<Map<String, Object>> ideas,
            boolean cached,
            OpportunityGate gate,
            List<?> removed,
            String emptyReason
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("runId", runId == null ? "" : runId.toString());
        body.put("ideas", ideas);
        body.put("cached", cached);
        body.put("pipeline", VERSION);
        body.put("gate", gate == null ? Map.of() : mapper.convertValue(gate, new TypeReference<LinkedHashMap<String, Object>>() {}));
        body.put("removed", removed);
        if (emptyReason != null && !emptyReason.isBlank()) {
            body.put("emptyReason", emptyReason);
        }
        return body;
    }

    private static Map<String, Object> unread() {
        Map<String, Object> gate = new LinkedHashMap<>();
        gate.put("proceed", false);
        gate.put("reason", "Could not read a usable signal from this article.");
        gate.put("confidence", 0);
        gate.put("suggestions", IdeaPipelineSchemas.DEFAULT_SUGGESTIONS);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ideas", List.of());
        body.put("cached", false);
        body.put("pipeline", VERSION);
        body.put("gate", gate);
        body.put("removed", List.of());
        return body;
    }

    private static boolean isFyiNews(String signalType, String title, String description) {
        if (SignalRules.isFyi(signalType)) {
            return true;
        }
        String blob = ((title == null ? "" : title) + " " + (description == null ? "" : description))
                .toLowerCase(Locale.ROOT);
        return SignalRules.isFyi(SignalRules.detect(blob));
    }

    private static OpportunityGate fyiGate() {
        return new OpportunityGate(
                false,
                "This news is FYI only — it does not create a specific buyer or buying moment for a small team.",
                0.9,
                IdeaPipelineSchemas.DEFAULT_SUGGESTIONS);
    }

    private Map<String, Object> fyiResponse() {
        return response(null, List.of(), false, fyiGate(), List.of(), "");
    }

    private BusinessIdea readIdea(Map<String, Object> body, Map<String, Object> found) {
        try {
            BusinessIdea idea = mapper.convertValue(body, BusinessIdea.class);
            if (idea != null && idea.title() != null && !idea.title().isBlank() && idea.customer() != null) {
                return idea;
            }
        } catch (Exception ignored) {
            // Fall through to a plan built from the stored title.
        }
        String title = String.valueOf(found.getOrDefault("title", "This offer"));
        String why = String.valueOf(found.getOrDefault("why", ""));
        String offer = String.valueOf(body.getOrDefault("offer", why));
        return new BusinessIdea(
                "education_or_training",
                title,
                offer.isBlank() ? title : offer,
                new Customer("Readers already following this story", "India"),
                why.isBlank() ? "The story is current and the reader has to decide what to do with it." : why,
                why.isBlank() ? "They want a concrete next step, not another summary." : why,
                why.isBlank() ? "The story is still timely." : why,
                offer + " Ship a one-page version and ask for a reply.",
                new RevenueModel("service", null, "The first price comes from what the first five people say they would pay."),
                List.of(
                        "Write the offer in one page.",
                        "Send it to five people in the customer group.",
                        "Count replies against the metric."),
                new ValidationTest("Send the one-page offer", "Five replies in seven days"),
                List.of("The stored idea is thin, so the first test may be too vague."),
                "days",
                false);
    }

    /** Pad headline + description to satisfy NewsResolve MIN_CHARS when the article cannot be fetched. */
    static String padArticle(String title, String description) {
        StringBuilder out = new StringBuilder();
        if (title != null && !title.isBlank()) {
            out.append(title.trim()).append(". ");
        }
        if (description != null && !description.isBlank()) {
            out.append(description.trim()).append(' ');
        }
        String seed = out.toString().trim();
        if (seed.isBlank()) {
            seed = "This news item needs a short explainer for viewers who follow product launches.";
        }
        while (out.length() < 650) {
            out.append(' ').append(seed);
        }
        return out.toString().trim();
    }

    private static Map<String, Object> asMap(Object value) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    copy.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
        return copy;
    }

    private static Map<String, Object> copyMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                copy.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return copy;
    }

    private static List<Map<String, Object>> copyRemovals(List<?> list) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                rows.add(copyMap(map));
            }
        }
        return rows;
    }

    private static String disclaimer(String industry) {
        return switch (industry) {
            case "Share Market" -> "Informational only. Not investment advice.";
            case "Real Estate" -> "Informational only. Not property, loan, or investment advice.";
            case "Medical" -> "Research aid, not a diagnosis or treatment.";
            case "Astrology", "Gaming" -> "Ideas here are cultural or entertainment context, not predictions.";
            default -> "";
        };
    }
}
