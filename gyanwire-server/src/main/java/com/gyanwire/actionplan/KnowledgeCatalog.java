package com.gyanwire.actionplan;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class KnowledgeCatalog {

    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final Map<String, MoneyRule> moneyRules = new LinkedHashMap<>();
    private final Map<String, Platform> platforms = new LinkedHashMap<>();

    public KnowledgeCatalog() {
        loadTools();
        loadMoney();
        loadPlatforms();
    }

    public Optional<Tool> tool(String id) {
        return Optional.ofNullable(tools.get(id));
    }

    public Optional<MoneyRule> moneyRule(String id) {
        return Optional.ofNullable(moneyRules.get(id));
    }

    public Optional<Platform> platform(String id) {
        return Optional.ofNullable(platforms.get(id));
    }

    public Map<String, Tool> tools() {
        return Map.copyOf(tools);
    }

    public Map<String, MoneyRule> moneyRules() {
        return Map.copyOf(moneyRules);
    }

    public Map<String, Platform> platforms() {
        return Map.copyOf(platforms);
    }

    public boolean freeOrOpen(String tier) {
        return "free".equals(tier) || "open_source".equals(tier) || "freemium".equals(tier);
    }

    public boolean stale(String lastVerified, int days) {
        if (lastVerified == null || lastVerified.isBlank()) {
            return true;
        }
        try {
            LocalDate when = LocalDate.parse(lastVerified.trim());
            return ChronoUnit.DAYS.between(when, LocalDate.now()) > days;
        } catch (Exception ex) {
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    private void loadTools() {
        for (Map<String, Object> row : loadList("classpath:actionplan/tools.yml")) {
            String id = str(row.get("id"));
            if (id.isBlank()) {
                continue;
            }
            tools.put(id, new Tool(
                    id,
                    str(row.get("name")),
                    str(row.get("tier")),
                    str(row.get("license")),
                    stringList(row.get("covers")),
                    stringList(row.get("platforms")),
                    str(row.get("url")),
                    str(row.get("freeLimit")),
                    intOrNull(row.get("priceInrMonth")),
                    str(row.get("priceVerifiedOn")),
                    str(row.get("lastVerified"))));
        }
    }

    private static Integer intOrNull(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void loadMoney() {
        for (Map<String, Object> row : loadList("classpath:actionplan/money-rules.yml")) {
            String id = str(row.get("id"));
            if (id.isBlank()) {
                continue;
            }
            moneyRules.put(id, new MoneyRule(
                    id,
                    str(row.get("claim")),
                    stringList(row.get("appliesTo")),
                    Boolean.TRUE.equals(row.get("honestForNewChannel")),
                    str(row.get("lastVerified"))));
        }
    }

    @SuppressWarnings("unchecked")
    private void loadPlatforms() {
        for (Map<String, Object> row : loadList("classpath:actionplan/platforms.yml")) {
            String id = str(row.get("id"));
            if (id.isBlank()) {
                continue;
            }
            platforms.put(id, new Platform(
                    id,
                    str(row.get("label")),
                    stringList(row.get("rules")),
                    str(row.get("lastVerified"))));
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadList(String location) {
        try {
            Resource[] files = new PathMatchingResourcePatternResolver().getResources(location);
            Yaml yaml = new Yaml();
            List<Map<String, Object>> out = new ArrayList<>();
            for (Resource file : files) {
                try (InputStream in = file.getInputStream()) {
                    Object loaded = yaml.load(in);
                    if (loaded instanceof List<?> list) {
                        for (Object item : list) {
                            if (item instanceof Map<?, ?> map) {
                                out.add((Map<String, Object>) map);
                            }
                        }
                    }
                }
            }
            return out;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
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

    public record Tool(
            String id,
            String name,
            String tier,
            String license,
            List<String> covers,
            List<String> platforms,
            String url,
            String freeLimit,
            Integer priceInrMonth,
            String priceVerifiedOn,
            String lastVerified
    ) {
        public boolean isOpenSource() {
            return "open_source".equals(tier);
        }

        public boolean isFreeNotOpenSource() {
            return "free".equals(tier) || "freemium".equals(tier);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", id);
            out.put("name", name);
            out.put("tier", tier);
            out.put("license", license == null || license.isBlank()
                    ? (isOpenSource() ? "see project" : "proprietary")
                    : license);
            out.put("url", url);
            out.put("freeLimit", freeLimit);
            out.put("priceInrMonth", priceInrMonth);
            out.put("priceVerifiedOn", priceVerifiedOn == null || priceVerifiedOn.isBlank()
                    ? null
                    : priceVerifiedOn);
            out.put("lastVerified", lastVerified);
            out.put("openSource", isOpenSource());
            return out;
        }
    }

    public record MoneyRule(
            String id,
            String claim,
            List<String> appliesTo,
            boolean honestForNewChannel,
            String lastVerified
    ) {
    }

    public record Platform(
            String id,
            String label,
            List<String> rules,
            String lastVerified
    ) {
    }
}
