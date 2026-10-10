package com.gyanwire.plans;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gyanwire.persistence.FlowStore;
import com.gyanwire.research.ResearchException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class CalibrationService {

    public static final int MIN_SAMPLES = 20;

    private final FlowStore store;
    private final ObjectMapper mapper;

    public CalibrationService(FlowStore store, ObjectMapper mapper) {
        this.store = store;
        this.mapper = mapper;
    }

    public Map<String, Object> recordActual(
            UUID userId,
            UUID ideaId,
            int weekNo,
            String optionId,
            String taskType,
            double hoursActual,
            Double baselineHours
    ) {
        if (optionId == null || optionId.isBlank()) {
            throw new ResearchException("Send optionId.", "VALIDATION_ERROR", 400);
        }
        if (taskType == null || taskType.isBlank()) {
            throw new ResearchException("Send taskType.", "VALIDATION_ERROR", 400);
        }
        if (weekNo < 1 || weekNo > 16) {
            throw new ResearchException("weekNo must be 1–16.", "VALIDATION_ERROR", 400);
        }
        if (hoursActual < 0 || hoursActual > 168) {
            throw new ResearchException("hoursActual must be between 0 and 168.", "VALIDATION_ERROR", 400);
        }
        String type = taskType.trim().toLowerCase(Locale.ROOT);
        store.savePlanActual(userId, ideaId, weekNo, optionId.trim(), type, hoursActual);
        boolean calibrated = maybeCalibrate(optionId.trim(), type, baselineHours);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("saved", true);
        out.put("calibrated", calibrated);
        List<Map<String, Object>> agg = store.optionTaskAggregates(optionId.trim(), type);
        out.put("sampleCount", agg.isEmpty() ? 0 : agg.get(0).get("n"));
        return out;
    }

    boolean maybeCalibrate(String optionId, String taskType, Double baselineHours) {
        List<Map<String, Object>> agg = store.optionTaskAggregates(optionId, taskType);
        if (agg.isEmpty()) {
            return false;
        }
        int n = ((Number) agg.get(0).get("n")).intValue();
        if (n < MIN_SAMPLES) {
            return false;
        }
        double avgHours = ((Number) agg.get(0).get("avgHours")).doubleValue();
        double base = baselineHours == null || baselineHours <= 0 ? WeekTaskType.HOURS_PER_SITTING : baselineHours;
        // speedup = baseline / actual; clamp to a modest range so outliers don't dominate
        double mid = clamp(base / Math.max(0.25, avgHours), 0.8, 2.0);
        double lo = clamp(mid * 0.9, 0.8, 2.0);
        double hi = clamp(mid * 1.1, 0.8, 2.0);
        if (lo > hi) {
            double tmp = lo;
            lo = hi;
            hi = tmp;
        }

        Map<String, Object> item = store.findCatalogItem(optionId);
        ObjectNode fit = mapper.createObjectNode();
        if (item != null && item.get("taskFit") instanceof Map<?, ?> existing) {
            fit = mapper.valueToTree(existing);
        }
        ObjectNode entry = mapper.createObjectNode();
        entry.putArray("speedup").add(round2(lo)).add(round2(hi));
        entry.put("confidence", "measured");
        entry.put("source", "aggregate plan_week_actuals");
        entry.put("sampleCount", n);
        fit.set(taskType, entry);
        try {
            store.updateOptionTaskFit(optionId, mapper.writeValueAsString(fit), n);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
