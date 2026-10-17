package com.gyanwire.actionplan.compose;

import com.gyanwire.actionplan.KnowledgeCatalog;
import com.gyanwire.actionplan.StepDetailService;
import com.gyanwire.actionplan.format.FormatRegistry;
import com.gyanwire.actionplan.format.FormatSpec;
import com.gyanwire.actionplan.news.NewsBrief;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class StepComposer {

    private final FormatRegistry formats;
    private final KnowledgeCatalog catalog;
    private final StepDetailService stepDetails;

    public StepComposer(FormatRegistry formats, KnowledgeCatalog catalog, StepDetailService stepDetails) {
        this.formats = formats;
        this.catalog = catalog;
        this.stepDetails = stepDetails;
    }

    public Map<String, Object> compose(
            NewsBrief brief,
            Map<String, Object> angle,
            FormatSpec format,
            int budgetMinutes,
            Map<String, String> slots,
            boolean demo
    ) {
        FormatSpec spec = format == null ? formats.select(budgetMinutes, demo) : format;
        List<Map<String, Object>> template = formats.stepTemplate(spec.stepTemplate());
        Map<String, String> filled = slots == null ? fallbackSlots(brief) : new LinkedHashMap<>(slots);
        ensureSlots(filled, brief);

        List<Map<String, Object>> rawSteps = new ArrayList<>();
        for (Map<String, Object> row : template) {
            rawSteps.add(new LinkedHashMap<>(row));
        }
        if (demo) {
            insertDemoStep(rawSteps);
        }

        int targetBudget = budgetMinutes > 0 ? budgetMinutes : spec.defaultBudgetMinutes();
        scale(rawSteps, targetBudget);

        List<Map<String, Object>> steps = new ArrayList<>();
        for (Map<String, Object> row : rawSteps) {
            steps.add(materialize(row, filled, spec.id()));
        }

        String moneyRuleId = spec.moneyRuleId().isBlank() ? "yt_shorts_ypp" : spec.moneyRuleId();
        var money = catalog.moneyRule(moneyRuleId);
        Map<String, Object> moneyBlock = new LinkedHashMap<>();
        moneyBlock.put("ruleId", moneyRuleId);
        moneyBlock.put("claim", money.map(KnowledgeCatalog.MoneyRule::claim)
                .orElse("Shorts can earn ad revenue share only after YouTube Partner Program eligibility. This is not instant income."));
        moneyBlock.put("verifiedOn", money.map(KnowledgeCatalog.MoneyRule::lastVerified).orElse(""));

        String entity = brief.entities() != null && !brief.entities().isEmpty()
                ? brief.entities().get(0)
                : firstEntity(brief.headline());
        String label = "Quick short on " + entity;

        String script = String.join(" ",
                filled.getOrDefault("hook", ""),
                filled.getOrDefault("point_1", ""),
                filled.getOrDefault("point_2", ""),
                filled.getOrDefault("point_3", ""),
                filled.getOrDefault("cta", "")).trim();

        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("planId", UUID.randomUUID().toString());
        plan.put("newsId", brief.newsId());
        plan.put("kind", "action_plan");
        plan.put("type", "social_media");
        plan.put("format", spec.id());
        plan.put("label", label);
        plan.put("angle", angle == null ? Map.of() : angle);
        plan.put("budgetMinutes", targetBudget);
        plan.put("deadline", targetBudget <= 240 ? "today" : "day2");
        plan.put("platforms", spec.platforms());
        plan.put("steps", steps);
        int osTotal = steps.stream().mapToInt(s -> intVal(s.get("durationMin"), 0)).sum();
        int paidTotal = steps.stream().mapToInt(s -> intVal(s.get("paidDurationMin"), intVal(s.get("durationMin"), 0))).sum();
        double planGain = osTotal <= 0 ? 0 : Math.round(((osTotal - paidTotal) * 1000.0 / osTotal)) / 10.0;
        plan.put("pathTotals", Map.of(
                "openSourceMinutes", osTotal,
                "paidMinutes", paidTotal,
                "gainPct", planGain,
                "basis", "estimate"));
        plan.put("money", moneyBlock);
        plan.put("slots", filled);
        plan.put("script", script);
        plan.put("hook", filled.getOrDefault("hook", ""));
        plan.put("factIds", brief.facts() == null ? List.of()
                : brief.facts().stream().map(NewsBrief.Fact::id).toList());
        plan.put("lint", Map.of("passed", false, "errors", List.of()));
        return plan;
    }

    /** Safe template with plain placeholders when lint retries are exhausted. */
    public Map<String, Object> safeTemplate(NewsBrief brief, FormatSpec format, int budgetMinutes) {
        Map<String, String> slots = fallbackSlots(brief);
        Map<String, Object> angle = Map.of(
                "id", "a1",
                "oneLine", "Explain the news for people who will care today",
                "forWhom", brief.whoItAffects() == null || brief.whoItAffects().isEmpty()
                        ? "viewers"
                        : brief.whoItAffects().get(0),
                "whyNow", "The story is fresh",
                "test", "100 views in 48 h");
        Map<String, Object> plan = compose(brief, angle, format, budgetMinutes, slots, false);
        plan.put("lint", Map.of("passed", false, "errors", List.of("safe_template")));
        return plan;
    }

    private Map<String, Object> materialize(Map<String, Object> row, Map<String, String> slots, String formatId) {
        Map<String, Object> step = new LinkedHashMap<>();
        String stepId = str(row.get("id"));
        step.put("id", stepId);
        step.put("order", intVal(row.get("order"), 0));
        step.put("title", str(row.get("title")));
        step.put("startMin", intVal(row.get("startMin"), 0));
        int osMin = intVal(row.get("durationMin"), 0);
        int paidMin = intVal(row.get("paidDurationMin"), osMin);
        step.put("durationMin", osMin);
        step.put("paidDurationMin", paidMin);
        step.put("action", fill(str(row.get("action")), slots));
        step.put("input", str(row.get("input")));
        step.put("output", str(row.get("output")));
        step.put("doneWhen", str(row.get("doneWhen")));
        step.put("tool", toolRef(str(row.get("toolId"))));
        String paidTool = str(row.get("paidToolId"));
        if (!paidTool.isBlank()) {
            step.put("paidTool", toolRef(paidTool));
        }
        String alt = str(row.get("altToolId"));
        if (!alt.isBlank()) {
            step.put("altTool", toolRef(alt));
        }
        String caption = str(row.get("captionToolId"));
        if (!caption.isBlank()) {
            step.put("captionTool", toolRef(caption));
        }
        step.put("ifStuck", str(row.get("ifStuck")));
        step.put("platforms", row.get("platforms") instanceof List<?> list ? list : List.of("all"));
        double gain = osMin <= 0 ? 0 : Math.round(((osMin - paidMin) * 1000.0 / osMin)) / 10.0;
        step.put("gain", Map.of("pct", gain, "basis", "estimate", "label", "estimate"));
        step.put("microSteps", fillAll(stepDetails.microSteps(formatId, stepId, "open_source"), slots));
        step.put("paidMicroSteps", fillAll(stepDetails.microSteps(formatId, stepId, "paid"), slots));
        return step;
    }

    private Map<String, Object> toolRef(String id) {
        Map<String, Object> out = new LinkedHashMap<>();
        var tool = catalog.tool(id);
        if (tool.isPresent()) {
            out.putAll(tool.get().toMap());
        } else {
            out.put("id", id);
            out.put("name", id);
            out.put("tier", "free");
            out.put("url", "");
            out.put("openSource", false);
        }
        return out;
    }

    private void scale(List<Map<String, Object>> steps, int budgetMinutes) {
        int base = 0;
        for (Map<String, Object> step : steps) {
            base += intVal(step.get("durationMin"), 0);
        }
        if (base == budgetMinutes || base == 0) {
            recomputeStarts(steps);
            return;
        }
        // Scale only steps marked scalable (4–6 and 9)
        int fixed = 0;
        int scalable = 0;
        for (Map<String, Object> step : steps) {
            int d = intVal(step.get("durationMin"), 0);
            if (Boolean.TRUE.equals(step.get("scalable"))) {
                scalable += d;
            } else {
                fixed += d;
            }
        }
        int remaining = Math.max(budgetMinutes - fixed, scalable);
        double factor = scalable == 0 ? 1.0 : (double) remaining / scalable;
        for (Map<String, Object> step : steps) {
            if (Boolean.TRUE.equals(step.get("scalable"))) {
                int d = intVal(step.get("durationMin"), 0);
                step.put("durationMin", Math.max(5, (int) Math.round(d * factor)));
            }
        }
        // Fix rounding so sum == budget
        int sum = steps.stream().mapToInt(s -> intVal(s.get("durationMin"), 0)).sum();
        int delta = budgetMinutes - sum;
        if (delta != 0) {
            for (int i = steps.size() - 1; i >= 0; i--) {
                if (Boolean.TRUE.equals(steps.get(i).get("scalable"))) {
                    steps.get(i).put("durationMin", intVal(steps.get(i).get("durationMin"), 0) + delta);
                    break;
                }
            }
        }
        recomputeStarts(steps);
    }

    private static void recomputeStarts(List<Map<String, Object>> steps) {
        int start = 0;
        for (Map<String, Object> step : steps) {
            step.put("startMin", start);
            start += intVal(step.get("durationMin"), 0);
        }
    }

    private void insertDemoStep(List<Map<String, Object>> steps) {
        if (steps.isEmpty()) {
            return;
        }
        Map<String, Object> demo = new LinkedHashMap<>();
        demo.put("id", "s1b");
        demo.put("order", 2);
        demo.put("title", "Open the product and capture one screenshot");
        demo.put("startMin", 15);
        demo.put("durationMin", 15);
        demo.put("action", "Open the product, run one prompt, and screenshot the result for the short.");
        demo.put("input", "facts.md (from s1)");
        demo.put("output", "demo.png");
        demo.put("doneWhen", "One screenshot showing a real result");
        demo.put("toolId", "obs_studio");
        demo.put("altToolId", "sharex");
        demo.put("ifStuck", "Skip the live demo and keep the short as a news explainer.");
        demo.put("platforms", List.of("all"));
        demo.put("scalable", false);
        steps.add(1, demo);
        for (int i = 0; i < steps.size(); i++) {
            steps.get(i).put("order", i + 1);
        }
    }

    public static Map<String, String> fallbackSlots(NewsBrief brief) {
        Map<String, String> slots = new LinkedHashMap<>();
        ensureSlots(slots, brief);
        return slots;
    }

    public static void ensureSlots(Map<String, String> slots, NewsBrief brief) {
        List<NewsBrief.Fact> facts = brief.facts() == null ? List.of() : brief.facts();
        String entity = brief.entities() != null && !brief.entities().isEmpty()
                ? brief.entities().get(0)
                : firstEntity(brief.headline());
        String f1 = facts.size() > 0 ? facts.get(0).text() : brief.headline();
        String f2 = facts.size() > 1 ? facts.get(1).text() : "Who it affects is in the article.";
        String f3 = facts.size() > 2 ? facts.get(2).text() : "Here is why it matters today.";
        slots.putIfAbsent("publisherUrl", brief.publisherUrl() == null ? "" : brief.publisherUrl());
        slots.putIfAbsent("headline", brief.headline() == null ? "" : brief.headline());
        slots.putIfAbsent("publisher", brief.publisher() == null ? "" : brief.publisher());
        slots.putIfAbsent("hook", entity + ": " + trimWords(f1, 12));
        slots.putIfAbsent("point_1", trimWords(f1, 20));
        slots.putIfAbsent("point_2", trimWords(f2, 20));
        slots.putIfAbsent("point_3", trimWords(f3, 20));
        slots.putIfAbsent("cta", "Follow for the next product brief. Link in comments.");
        slots.putIfAbsent("caption_youtube", slots.get("hook") + " #Shorts");
        slots.putIfAbsent("caption_instagram", slots.get("hook"));
        slots.putIfAbsent("caption_linkedin", slots.get("hook"));
        slots.putIfAbsent("title", trimWords(entity + " — " + f1, 12));
        // Keep combined script inside a short-vertical word band (~90–130).
        fitScriptWords(slots, 90, 130);
    }

    static void fitScriptWords(Map<String, String> slots, int min, int max) {
        String script = String.join(" ",
                slots.getOrDefault("hook", ""),
                slots.getOrDefault("point_1", ""),
                slots.getOrDefault("point_2", ""),
                slots.getOrDefault("point_3", ""),
                slots.getOrDefault("cta", "")).trim();
        int words = script.isEmpty() ? 0 : script.split("\\s+").length;
        if (words < min) {
            slots.put("point_3", slots.getOrDefault("point_3", "")
                    + " This update matters for people watching AI tools this week who need a plain read before the story goes stale.");
            fitScriptWords(slots, min, max);
            return;
        }
        if (words > max) {
            slots.put("point_3", trimWords(slots.getOrDefault("point_3", ""), 12));
            slots.put("point_2", trimWords(slots.getOrDefault("point_2", ""), 14));
            slots.put("point_1", trimWords(slots.getOrDefault("point_1", ""), 14));
            String again = String.join(" ",
                    slots.getOrDefault("hook", ""),
                    slots.getOrDefault("point_1", ""),
                    slots.getOrDefault("point_2", ""),
                    slots.getOrDefault("point_3", ""),
                    slots.getOrDefault("cta", "")).trim();
            int againWords = again.isEmpty() ? 0 : again.split("\\s+").length;
            if (againWords > max) {
                slots.put("hook", trimWords(slots.getOrDefault("hook", ""), 16));
            }
        }
    }

    private static List<String> fillAll(List<String> lines, Map<String, String> slots) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(fill(line, slots));
        }
        return out;
    }

    private static String fill(String text, Map<String, String> slots) {
        String out = text;
        for (Map.Entry<String, String> e : slots.entrySet()) {
            out = out.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    private static String firstEntity(String headline) {
        if (headline == null || headline.isBlank()) {
            return "this news";
        }
        String[] parts = headline.trim().split("\\s+");
        return parts[0].replaceAll("[^A-Za-z0-9.].*", "");
    }

    private static String trimWords(String text, int max) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] parts = text.trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(max, parts.length); i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(parts[i]);
        }
        return out.toString();
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int intVal(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return fallback;
        }
    }
}
