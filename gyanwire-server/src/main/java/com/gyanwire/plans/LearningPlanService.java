package com.gyanwire.plans;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.config.PlanLimitException;
import com.gyanwire.llm.LlmClient;
import com.gyanwire.llm.LlmRequest;
import com.gyanwire.llm.LlmResult;
import com.gyanwire.llm.ModelTier;
import com.gyanwire.persistence.FlowStore;
import com.gyanwire.profile.ProfileService;
import com.gyanwire.research.engine.LlmService;
import com.gyanwire.usage.UsageService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class LearningPlanService {

    private final FlowStore store;
    private final ProfileService profiles;
    private final UsageService usage;
    private final LlmClient llmClient;
    private final ObjectMapper mapper;
    private final ObjectProvider<com.gyanwire.llm.LlmService> tiered;
    private final ObjectProvider<PlanDraftService> drafts;

    public LearningPlanService(
            FlowStore store,
            ProfileService profiles,
            UsageService usage,
            LlmClient llmClient,
            ObjectMapper mapper,
            ObjectProvider<com.gyanwire.llm.LlmService> tiered,
            ObjectProvider<PlanDraftService> drafts
    ) {
        this.store = store;
        this.profiles = profiles;
        this.usage = usage;
        this.llmClient = llmClient;
        this.mapper = mapper;
        this.tiered = tiered;
        this.drafts = drafts;
    }

    public Map<String, Object> skillPlan(UUID userId, UUID ideaId) {
        SkillBudgetPlanner.Path path = computedPath(userId, ideaId, false);
        SkillBudgetPlanner.Plan plan = path.budget();
        PlanDraftService draft = drafts.getIfAvailable();
        if (draft != null) {
            draft.stageA(userId, path.newsTitle(), "general");
        }
        String explanation = "This path finishes \"" + path.ideaTitle() + "\" from the news \"" + path.newsTitle()
                + "\". Each week has one goal and the tool to use. This month's budget is ₹" + plan.skillBudgetMonth()
                + " and this plan spends ₹" + plan.total() + ". Totals come from the plan JSON.";
        try {
            var node = complete(userId, "skill-plan", "skill-plan.v1", LlmService.prompt("skill-plan.v1.txt"), mapper.writeValueAsString(Map.of(
                    "goal", path.goal(),
                    "ideaTitle", path.ideaTitle(),
                    "newsTitle", path.newsTitle(),
                    "skillBudgetMonth", plan.skillBudgetMonth(),
                    "total", plan.total(),
                    "weeks", path.weeks()
            )));
            if (node != null && !node.path("explanation").asText("").isBlank()) {
                explanation = node.path("explanation").asText();
            }
        } catch (Exception ignored) {
            // keep the fixed sentence
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ideaId", ideaId.toString());
        out.put("ideaTitle", path.ideaTitle());
        out.put("newsTitle", path.newsTitle());
        out.put("newsUrl", path.newsUrl());
        out.put("offer", path.offer());
        out.put("goal", path.goal());
        out.put("skillBudgetMonth", plan.skillBudgetMonth());
        out.put("total", plan.total());
        out.put("monthsToGoal", plan.monthsToGoal());
        out.put("split", SkillBudgetPlanner.split(String.valueOf(profiles.requireProfile(userId).get("goal90d"))));
        out.put("lines", plan.lines());
        out.put("weeks", path.weeks());
        out.put("explanation", explanation);
        out.put("amountSource", "plan");
        out.put("hoursPerWeek", hoursFromProfile(userId));
        out.put("hoursPerSitting", WeekTaskType.HOURS_PER_SITTING);
        return out;
    }

    public Map<String, Object> weekly(UUID userId, UUID ideaId) {
        requireRoadmap(userId);
        SkillBudgetPlanner.Path path = computedPath(userId, ideaId, false);
        int hours = path.weeks().isEmpty() ? 5 : hoursFromProfile(userId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SkillBudgetPlanner.WeekStep week : path.weeks()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("weekNo", week.weekNo());
            row.put("outcome", week.goal());
            row.put("tasks", week.tasks());
            row.put("metric", week.metric());
            row.put("toolId", week.toolId());
            row.put("toolName", week.toolName());
            row.put("costInr", week.costInr());
            row.put("billing", week.billing());
            row.put("taskType", week.taskType());
            row.put("sittings", week.sittings());
            row.put("baseHours", week.baseHours());
            if (week.toolUrl() != null && !week.toolUrl().isBlank()) {
                row.put("toolUrl", week.toolUrl());
            }
            if (week.toolBlurb() != null && !week.toolBlurb().isBlank()) {
                row.put("toolBlurb", week.toolBlurb());
            }
            rows.add(row);
        }
        overlayStageB(userId, path, rows);
        try {
            store.saveWeekly(userId, ideaId, hours, false, rows);
        } catch (Exception ignored) {
            // return the computed plan even if persistence is unavailable
        }
        return Map.of("weeks", rows, "hoursPerWeek", hours, "lighter", false, "goal", path.goal());
    }

    private void overlayStageB(UUID userId, SkillBudgetPlanner.Path path, List<Map<String, Object>> rows) {
        PlanDraftService draft = drafts.getIfAvailable();
        if (draft == null) {
            return;
        }
        try {
            var shared = draft.stageA(userId, path.newsTitle(), "general");
            if (shared == null) {
                return;
            }
            Set<String> tools = new HashSet<>();
            for (SkillBudgetPlanner.WeekStep week : path.weeks()) {
                tools.add(week.toolId());
            }
            var generated = draft.stageB(userId, mapper.writeValueAsString(shared), path.budget().total(), tools);
            if (generated == null || !generated.path("weeks").isArray()) {
                return;
            }
            int i = 0;
            for (var week : generated.path("weeks")) {
                if (i >= rows.size()) {
                    break;
                }
                String title = week.path("title").asText("");
                if (!title.isBlank()) {
                    rows.get(i).put("outcome", title);
                }
                i++;
            }
        } catch (Exception ignored) {
            // Java weeks stay.
        }
    }

    private com.fasterxml.jackson.databind.JsonNode complete(UUID userId, String feature, String version, String system, String user) {
        com.gyanwire.llm.LlmService routed = tiered.getIfAvailable();
        if (routed != null && routed.routerEnabled() && !routed.shadow()) {
            String stage = "outline".equals(feature) || "skill-plan".equals(feature) ? "plan_b" : feature;
            LlmResult result = routed.call(new LlmRequest(stage, ModelTier.LIGHT, system, user, userId, null, "high", null));
            return result.ok() ? result.json() : null;
        }
        return llmClient.complete(userId, feature, version, system, user);
    }

    public void checkin(UUID userId, UUID taskId, String state) {
        requireRoadmap(userId);
        if (!List.of("done", "partly", "not_done").contains(state)) {
            throw new com.gyanwire.research.ResearchException("Check-in must be done, partly, or not_done.", "VALIDATION_ERROR", 400);
        }
        store.checkin(userId, taskId, state);
    }

    public Map<String, Object> outline(UUID userId, UUID ideaId) {
        requireRoadmap(userId);
        Map<String, Object> skill = skillPlan(userId, ideaId);
        int cost = ((Number) skill.getOrDefault("total", 0)).intValue();
        Map<String, Object> content = OutlineDraft.template("Idea outline", cost, 0);
        try {
            var node = complete(userId, "outline", "outline.v1", LlmService.prompt("outline.v1.txt"), mapper.writeValueAsString(content));
            if (node != null && node.isObject()) {
                node.fields().forEachRemaining(entry -> {
                    if (!"monthlyCost".equals(entry.getKey()) && !"breakEvenCustomers".equals(entry.getKey())) {
                        content.put(entry.getKey(), entry.getValue().asText(String.valueOf(content.get(entry.getKey()))));
                    }
                });
            }
        } catch (Exception ignored) {
            // template stands
        }
        try {
            store.saveOutline(userId, ideaId, mapper.writeValueAsString(content), "outline.v1");
        } catch (Exception ignored) {
            // still return the outline
        }
        return content;
    }

    public String outlineMarkdown(UUID userId, UUID ideaId) {
        String stored = null;
        try {
            stored = store.latestOutline(userId, ideaId);
        } catch (Exception ignored) {
            stored = null;
        }
        Map<String, Object> content = outline(userId, ideaId);
        if (stored != null) {
            try {
                content = mapper.readValue(stored, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            } catch (Exception ignored) {
                // use freshly built content
            }
        }
        StringBuilder md = new StringBuilder("# Business outline\n\n");
        for (Map.Entry<String, Object> entry : content.entrySet()) {
            md.append("## ").append(entry.getKey()).append("\n\n").append(entry.getValue()).append("\n\n");
        }
        md.append("Rupee figures are copied from the plan JSON.\n");
        return md.toString();
    }

    private SkillBudgetPlanner.Path computedPath(UUID userId, UUID ideaId, boolean lighter) {
        if (ideaId == null) {
            throw new com.gyanwire.research.ResearchException("Open an idea from a news finding first.", "VALIDATION_ERROR", 400);
        }
        Map<String, Object> idea = store.findIdea(userId, ideaId);
        if (idea == null) {
            throw new com.gyanwire.research.ResearchException(
                    "That idea is not from a news finding on this account.", "VALIDATION_ERROR", 400);
        }
        Map<String, Object> profile = profiles.requireProfile(userId);
        String band = String.valueOf(profile.getOrDefault("incomeBand", "0"));
        int pct = profile.get("investPct") instanceof Number n ? n.intValue() : 10;
        int budget = SkillBudgetPlanner.budget(band, pct);
        int hours = profile.get("hoursPerWeek") instanceof Number n ? n.intValue() : 5;
        List<SkillBudgetPlanner.Item> items = new ArrayList<>();
        Map<String, List<String>> skills = new HashMap<>();
        try {
            for (Map<String, Object> row : store.catalogItems()) {
                String id = String.valueOf(row.get("id"));
                items.add(new SkillBudgetPlanner.Item(
                        id,
                        String.valueOf(row.get("name")),
                        ((Number) row.get("costInr")).intValue(),
                        String.valueOf(row.get("billing")),
                        String.valueOf(row.get("bucket")),
                        row.get("freeAlternativeId") == null ? null : String.valueOf(row.get("freeAlternativeId")),
                        ((Number) row.get("weeksSaved")).intValue(),
                        ((Number) row.get("priority")).intValue(),
                        Boolean.TRUE.equals(row.get("free")),
                        blankStr(row.get("url")),
                        blankStr(row.get("blurb"))
                ));
                skills.put(id, skillNames(row.get("skills")));
            }
        } catch (Exception ignored) {
            items = List.of(new SkillBudgetPlanner.Item("free-notes", "Free notes", 0, "free", "learning", null, 0, 1, true));
            skills = Map.of("free-notes", List.of("notes"));
        }
        String offer = offerOf(idea.get("body"));
        String ideaTitle = String.valueOf(idea.getOrDefault("title", "This idea"));
        String newsTitle = String.valueOf(idea.getOrDefault("newsTitle", ""));
        String industry = String.valueOf(idea.getOrDefault("industry", ""));
        String why = String.valueOf(idea.getOrDefault("why", ""));
        SkillBudgetPlanner.Path path = SkillBudgetPlanner.path(
                ideaTitle,
                newsTitle,
                ideaTitle + " " + why + " " + offer + " " + industry + " " + newsTitle,
                String.valueOf(profile.get("goal90d")),
                budget,
                hours,
                lighter,
                items,
                skills,
                knownSkills(profile.get("skills")),
                LocalDate.now()
        );
        return new SkillBudgetPlanner.Path(
                path.budget(),
                path.goal(),
                path.ideaTitle(),
                path.newsTitle(),
                idea.get("newsUrl") == null ? "" : String.valueOf(idea.get("newsUrl")),
                offer,
                path.weeks()
        );
    }

    private int hoursFromProfile(UUID userId) {
        Map<String, Object> profile = profiles.requireProfile(userId);
        return profile.get("hoursPerWeek") instanceof Number n ? n.intValue() : 5;
    }

    private static String offerOf(Object body) {
        if (body instanceof Map<?, ?> map && map.get("offer") != null) {
            return String.valueOf(map.get("offer"));
        }
        return "";
    }

    private static String blankStr(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equals(text) ? null : text;
    }

    private static List<String> skillNames(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Object item : list) {
            if (item != null && !String.valueOf(item).isBlank()) {
                names.add(String.valueOf(item));
            }
        }
        return names;
    }

    private static Set<String> knownSkills(Object raw) {
        Set<String> known = new HashSet<>();
        if (!(raw instanceof List<?> list)) {
            return known;
        }
        for (Object item : list) {
            if (item instanceof String value && !value.isBlank()) {
                known.add(value.toLowerCase(Locale.ROOT));
                continue;
            }
            if (item instanceof Map<?, ?> map) {
                int level = map.get("level") instanceof Number n ? n.intValue() : 0;
                if (level < 3) {
                    continue;
                }
                Object name = map.get("name") != null ? map.get("name") : map.get("skill");
                if (name != null && !String.valueOf(name).isBlank()) {
                    known.add(String.valueOf(name).toLowerCase(Locale.ROOT));
                }
            }
        }
        return known;
    }

    private void requireRoadmap(UUID userId) {
        Map<String, Object> plan = usage.resolveUserPlan(userId);
        if (!Boolean.TRUE.equals(plan.get("canRoadmap"))) {
            throw new PlanLimitException("Weekly plans and outlines are on Pro and Team.", "LIMIT_REACHED", Map.of("upgradeUrl", "/pricing"));
        }
    }
}
