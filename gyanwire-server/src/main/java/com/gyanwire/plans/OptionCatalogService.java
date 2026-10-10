package com.gyanwire.plans;

import com.gyanwire.persistence.FlowStore;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OptionCatalogService {

    private static final int STALE_DAYS = 30;

    private final FlowStore store;

    public OptionCatalogService(FlowStore store) {
        this.store = store;
    }

    public List<Map<String, Object>> list(String taskType) {
        String filter = taskType == null || taskType.isBlank() ? null : taskType.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : store.catalogItems()) {
            Map<String, Object> option = toOption(row);
            if (filter != null) {
                Object fit = option.get("taskFit");
                if (!(fit instanceof Map<?, ?> map) || !map.containsKey(filter)) {
                    continue;
                }
            }
            out.add(option);
        }
        out.sort(Comparator
                .comparing((Map<String, Object> o) -> monthlyInr(o) > 0)
                .thenComparing(o -> String.valueOf(o.getOrDefault("name", ""))));
        return out;
    }

    private Map<String, Object> toOption(Map<String, Object> row) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("id", row.get("id"));
        option.put("name", row.get("name"));
        option.put("kind", row.getOrDefault("kind", "tool"));
        String billing = String.valueOf(row.getOrDefault("billing", "free"));
        int cost = row.get("costInr") instanceof Number n ? n.intValue() : 0;
        option.put("monthlyInr", "monthly".equals(billing) ? cost : 0);
        option.put("costInr", cost);
        option.put("billing", billing);
        option.put("pricedAt", row.get("pricedAt"));
        option.put("stale", Boolean.TRUE.equals(row.get("stale")) || isStale(row.get("pricedAt")));
        option.put("taskFit", row.getOrDefault("taskFit", Map.of()));
        option.put("freeAlternativeId", row.get("freeAlternativeId"));
        option.put("bucket", row.get("bucket"));
        option.put("sampleCount", row.getOrDefault("sampleCount", 0));
        option.put("skills", row.getOrDefault("skills", List.of()));
        if (row.get("url") != null && !String.valueOf(row.get("url")).isBlank()) {
            option.put("url", String.valueOf(row.get("url")).trim());
        }
        if (row.get("blurb") != null && !String.valueOf(row.get("blurb")).isBlank()) {
            option.put("blurb", String.valueOf(row.get("blurb")).trim());
        }
        return option;
    }

    private static int monthlyInr(Map<String, Object> option) {
        Object v = option.get("monthlyInr");
        return v instanceof Number n ? n.intValue() : 0;
    }

    private static boolean isStale(Object pricedAt) {
        if (pricedAt == null) {
            return true;
        }
        try {
            LocalDate d = LocalDate.parse(String.valueOf(pricedAt));
            return ChronoUnit.DAYS.between(d, LocalDate.now()) > STALE_DAYS;
        } catch (Exception e) {
            return true;
        }
    }
}
