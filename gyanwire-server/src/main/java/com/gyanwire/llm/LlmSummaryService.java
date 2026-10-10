package com.gyanwire.llm;

import com.gyanwire.news.NewsQualityService;
import com.gyanwire.persistence.postgres.model.LlmCallEntity;
import com.gyanwire.persistence.postgres.model.PlanCacheEntity;
import com.gyanwire.persistence.postgres.repository.LlmCallRepository;
import com.gyanwire.persistence.postgres.repository.PlanCacheRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmSummaryService {

    private final LlmCallRepository calls;
    private final PlanCacheRepository cache;
    private final LlmProperties properties;
    private final NewsQualityService news;

    public LlmSummaryService(
            LlmCallRepository calls,
            PlanCacheRepository cache,
            LlmProperties properties,
            NewsQualityService news
    ) {
        this.calls = calls;
        this.cache = cache;
        this.properties = properties;
        this.news = news;
    }

    public Map<String, Object> summary() {
        Instant now = Instant.now();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("capInr", properties.getDailySpendCapInr());
        out.put("today", window(now.minusSeconds(0), startOfToday()));
        out.put("days7", window(now.minusSeconds(7L * 86400), now.minusSeconds(7L * 86400)));
        out.put("days30", window(now.minusSeconds(30L * 86400), now.minusSeconds(30L * 86400)));
        long hits = 0;
        long rows = 0;
        for (PlanCacheEntity row : cache.findAll()) {
            rows++;
            hits += Math.max(0, row.getHits());
        }
        out.put("cacheRows", rows);
        out.put("cacheHits", hits);
        try {
            out.put("news", news.summary());
        } catch (Exception ex) {
            out.put("news", Map.of("error", "News quality is not ready."));
        }
        return out;
    }

    private Map<String, Object> window(Instant ignored, Instant start) {
        List<LlmCallEntity> rows = calls.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(start);
        BigDecimal total = BigDecimal.ZERO;
        int escalated = 0;
        Map<String, BigDecimal> byStage = new LinkedHashMap<>();
        Map<String, BigDecimal> byPlan = new LinkedHashMap<>();
        for (LlmCallEntity row : rows) {
            BigDecimal cost = row.getCostInr() == null ? BigDecimal.ZERO : row.getCostInr();
            total = total.add(cost);
            String stage = row.getStage() == null ? row.getFeature() : row.getStage();
            byStage.merge(stage, cost, BigDecimal::add);
            String plan = row.getPlanCode() == null ? "unknown" : row.getPlanCode();
            byPlan.merge(plan, cost, BigDecimal::add);
            if (row.getEscalatedFrom() != null && !row.getEscalatedFrom().isBlank()) {
                escalated++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("costInr", total);
        out.put("calls", rows.size());
        out.put("escalations", escalated);
        out.put("byStage", byStage);
        out.put("byPlan", byPlan);
        return out;
    }

    private static Instant startOfToday() {
        return LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
