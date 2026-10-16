package com.gyanwire.actionplan.format;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class FormatRegistry {

    private final Map<String, FormatSpec> formats = new LinkedHashMap<>();
    private final Map<String, List<Map<String, Object>>> stepTemplates = new LinkedHashMap<>();

    public FormatRegistry() {
        loadFormats();
        loadTemplates();
    }

    public Optional<FormatSpec> get(String id) {
        return Optional.ofNullable(formats.get(id));
    }

    public FormatSpec require(String id) {
        return get(id).orElseThrow(() -> new IllegalArgumentException("Unknown format: " + id));
    }

    public List<Map<String, Object>> stepTemplate(String name) {
        return stepTemplates.getOrDefault(name, List.of());
    }

    /**
     * Default to short_vertical when budget ≤ 240. youtube_long only when budget ≥ 480 and demo.
     */
    public FormatSpec select(int budgetMinutes, boolean demo) {
        return select(budgetMinutes, demo, null);
    }

    public FormatSpec select(int budgetMinutes, boolean demo, String formatId) {
        if (formatId != null && !formatId.isBlank() && formats.containsKey(formatId.trim())) {
            return formats.get(formatId.trim());
        }
        if (budgetMinutes >= 480 && demo && formats.containsKey("youtube_long")) {
            return formats.get("youtube_long");
        }
        return formats.getOrDefault("short_vertical", formats.values().iterator().next());
    }

    @SuppressWarnings("unchecked")
    private void loadFormats() {
        try {
            Resource[] files = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:actionplan/formats/*.yml");
            Yaml yaml = new Yaml();
            for (Resource file : files) {
                try (InputStream in = file.getInputStream()) {
                    Map<String, Object> row = yaml.load(in);
                    if (row == null) {
                        continue;
                    }
                    String id = str(row.get("id"));
                    if (id.isBlank()) {
                        continue;
                    }
                    formats.put(id, new FormatSpec(
                            id,
                            str(row.get("label")),
                            stringList(row.get("platforms")),
                            str(row.get("aspect")),
                            str(row.get("resolution")),
                            intOrNull(row.get("maxSeconds")),
                            intPair(row.get("targetSeconds"), 35, 50),
                            intPair(row.get("scriptWords"), 90, 130),
                            intOr(row.get("defaultBudgetMinutes"), 240),
                            str(row.get("stepTemplate")),
                            str(row.get("moneyRuleId")),
                            Boolean.TRUE.equals(row.get("requiresDemo")),
                            mapList(row.get("beats"))));
                }
            }
        } catch (Exception ignored) {
            // Empty registry fails closed in select().
        }
    }

    @SuppressWarnings("unchecked")
    private void loadTemplates() {
        try {
            Resource[] files = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:actionplan/templates/*.yml");
            Yaml yaml = new Yaml();
            for (Resource file : files) {
                try (InputStream in = file.getInputStream()) {
                    Map<String, Object> row = yaml.load(in);
                    if (row == null) {
                        continue;
                    }
                    String name = file.getFilename();
                    Object steps = row.get("steps");
                    if (name != null && steps instanceof List<?> list) {
                        List<Map<String, Object>> out = new ArrayList<>();
                        for (Object item : list) {
                            if (item instanceof Map<?, ?> map) {
                                out.add(new LinkedHashMap<>((Map<String, Object>) map));
                            }
                        }
                        stepTemplates.put(name, out);
                    }
                }
            }
        } catch (Exception ignored) {
            // Composer falls back to empty steps.
        }
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int intOr(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static Integer intOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private static int[] intPair(Object value, int a, int b) {
        if (value instanceof List<?> list && list.size() >= 2) {
            return new int[]{intOr(list.get(0), a), intOr(list.get(1), b)};
        }
        return new int[]{a, b};
    }

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return out;
    }
}
