package com.gyanwire.actionplan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.actionplan.angle.AngleService;
import com.gyanwire.actionplan.compose.StepComposer;
import com.gyanwire.actionplan.format.FormatRegistry;
import com.gyanwire.actionplan.format.FormatSpec;
import com.gyanwire.actionplan.lint.PlanLinter;
import com.gyanwire.actionplan.news.NewsBrief;
import com.gyanwire.actionplan.news.NewsBriefService;
import com.gyanwire.actionplan.research.VideoResearchPack;
import com.gyanwire.actionplan.research.VideoResearchService;
import com.gyanwire.config.PlanLimitException;
import com.gyanwire.ideas.pipeline.IdeaStageClient;
import com.gyanwire.llm.ModelTier;
import com.gyanwire.research.engine.LlmService;
import com.gyanwire.usage.UsageService;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ActionPlanPipeline {

    private final NewsBriefService briefs;
    private final AngleService angles;
    private final FormatRegistry formats;
    private final StepComposer composer;
    private final KnowledgeCatalog catalog;
    private final ActionPlanStore store;
    private final UsageService usage;
    private final ObjectMapper mapper;
    private final ObjectProvider<IdeaStageClient> stageClient;
    private final VideoResearchService videoResearch;

    public ActionPlanPipeline(
            NewsBriefService briefs,
            AngleService angles,
            FormatRegistry formats,
            StepComposer composer,
            KnowledgeCatalog catalog,
            ActionPlanStore store,
            UsageService usage,
            ObjectMapper mapper,
            ObjectProvider<IdeaStageClient> stageClient,
            VideoResearchService videoResearch
    ) {
        this.briefs = briefs;
        this.angles = angles;
        this.formats = formats;
        this.composer = composer;
        this.catalog = catalog;
        this.store = store;
        this.usage = usage;
        this.mapper = mapper;
        this.stageClient = stageClient;
        this.videoResearch = videoResearch;
    }

    public Map<String, Object> generate(
            UUID userId,
            String ipHash,
            String newsUrl,
            String title,
            String source,
            Integer budgetMinutes,
            String pastedText,
            boolean demo,
            boolean persist
    ) {
        return generate(userId, ipHash, newsUrl, title, source, budgetMinutes, pastedText, demo, persist, null);
    }

    public Map<String, Object> generate(
            UUID userId,
            String ipHash,
            String newsUrl,
            String title,
            String source,
            Integer budgetMinutes,
            String pastedText,
            boolean demo,
            boolean persist,
            String formatId
    ) {
        assertQuota(userId, ipHash);
        int budget = normalizeBudget(budgetMinutes);
        FormatSpec format = formats.select(budget, demo, formatId);

        NewsBrief brief = briefs.extract(newsUrl, title, source, pastedText, userId);
        if (!brief.usable()) {
            throw new ActionPlanException(
                    "Could not read enough of this article",
                    "ARTICLE_UNREADABLE",
                    422,
                    Map.of("reason", "insufficient_facts", "pasteRequired", true));
        }

        Map<String, Object> research = resolveVideoResearch(newsUrl);
        Map<String, Object> plan = buildUsablePlan(brief, format, budget, demo, userId, research, null, null);
        usage.recordUsage(userId, ipHash, "action_plan", BigDecimal.ZERO);
        return persistPlan(plan, userId, newsUrl, persist, null);
    }

    /**
     * Streams a saved skeleton first so the client can navigate, then emits
     * status and the finished plan on the same plan id.
     */
    public void generateStream(
            UUID userId,
            String ipHash,
            String newsUrl,
            String title,
            String source,
            Integer budgetMinutes,
            String pastedText,
            boolean demo,
            boolean persist,
            String formatId,
            ActionPlanSink sink
    ) {
        generateStream(
                userId, ipHash, newsUrl, title, source, budgetMinutes, pastedText,
                demo, persist, formatId, null, null, sink);
    }

    public void generateStream(
            UUID userId,
            String ipHash,
            String newsUrl,
            String title,
            String source,
            Integer budgetMinutes,
            String pastedText,
            boolean demo,
            boolean persist,
            String formatId,
            String ideaTitle,
            String whyNow,
            ActionPlanSink sink
    ) {
        ActionPlanSink out = sink == null ? ActionPlanSink.noop() : sink;
        assertQuota(userId, ipHash);

        int budget = normalizeBudget(budgetMinutes);
        FormatSpec format = formats.select(budget, demo, formatId);
        NewsBrief skeletonBrief = NewsBrief.insufficient(title, newsUrl);
        Map<String, Object> skeleton = composer.compose(
                skeletonBrief,
                Map.of(),
                format,
                budget,
                StepComposer.fallbackSlots(skeletonBrief),
                demo);
        skeleton.put("buildStatus", "building");
        skeleton.put("newsBrief", skeletonBrief.toMap());
        skeleton.put("kind", "action_plan");
        skeleton.put("angles", List.of());
        String planId = persistPlan(skeleton, userId, newsUrl, persist, null).get("planId").toString();
        out.plan(skeleton);

        out.status("brief");
        NewsBrief brief = briefs.extract(newsUrl, title, source, pastedText, userId);
        if (!brief.usable()) {
            skeleton.put("buildStatus", "paste");
            skeleton.put("newsBrief", brief.toMap());
            persistPlan(skeleton, userId, newsUrl, persist, planId);
            out.plan(skeleton);
            throw new ActionPlanException(
                    "Could not read enough of this article",
                    "ARTICLE_UNREADABLE",
                    422,
                    Map.of("reason", "insufficient_facts", "pasteRequired", true, "planId", planId));
        }

        Map<String, Object> research = resolveVideoResearch(newsUrl);
        Map<String, Object> plan = buildUsablePlan(
                brief, format, budget, demo, userId, research, ideaTitle, whyNow, out);
        plan.put("buildStatus", "ready");
        usage.recordUsage(userId, ipHash, "action_plan", BigDecimal.ZERO);
        persistPlan(plan, userId, newsUrl, persist, planId);
        out.plan(plan);
    }

    private Map<String, Object> resolveVideoResearch(String newsUrl) {
        VideoResearchPack pack = videoResearch.find(newsUrl);
        if (pack != null && pack.running()) {
            pack = videoResearch.awaitReady(newsUrl, VideoResearchService.AWAIT_TIMEOUT);
        }
        if (pack != null && pack.ready()) {
            return pack.summary();
        }
        return null;
    }

    private Map<String, Object> buildUsablePlan(
            NewsBrief brief,
            FormatSpec format,
            int budget,
            boolean demo,
            UUID userId,
            Map<String, Object> videoResearchSummary,
            String ideaTitle,
            String whyNow
    ) {
        return buildUsablePlan(
                brief, format, budget, demo, userId, videoResearchSummary, ideaTitle, whyNow, ActionPlanSink.noop());
    }

    private Map<String, Object> buildUsablePlan(
            NewsBrief brief,
            FormatSpec format,
            int budget,
            boolean demo,
            UUID userId,
            Map<String, Object> videoResearchSummary,
            String ideaTitle,
            String whyNow,
            ActionPlanSink sink
    ) {
        ActionPlanSink out = sink == null ? ActionPlanSink.noop() : sink;
        out.status("angles");
        List<Map<String, Object>> angleList = angles.pick(brief, format, userId);
        Map<String, Object> angle = angleList.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(angleList.get(0));
        if (ideaTitle != null && !ideaTitle.isBlank()) {
            angle.put("selectedTitle", ideaTitle.trim());
        }
        if (whyNow != null && !whyNow.isBlank()) {
            angle.put("selectedWhyNow", whyNow.trim());
        }

        out.status("slots");
        Map<String, String> slots = fillSlots(brief, userId, videoResearchSummary, ideaTitle, whyNow, null);
        PlanLinter linter = new PlanLinter(catalog);
        Map<String, Object> plan = composer.compose(brief, angle, format, budget, slots, demo);
        if (videoResearchSummary != null) {
            plan.put("videoResearch", videoResearchSummary);
        }
        PlanLinter.LintResult lint = linter.lint(plan, brief, format);

        for (int attempt = 0; attempt < 2 && !lint.passed(); attempt++) {
            slots = fillSlots(brief, userId, videoResearchSummary, ideaTitle, whyNow, lint.errors());
            plan = composer.compose(brief, angle, format, budget, slots, demo);
            if (videoResearchSummary != null) {
                plan.put("videoResearch", videoResearchSummary);
            }
            lint = linter.lint(plan, brief, format);
        }
        if (!lint.passed()) {
            plan = composer.safeTemplate(brief, format, budget);
            if (videoResearchSummary != null) {
                plan.put("videoResearch", videoResearchSummary);
            }
            lint = linter.lint(plan, brief, format);
            if (!lint.passed()) {
                plan.put("lint", Map.of("passed", false, "errors", lint.errors(), "warnings", lint.warnings()));
            } else {
                plan.put("lint", lint.toMap());
            }
        } else {
            plan.put("lint", lint.toMap());
        }

        plan.put("angles", angleList);
        plan.put("newsBrief", brief.toMap());
        plan.put("kind", "action_plan");
        return plan;
    }

    private Map<String, Object> persistPlan(
            Map<String, Object> plan,
            UUID userId,
            String newsUrl,
            boolean persist,
            String forcedPlanId
    ) {
        if (forcedPlanId != null && !forcedPlanId.isBlank()) {
            plan.put("planId", forcedPlanId);
        }
        if (persist && userId != null) {
            UUID id = store.savePlan(userId, NewsBriefService.urlHash(newsUrl), plan);
            plan.put("planId", id.toString());
            plan.put("saved", true);
        } else {
            if (plan.get("planId") == null || String.valueOf(plan.get("planId")).isBlank()) {
                plan.put("planId", UUID.randomUUID().toString());
            }
            plan.put("saved", false);
        }
        return plan;
    }

    private static int normalizeBudget(Integer budgetMinutes) {
        return budgetMinutes == null || budgetMinutes <= 0 ? 240 : budgetMinutes;
    }

    /** Used by LaunchPlanStage for social ideas. */
    public Map<String, Object> generateForIdea(
            UUID userId,
            String newsUrl,
            String newsTitle,
            boolean demo
    ) {
        return generateForIdea(userId, newsUrl, newsTitle, demo, 240, null);
    }

    public Map<String, Object> generateForIdea(
            UUID userId,
            String newsUrl,
            String newsTitle,
            boolean demo,
            Integer budgetMinutes,
            String formatId
    ) {
        int budget = budgetMinutes == null || budgetMinutes <= 0 ? 240 : budgetMinutes;
        return generate(userId, null, newsUrl, newsTitle, "", budget, null, demo, userId != null, formatId);
    }

    public static boolean isActionPlan(Map<?, ?> plan) {
        return plan != null && "action_plan".equals(String.valueOf(plan.get("kind")));
    }

    public static boolean hasValidShape(Map<?, ?> plan) {
        if (!isActionPlan(plan)) {
            return false;
        }
        if (!(plan.get("steps") instanceof List<?> steps) || steps.size() < 8) {
            return false;
        }
        Object lint = plan.get("lint");
        // Allow flagged plans that still have structure
        return plan.get("budgetMinutes") instanceof Number
                || (plan.get("budgetMinutes") != null && String.valueOf(plan.get("budgetMinutes")).matches("\\d+"));
    }

    private Map<String, String> fillSlots(
            NewsBrief brief,
            UUID userId,
            Map<String, Object> videoResearchSummary,
            String ideaTitle,
            String whyNow,
            List<String> lintErrors
    ) {
        Map<String, String> fallback = StepComposer.fallbackSlots(brief);
        IdeaStageClient client = stageClient.getIfAvailable();
        if (client == null) {
            return fallback;
        }
        try {
            String system = LlmService.prompt("action-plan-slots.v1.txt");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("entities", brief.entities());
            payload.put("facts", brief.facts().stream().map(f -> Map.of(
                    "id", f.id(),
                    "text", f.text())).toList());
            if (videoResearchSummary != null) {
                payload.put("whatHappened", videoResearchSummary.get("whatHappened"));
                payload.put("whyItMatters", videoResearchSummary.get("whyItMatters"));
                Object packFacts = videoResearchSummary.get("facts");
                if (packFacts instanceof List<?> list) {
                    List<Map<String, Object>> rows = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> map) {
                            Map<String, Object> row = new LinkedHashMap<>();
                            Object id = map.get("id");
                            Object text = map.get("text");
                            Object sourceUrl = map.get("sourceUrl");
                            row.put("id", id == null ? "" : String.valueOf(id));
                            row.put("text", text == null ? "" : String.valueOf(text));
                            row.put("sourceUrl", sourceUrl == null ? "" : String.valueOf(sourceUrl));
                            rows.add(row);
                        }
                    }
                    payload.put("videoResearchFacts", rows);
                }
            }
            if (ideaTitle != null && !ideaTitle.isBlank()) {
                payload.put("ideaTitle", ideaTitle.trim());
            }
            if (whyNow != null && !whyNow.isBlank()) {
                payload.put("whyNow", whyNow.trim());
            }
            if (lintErrors != null && !lintErrors.isEmpty()) {
                payload.put("lintErrors", lintErrors);
            }
            String user = mapper.writeValueAsString(payload);
            JsonNode node = client.complete(
                    userId, "action-plan-slots.v1", "action-plan-slots", system, user, ModelTier.LIGHT);
            if (node == null || !node.isObject()) {
                return fallback;
            }
            Map<String, String> slots = new LinkedHashMap<>(fallback);
            for (String key : List.of(
                    "hook", "point_1", "point_2", "point_3", "cta",
                    "caption_youtube", "caption_instagram", "caption_linkedin", "title")) {
                String value = node.path(key).asText("").trim();
                if (!value.isBlank()) {
                    slots.put(key, value);
                }
            }
            StepComposer.ensureSlots(slots, brief);
            return slots;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private void assertQuota(UUID userId, String ipHash) {
        Map<String, Object> plan = usage.resolveUserPlan(userId);
        int limit = plan.get("dailyActionPlanLimit") instanceof Number n
                ? n.intValue()
                : ((Number) plan.getOrDefault("dailySearchLimit", 5)).intValue();
        if (userId == null) {
            limit = Math.min(limit, UsageService.ANON_DAILY_SEARCH_LIMIT);
        }
        long used = usage.countToday(userId, ipHash, "action_plan");
        if (used >= limit) {
            throw new PlanLimitException(
                    "Daily action plan limit reached.",
                    "LIMIT_REACHED",
                    Map.of("upgradeUrl", "/pricing", "used", used, "limit", limit));
        }
    }
}
