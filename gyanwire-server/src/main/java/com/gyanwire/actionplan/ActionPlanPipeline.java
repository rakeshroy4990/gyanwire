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
import com.gyanwire.config.PlanLimitException;
import com.gyanwire.ideas.pipeline.IdeaStageClient;
import com.gyanwire.llm.ModelTier;
import com.gyanwire.research.engine.LlmService;
import com.gyanwire.usage.UsageService;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    public ActionPlanPipeline(
            NewsBriefService briefs,
            AngleService angles,
            FormatRegistry formats,
            StepComposer composer,
            KnowledgeCatalog catalog,
            ActionPlanStore store,
            UsageService usage,
            ObjectMapper mapper,
            ObjectProvider<IdeaStageClient> stageClient
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

        NewsBrief brief = briefs.extract(newsUrl, title, source, pastedText, userId);
        if (!brief.usable()) {
            throw new ActionPlanException(
                    "Could not read enough of this article",
                    "ARTICLE_UNREADABLE",
                    422,
                    Map.of("reason", "insufficient_facts", "pasteRequired", true));
        }

        int budget = budgetMinutes == null || budgetMinutes <= 0 ? 240 : budgetMinutes;
        FormatSpec format = formats.select(budget, demo, formatId);
        List<Map<String, Object>> angleList = angles.pick(brief, format, userId);
        Map<String, Object> angle = angleList.isEmpty() ? Map.of() : angleList.get(0);

        Map<String, String> slots = fillSlots(brief, userId, null);
        PlanLinter linter = new PlanLinter(catalog);
        Map<String, Object> plan = composer.compose(brief, angle, format, budget, slots, demo);
        PlanLinter.LintResult lint = linter.lint(plan, brief, format);

        for (int attempt = 0; attempt < 2 && !lint.passed(); attempt++) {
            slots = fillSlots(brief, userId, lint.errors());
            plan = composer.compose(brief, angle, format, budget, slots, demo);
            lint = linter.lint(plan, brief, format);
        }
        if (!lint.passed()) {
            plan = composer.safeTemplate(brief, format, budget);
            lint = linter.lint(plan, brief, format);
            if (!lint.passed()) {
                plan.put("lint", Map.of("passed", false, "errors", lint.errors(), "warnings", lint.warnings()));
            } else {
                // Safe template may still fail L5/L9 slightly; keep flag false if any errors remain
                plan.put("lint", lint.toMap());
            }
        } else {
            plan.put("lint", lint.toMap());
        }

        plan.put("angles", angleList);
        plan.put("newsBrief", brief.toMap());
        plan.put("kind", "action_plan");

        usage.recordUsage(userId, ipHash, "action_plan", BigDecimal.ZERO);

        if (persist && userId != null) {
            UUID id = store.savePlan(userId, NewsBriefService.urlHash(newsUrl), plan);
            plan.put("planId", id.toString());
            plan.put("saved", true);
        } else {
            plan.put("saved", false);
        }
        return plan;
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

    private Map<String, String> fillSlots(NewsBrief brief, UUID userId, List<String> lintErrors) {
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
