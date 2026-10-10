package com.gyanwire.news;

import com.gyanwire.persistence.postgres.repository.LlmCallRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NewsQualityService {

    private static final Logger log = LoggerFactory.getLogger(NewsQualityService.class);

    private final NewsStore store;
    private final NewsProperties properties;
    private final LlmCallRepository calls;

    public NewsQualityService(NewsStore store, NewsProperties properties, LlmCallRepository calls) {
        this.store = store;
        this.properties = properties;
        this.calls = calls;
    }

    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> sources = store.sourceStatus();
        List<Map<String, Object>> industries = store.industryQuality(properties.getRank().windowDays()[0]);
        out.put("sources", sources);
        out.put("industries", industries);
        out.put("usefulness", store.feedbackBySignal());
        out.put("classifyCostInr30d", classifyCost());
        for (Map<String, Object> source : sources) {
            int streak = source.get("consecutiveFailures") instanceof Number n ? n.intValue() : 0;
            if (Boolean.TRUE.equals(source.get("enabled")) && streak >= properties.getAlert().getFailureStreak()) {
                log.warn("News source {} failed {} runs", source.get("name"), streak);
            }
        }
        for (Map<String, Object> industry : industries) {
            double median = industry.get("medianAgeDays") instanceof Number n ? n.doubleValue() : 0;
            if (median > properties.getAlert().getMedianAgeDays()) {
                log.warn("News median age for {} is {} days", industry.get("industry"), median);
            }
        }
        return out;
    }

    private BigDecimal classifyCost() {
        BigDecimal total = BigDecimal.ZERO;
        Instant start = Instant.now().minus(30, ChronoUnit.DAYS);
        for (var row : calls.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(start)) {
            String stage = row.getStage() == null ? row.getFeature() : row.getStage();
            if (!"news_classify".equals(stage)) {
                continue;
            }
            if (row.getCostInr() != null) {
                total = total.add(row.getCostInr());
            }
        }
        return total;
    }
}
