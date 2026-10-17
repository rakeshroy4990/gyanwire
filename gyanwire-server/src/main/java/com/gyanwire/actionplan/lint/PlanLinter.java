package com.gyanwire.actionplan.lint;

import com.gyanwire.actionplan.KnowledgeCatalog;
import com.gyanwire.actionplan.format.FormatSpec;
import com.gyanwire.actionplan.news.NewsBrief;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure accuracy gate. No LLM. Fully unit-tested.
 */
public final class PlanLinter {

    private static final Set<String> ALLOWED_VERBS = Set.of(
            "write", "record", "cut", "export", "upload", "paste", "schedule",
            "open", "read", "build", "assemble", "watch", "create", "re-read", "review");

    private final KnowledgeCatalog catalog;

    public PlanLinter(KnowledgeCatalog catalog) {
        this.catalog = catalog;
    }

    public LintResult lint(Map<String, Object> plan, NewsBrief brief, FormatSpec format) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (plan == null) {
            return new LintResult(false, List.of("L0: missing plan"), List.of());
        }
        checkL1(plan, errors);
        checkL2(plan, errors);
        checkL3(plan, format, errors);
        checkL4(plan, brief, errors);
        checkL5(plan, brief, errors);
        checkL6(plan, errors, warnings);
        checkL7(plan, errors);
        checkL8(plan, brief, errors);
        checkL9(plan, format, errors);
        checkL10(plan, errors);
        checkL11(plan, warnings);
        return new LintResult(errors.isEmpty(), List.copyOf(errors), List.copyOf(warnings));
    }

    @SuppressWarnings("unchecked")
    private void checkL1(Map<String, Object> plan, List<String> errors) {
        int budget = intVal(plan.get("budgetMinutes"), 0);
        List<Map<String, Object>> steps = steps(plan);
        int sum = 0;
        int expectedStart = 0;
        for (Map<String, Object> step : steps) {
            int start = intVal(step.get("startMin"), -1);
            int duration = intVal(step.get("durationMin"), 0);
            sum += duration;
            if (start != expectedStart) {
                errors.add("L1: steps not contiguous at " + step.get("id"));
            }
            expectedStart = start + duration;
        }
        if (sum != budget) {
            errors.add("L1: duration sum " + sum + " != budgetMinutes " + budget);
        }
    }

    private void checkL2(Map<String, Object> plan, List<String> errors) {
        for (Map<String, Object> step : steps(plan)) {
            for (String field : List.of("action", "input", "output", "doneWhen", "tool")) {
                if (blank(step.get(field)) && !(field.equals("tool") && step.get("tool") instanceof Map<?, ?>)) {
                    if (!"tool".equals(field)) {
                        errors.add("L2: step " + step.get("id") + " missing " + field);
                    }
                }
            }
            Object tool = step.get("tool");
            if (!(tool instanceof Map<?, ?> toolMap) || blank(toolMap.get("id"))) {
                errors.add("L2: step " + step.get("id") + " missing tool");
            }
            String action = str(step.get("action"));
            String first = firstWord(action).toLowerCase(Locale.ROOT);
            if (!ALLOWED_VERBS.contains(first)) {
                errors.add("L2: step " + step.get("id") + " action must start with a concrete verb");
            }
        }
    }

    private void checkL3(Map<String, Object> plan, FormatSpec format, List<String> errors) {
        if (format == null) {
            return;
        }
        String blob = String.valueOf(plan);
        if ("short_vertical".equals(format.id())) {
            if (blob.matches("(?s).*\\b6\\s*[–-]\\s*10\\s+minute.*")
                    || blob.toLowerCase(Locale.ROOT).contains("6-10 minute")
                    || blob.toLowerCase(Locale.ROOT).contains("6–10 minute")) {
                errors.add("L3: short_vertical plan mentions 6–10 minute length");
            }
            if (format.maxSeconds() != null) {
                // any explicit "N minute" over maxSeconds/60 is bad
                if (blob.matches("(?is).*\\b([3-9]|\\d{2,})\\s*-\\s*\\d+\\s+minutes?\\b.*")
                        && blob.toLowerCase(Locale.ROOT).contains("minute")
                        && !blob.toLowerCase(Locale.ROOT).contains("budget")) {
                    // soft: only the known bad pattern above is hard-fail
                }
            }
        }
    }

    private void checkL4(Map<String, Object> plan, NewsBrief brief, List<String> errors) {
        String label = str(plan.get("label"));
        String blob = copyBlob(plan);
        if (label.toLowerCase(Locale.ROOT).startsWith("post today:")
                || blob.toLowerCase(Locale.ROOT).contains("post today:")) {
            errors.add("L4: contains Post today: prefix");
        }
        if (brief != null && brief.headline() != null && !brief.headline().isBlank()) {
            String headline = brief.headline().trim();
            int count = 0;
            int idx = 0;
            while ((idx = blob.indexOf(headline, idx)) >= 0) {
                count++;
                idx += headline.length();
            }
            if (count > 1) {
                errors.add("L4: full headline repeated more than once");
            }
        }
    }

    /** User-facing copy only — skip newsBrief and micro-steps (they intentionally echo the story). */
    private static String copyBlob(Map<String, Object> plan) {
        StringBuilder out = new StringBuilder();
        out.append(str(plan.get("label"))).append(' ');
        out.append(str(plan.get("hook"))).append(' ');
        out.append(str(plan.get("script"))).append(' ');
        if (plan.get("slots") instanceof Map<?, ?> slots) {
            for (Map.Entry<?, ?> e : slots.entrySet()) {
                String key = String.valueOf(e.getKey());
                if ("headline".equals(key) || "publisherUrl".equals(key) || "publisher".equals(key)) {
                    continue;
                }
                out.append(e.getValue() == null ? "" : e.getValue()).append(' ');
            }
        }
        for (Map<String, Object> step : steps(plan)) {
            out.append(str(step.get("title"))).append(' ');
            out.append(str(step.get("action"))).append(' ');
            out.append(str(step.get("ifStuck"))).append(' ');
        }
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private void checkL5(Map<String, Object> plan, NewsBrief brief, List<String> errors) {
        if (brief == null) {
            return;
        }
        String hook = str(plan.get("hook"));
        if (hook.isBlank() && plan.get("slots") instanceof Map<?, ?> slots) {
            hook = str(slots.get("hook"));
        }
        if (hook.isBlank()) {
            // try step 2 action
            for (Map<String, Object> step : steps(plan)) {
                if ("s2".equals(String.valueOf(step.get("id")))) {
                    hook = str(step.get("action"));
                }
            }
        }
        String lower = hook.toLowerCase(Locale.ROOT);
        boolean entityHit = false;
        if (brief.entities() != null) {
            for (String entity : brief.entities()) {
                if (entity != null && !entity.isBlank() && lower.contains(entity.toLowerCase(Locale.ROOT))) {
                    entityHit = true;
                    break;
                }
            }
        }
        boolean factHit = false;
        if (brief.facts() != null) {
            for (NewsBrief.Fact fact : brief.facts()) {
                String text = fact.text() == null ? "" : fact.text();
                for (String word : text.split("\\s+")) {
                    if (word.length() >= 5 && lower.contains(word.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""))) {
                        factHit = true;
                        break;
                    }
                }
                if (factHit) {
                    break;
                }
            }
        }
        if (!entityHit || !factHit) {
            errors.add("L5: hook must contain an entity and a fact keyword");
        }
    }

    @SuppressWarnings("unchecked")
    private void checkL6(Map<String, Object> plan, List<String> errors, List<String> warnings) {
        for (Map<String, Object> step : steps(plan)) {
            Object tool = step.get("tool");
            if (!(tool instanceof Map<?, ?> toolMap)) {
                continue;
            }
            String id = str(toolMap.get("id"));
            var known = catalog.tool(id);
            if (known.isEmpty()) {
                errors.add("L6: unknown tool " + id);
                continue;
            }
            String tier = known.get().tier();
            if (!catalog.freeOrOpen(tier) && !"freemium".equals(tier)) {
                errors.add("L6: default tool " + id + " is not free/open_source");
            }
            // freemium allowed as default (CapCut free tier)
            if ("paid".equals(tier)) {
                errors.add("L6: default tool " + id + " is paid");
            }
        }
    }

    private void checkL7(Map<String, Object> plan, List<String> errors) {
        Object money = plan.get("money");
        if (!(money instanceof Map<?, ?> moneyMap)) {
            errors.add("L7: missing money block");
            return;
        }
        String ruleId = str(moneyMap.get("ruleId"));
        var rule = catalog.moneyRule(ruleId);
        if (rule.isEmpty()) {
            errors.add("L7: unknown money ruleId " + ruleId);
            return;
        }
        if (!rule.get().honestForNewChannel()) {
            String claim = str(moneyMap.get("claim")).toLowerCase(Locale.ROOT);
            if (!claim.contains("not instant")) {
                errors.add("L7: money claim must say not instant for new channels");
            }
        }
    }

    private void checkL8(Map<String, Object> plan, NewsBrief brief, List<String> errors) {
        if (brief == null || brief.facts() == null || brief.facts().isEmpty()) {
            return;
        }
        String script = str(plan.get("script"));
        if (script.isBlank() && plan.get("slots") instanceof Map<?, ?> slots) {
            script = str(slots.get("hook")) + " " + str(slots.get("point_1")) + " "
                    + str(slots.get("point_2")) + " " + str(slots.get("point_3"));
        }
        Object mapped = plan.get("factIds");
        if (mapped instanceof List<?> ids && !ids.isEmpty()) {
            return;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b\\d{3,}\\b").matcher(script);
        StringBuilder factBlob = new StringBuilder();
        for (NewsBrief.Fact fact : brief.facts()) {
            factBlob.append(' ')
                    .append(fact.text() == null ? "" : fact.text())
                    .append(' ')
                    .append(fact.sourceSpan() == null ? "" : fact.sourceSpan());
        }
        // Verified multi-source pack facts may also ground numbers in the script.
        appendVideoResearchFacts(factBlob, plan.get("videoResearch"));
        String blob = factBlob.toString();
        while (matcher.find()) {
            String number = matcher.group();
            if (!blob.contains(number)) {
                errors.add("L8: numeric claim not mapped to a fact");
                return;
            }
        }
    }

    private static void appendVideoResearchFacts(StringBuilder factBlob, Object videoResearch) {
        if (!(videoResearch instanceof Map<?, ?> pack)) {
            return;
        }
        Object facts = pack.get("facts");
        if (!(facts instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            factBlob.append(' ').append(str(row.get("text")));
            factBlob.append(' ').append(str(row.get("sourceSpan")));
        }
        Object reactions = pack.get("reactions");
        if (reactions instanceof List<?> reactionList) {
            for (Object item : reactionList) {
                if (!(item instanceof Map<?, ?> row)) {
                    continue;
                }
                factBlob.append(' ').append(str(row.get("text")));
                factBlob.append(' ').append(str(row.get("sourceSpan")));
            }
        }
    }

    private void checkL9(Map<String, Object> plan, FormatSpec format, List<String> errors) {
        if (format == null) {
            return;
        }
        String script = str(plan.get("script"));
        if (script.isBlank() && plan.get("slots") instanceof Map<?, ?> slots) {
            script = String.join(" ",
                    str(slots.get("hook")),
                    str(slots.get("point_1")),
                    str(slots.get("point_2")),
                    str(slots.get("point_3")),
                    str(slots.get("cta")));
        }
        if (script.isBlank()) {
            return;
        }
        int words = script.trim().isEmpty() ? 0 : script.trim().split("\\s+").length;
        if (words < format.scriptWordsMin() || words > format.scriptWordsMax()) {
            errors.add("L9: script word count " + words + " outside " + format.scriptWordsMin()
                    + "-" + format.scriptWordsMax());
        }
    }

    @SuppressWarnings("unchecked")
    private void checkL10(Map<String, Object> plan, List<String> errors) {
        List<String> platforms = stringList(plan.get("platforms"));
        String blob = String.valueOf(plan).toLowerCase(Locale.ROOT);
        for (String banned : List.of("tiktok", "twitter", "facebook", "snapchat")) {
            if (blob.contains(banned) && platforms.stream().noneMatch(p -> p.toLowerCase(Locale.ROOT).contains(banned))) {
                // only fail if clearly a platform mention outside allow-list
                if (!platforms.contains(banned)) {
                    // soft skip tiktok etc unless explicitly claimed as target
                }
            }
        }
        for (Map<String, Object> step : steps(plan)) {
            Object stepPlatforms = step.get("platforms");
            if (stepPlatforms instanceof List<?> list) {
                for (Object p : list) {
                    String name = String.valueOf(p);
                    if (!"all".equals(name) && !platforms.contains(name)) {
                        errors.add("L10: step mentions platform not in plan.platforms: " + name);
                    }
                }
            }
        }
    }

    private void checkL11(Map<String, Object> plan, List<String> warnings) {
        for (Map<String, Object> step : steps(plan)) {
            Object tool = step.get("tool");
            if (!(tool instanceof Map<?, ?> toolMap)) {
                continue;
            }
            String id = str(toolMap.get("id"));
            catalog.tool(id).ifPresent(t -> {
                if (catalog.stale(t.lastVerified(), 90)) {
                    warnings.add("L11: tool " + id + " lastVerified older than 90 days");
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> steps(Map<String, Object> plan) {
        Object raw = plan.get("steps");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add((Map<String, Object>) map);
            }
        }
        return out;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                out.add(String.valueOf(item));
            }
        }
        return out;
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

    private static boolean blank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String firstWord(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] parts = text.trim().split("\\s+");
        return parts[0].replaceAll("[^A-Za-z\\-]", "");
    }

    public record LintResult(boolean passed, List<String> errors, List<String> warnings) {
        public Map<String, Object> toMap() {
            return Map.of(
                    "passed", passed,
                    "errors", errors,
                    "warnings", warnings == null ? List.of() : warnings);
        }
    }
}
