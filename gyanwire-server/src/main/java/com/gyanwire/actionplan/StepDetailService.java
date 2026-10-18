package com.gyanwire.actionplan;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class StepDetailService {

    private final KnowledgeCatalog catalog;
    /** format id → stepKey → content block */
    private final Map<String, Map<String, Map<String, Object>>> contentByFormat;

    public StepDetailService(KnowledgeCatalog catalog) {
        this.catalog = catalog;
        this.contentByFormat = new LinkedHashMap<>();
        this.contentByFormat.put("short_vertical", loadFormatSteps("short_vertical"));
        this.contentByFormat.put("youtube_long", loadFormatSteps("youtube_long"));
    }

    public Map<String, Object> build(Map<String, Object> plan, String stepKey, String path) {
        String key = stepKey == null ? "" : stepKey.trim();
        Map<String, Object> step = findStep(plan, key);
        if (step == null) {
            throw new ActionPlanException("Step not found.", "NOT_FOUND", 404, Map.of("stepKey", key));
        }
        String format = str(plan.get("format"));
        if (format.isBlank()) {
            format = "short_vertical";
        }
        Map<String, Object> content = contentFor(format, key);
        String pathNorm = normalizePath(path);
        Map<String, String> slots = slotMap(plan);

        int osMin = intVal(step.get("durationMin"), intVal(contentDeep(content, "open_source", "minutes"), 15));
        int paidMin = intVal(step.get("paidDurationMin"), intVal(contentDeep(content, "paid", "minutes"), osMin));
        double gainPct = osMin <= 0 ? 0 : Math.round(((osMin - paidMin) * 1000.0 / osMin)) / 10.0;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stepKey", key);
        out.put("title", step.get("title"));
        out.put("order", step.get("order"));
        out.put("goal", content.getOrDefault("goal", step.get("doneWhen")));
        out.put("input", step.get("input"));
        out.put("output", step.get("output"));
        out.put("doneWhen", step.get("doneWhen"));
        out.put("ifStuck", step.get("ifStuck"));
        out.put("commonMistakes", content.getOrDefault("commonMistakes", ""));
        out.put("path", pathNorm);
        out.put("openSourceMinutes", osMin);
        out.put("paidMinutes", paidMin);
        out.put("gain", Map.of(
                "pct", gainPct,
                "basis", "estimate",
                "label", "estimate"));

        Map<String, Object> osTab = pathTab(content, "open_source", osMin, slots, step.get("microSteps"));
        Map<String, Object> paidTab = pathTab(content, "paid", paidMin, slots, step.get("paidMicroSteps"));
        paidTab.put("giveUp", contentDeep(content, "paid", "giveUp"));
        paidTab.put("whatChanges", "Paid micro-steps replace the open-source how-to for this step.");
        out.put("openSource", osTab);
        out.put("paid", paidTab);
        out.put("freeNotOpenSource", freeNotOs(content));

        List<Map<String, Object>> tools = new ArrayList<>();
        tools.addAll(toolMaps(listOf(contentDeep(content, pathNorm.equals("paid") ? "paid" : "open_source", "tools"))));
        out.put("tools", tools);
        out.put("activeMicroSteps", pathNorm.equals("paid")
                ? paidTab.get("microSteps")
                : osTab.get("microSteps"));
        return out;
    }

    /** Raw micro-steps for a format/step/path, before slot fill. Empty when unknown. */
    public List<String> microSteps(String format, String stepKey, String path) {
        Map<String, Object> content = contentFor(format, stepKey);
        String pathNorm = normalizePath(path);
        Object block = content.get(pathNorm.equals("paid") ? "paid" : "open_source");
        if (!(block instanceof Map<?, ?> map)) {
            return List.of();
        }
        return listOf(map.get("microSteps"));
    }

    public List<Map<String, Object>> toolsFor(String step, String format) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (KnowledgeCatalog.Tool tool : catalog.tools().values()) {
            if (step != null && !step.isBlank() && tool.covers() != null
                    && tool.covers().stream().noneMatch(c -> c.contains(step) || step.contains(c))) {
                // still include if no cover match when step blank
            }
            out.add(tool.toMap());
        }
        if (format != null && !format.isBlank()) {
            Map<String, Object> content = contentFor(format, step);
            if (!content.isEmpty()) {
                List<Map<String, Object>> focused = new ArrayList<>();
                focused.addAll(toolMaps(listOf(contentDeep(content, "open_source", "tools"))));
                focused.addAll(toolMaps(listOf(contentDeep(content, "paid", "tools"))));
                focused.addAll(toolMaps(listOf(content.get("free_not_os"))));
                if (!focused.isEmpty()) {
                    return focused;
                }
            }
        }
        return out;
    }

    private Map<String, Object> pathTab(
            Map<String, Object> content,
            String key,
            int minutes,
            Map<String, String> slots,
            Object stepOverride
    ) {
        Map<String, Object> tab = new LinkedHashMap<>();
        Object block = content.get(key);
        List<String> micro = List.of();
        List<String> toolIds = List.of();
        if (block instanceof Map<?, ?> map) {
            micro = listOf(map.get("microSteps"));
            toolIds = listOf(map.get("tools"));
        }
        List<String> fromTemplate = fillAll(micro, slots);
        List<String> fromStep = listOf(stepOverride);
        // Prefer catalog templates so step detail can use fresh research summary slots.
        if (!fromTemplate.isEmpty()) {
            micro = fromTemplate;
        } else if (!fromStep.isEmpty()) {
            micro = fromStep;
        }
        tab.put("microSteps", micro);
        tab.put("minutes", minutes);
        tab.put("tools", toolMaps(toolIds));
        tab.put("basis", "estimate");
        return tab;
    }

    private Map<String, Object> contentFor(String format, String stepKey) {
        String fmt = format == null || format.isBlank() ? "short_vertical" : format.trim();
        Map<String, Map<String, Object>> steps = contentByFormat.getOrDefault(fmt, Map.of());
        return steps.getOrDefault(stepKey == null ? "" : stepKey.trim(), Map.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> slotMap(Map<String, Object> plan) {
        Map<String, String> slots = new LinkedHashMap<>();
        Object raw = plan.get("slots");
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null) {
                    slots.put(String.valueOf(e.getKey()), e.getValue() == null ? "" : String.valueOf(e.getValue()));
                }
            }
        }
        Object brief = plan.get("newsBrief");
        if (brief instanceof Map<?, ?> news) {
            slots.putIfAbsent("publisherUrl", str(news.get("publisherUrl")));
            slots.putIfAbsent("headline", str(news.get("headline")));
            slots.putIfAbsent("publisher", str(news.get("publisher")));
            Object entities = news.get("entities");
            if (entities instanceof List<?> list && !list.isEmpty() && list.get(0) != null) {
                slots.putIfAbsent("entity", String.valueOf(list.get(0)).trim());
            }
            Object facts = news.get("facts");
            if (facts instanceof List<?> list) {
                int i = 1;
                for (Object item : list) {
                    if (i > 3) {
                        break;
                    }
                    if (item instanceof Map<?, ?> fact) {
                        String text = str(fact.get("text"));
                        if (!text.isBlank()) {
                            slots.putIfAbsent("fact_" + i, text);
                            i++;
                        }
                    } else if (item != null) {
                        String text = String.valueOf(item).trim();
                        if (!text.isBlank()) {
                            slots.putIfAbsent("fact_" + i, text);
                            i++;
                        }
                    }
                }
                String f1 = slots.getOrDefault("fact_1", "");
                String f2 = slots.getOrDefault("fact_2", "");
                String story = (f1 + " " + f2).trim();
                if (!story.isBlank()) {
                    slots.putIfAbsent("whatHappened", story);
                }
                if (!slots.getOrDefault("fact_3", "").isBlank()) {
                    slots.putIfAbsent("whyItMatters", slots.get("fact_3"));
                }
            }
            slots.putIfAbsent("whatHappened", str(news.get("headline")));
            slots.putIfAbsent("entity", firstWord(str(news.get("headline"))));
        }
        Object research = plan.get("videoResearch");
        if (research instanceof Map<?, ?> pack) {
            String what = str(pack.get("whatHappened"));
            String why = str(pack.get("whyItMatters"));
            if (!what.isBlank()) {
                slots.put("whatHappened", what);
            }
            if (!why.isBlank()) {
                slots.put("whyItMatters", why);
            }
            Object packFacts = pack.get("facts");
            if (packFacts instanceof List<?> list) {
                int i = 1;
                for (Object item : list) {
                    if (i > 3) {
                        break;
                    }
                    if (item instanceof Map<?, ?> fact) {
                        String text = str(fact.get("text"));
                        if (!text.isBlank()) {
                            slots.put("fact_" + i, text);
                            i++;
                        }
                    }
                }
            }
        }
        slots.putIfAbsent("entity", firstWord(slots.getOrDefault("headline", "this news")));
        slots.putIfAbsent("whatHappened", slots.getOrDefault("headline", ""));
        slots.putIfAbsent("whyItMatters", slots.getOrDefault("fact_3", "Here is why it matters today."));
        slots.putIfAbsent("fact_1", slots.getOrDefault("whatHappened", ""));
        slots.putIfAbsent("fact_2", "Who it affects is in the article.");
        slots.putIfAbsent("fact_3", slots.getOrDefault("whyItMatters", ""));
        return slots;
    }

    private static List<String> fillAll(List<String> lines, Map<String, String> slots) {
        if (lines == null || lines.isEmpty() || slots == null || slots.isEmpty()) {
            return lines == null ? List.of() : lines;
        }
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(fill(line, slots));
        }
        return out;
    }

    private static String fill(String text, Map<String, String> slots) {
        String out = text == null ? "" : text;
        for (Map.Entry<String, String> e : slots.entrySet()) {
            out = out.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    private List<Map<String, Object>> freeNotOs(Map<String, Object> content) {
        return toolMaps(listOf(content.get("free_not_os")));
    }

    private List<Map<String, Object>> toolMaps(List<String> ids) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (ids == null) {
            return out;
        }
        for (String id : ids) {
            catalog.tool(id).ifPresentOrElse(
                    t -> out.add(t.toMap()),
                    () -> {
                        Map<String, Object> stub = new LinkedHashMap<>();
                        stub.put("id", id);
                        stub.put("name", id);
                        stub.put("tier", "unknown");
                        stub.put("openSource", false);
                        out.add(stub);
                    });
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findStep(Map<String, Object> plan, String key) {
        Object steps = plan.get("steps");
        if (!(steps instanceof List<?> list)) {
            return null;
        }
        for (Object row : list) {
            if (row instanceof Map<?, ?> map) {
                Map<String, Object> step = (Map<String, Object>) map;
                if (key.equals(str(step.get("id"))) || key.equals(str(step.get("stepKey")))) {
                    return step;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> loadFormatSteps(String formatId) {
        try {
            ClassPathResource resource = new ClassPathResource(
                    "actionplan/content/steps/" + formatId + "/index.yml");
            try (InputStream in = resource.getInputStream()) {
                Map<String, Object> root = new Yaml().load(in);
                Object steps = root.get("steps");
                if (steps instanceof Map<?, ?> map) {
                    Map<String, Map<String, Object>> out = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : map.entrySet()) {
                        if (e.getValue() instanceof Map<?, ?> v) {
                            out.put(String.valueOf(e.getKey()), (Map<String, Object>) v);
                        }
                    }
                    return out;
                }
            }
        } catch (Exception ignored) {
            // Empty catalog falls back to plan step fields.
        }
        return Map.of();
    }

    private static Object contentDeep(Map<String, Object> content, String block, String field) {
        Object b = content.get(block);
        if (b instanceof Map<?, ?> map) {
            return map.get(field);
        }
        return null;
    }

    private static String normalizePath(String path) {
        String p = path == null ? "open_source" : path.trim().toLowerCase(Locale.ROOT);
        if (p.equals("paid") || p.equals("mix")) {
            return p.equals("mix") ? "open_source" : "paid";
        }
        return "open_source";
    }

    private static List<String> listOf(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item == null) {
                continue;
            }
            if (item instanceof Map<?, ?> map && map.size() == 1) {
                Map.Entry<?, ?> e = map.entrySet().iterator().next();
                out.add(String.valueOf(e.getKey()) + ": " + String.valueOf(e.getValue()));
            } else {
                out.add(String.valueOf(item));
            }
        }
        return out;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String firstWord(String text) {
        if (text == null || text.isBlank()) {
            return "this news";
        }
        String[] parts = text.trim().split("\\s+");
        return parts[0].replaceAll("[^A-Za-z0-9.].*", "");
    }

    private static int intVal(Object v, int fallback) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception ex) {
            return fallback;
        }
    }
}
