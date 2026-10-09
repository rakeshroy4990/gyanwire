package com.gyanwire.digests;

import com.gyanwire.persistence.FlowStore;
import com.gyanwire.research.service.SearchEngineService;
import com.gyanwire.usage.UsageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true")
public class DigestJob {

    private static final Logger log = LoggerFactory.getLogger(DigestJob.class);

    private final FlowStore store;
    private final SearchEngineService search;
    private final UsageService usage;
    private final String owner;

    public DigestJob(
            FlowStore store,
            SearchEngineService search,
            UsageService usage,
            @Value("${app.jobs.owner:gyanwire}") String owner
    ) {
        this.store = store;
        this.search = search;
        this.usage = usage;
        this.owner = owner;
    }

    @Scheduled(cron = "${app.jobs.digest-cron:0 30 2 * * *}")
    public void nightly() {
        if (!store.tryLock("digest", owner)) {
            return;
        }
        try {
            store.flagStaleCatalog();
            for (Map<String, Object> saved : store.savedQueries()) {
                try {
                    sendSaved(saved);
                } catch (Exception rowError) {
                    log.info("Skipped a saved query: {}", rowError.getMessage());
                }
            }
        } catch (Exception ignored) {
            // Price rows stay as stored when the catalog table is unavailable.
        }
        log.info("Digest job locked by {}", owner);
    }

    private void sendSaved(Map<String, Object> saved) {
        UUID userId = (UUID) saved.get("userId");
        String query = String.valueOf(saved.getOrDefault("query", "")).trim();
        String industry = String.valueOf(saved.getOrDefault("industry", "IT")).trim();
        if (userId == null || query.length() < 8) {
            return;
        }
        Map<String, Object> plan = usage.resolveUserPlan(userId);
        String planId = String.valueOf(plan.get("id"));
        if (!"pro".equals(planId) && !"team".equals(planId)) {
            return;
        }
        long used = usage.countTodaySearches(userId, "digest");
        Map<String, Object> evaluation = UsageService.evaluateSearchLimit(used, ((Number) plan.get("dailySearchLimit")).intValue());
        if (!(Boolean) evaluation.get("allowed")) {
            return;
        }
        Map<String, Object> result = search.findBestResults(userId, List.of(industry.isBlank() ? "IT" : industry), null, query, 5, null, null);
        usage.recordSearchUsage(userId, "digest", BigDecimal.ZERO);
        List<String> urls = new ArrayList<>();
        if (result.get("results") instanceof List<?> rows) {
            for (Object row : rows) {
                if (row instanceof Map<?, ?> map && map.get("url") != null) {
                    urls.add(String.valueOf(map.get("url")));
                }
            }
        }
        int sent = sendNew(userId, urls, store.digestHashes(userId));
        if (sent > 0) {
            log.info("Digest email for {} with {} new links", userId, sent);
        }
    }

    public int sendNew(UUID userId, List<String> urls, Set<String> already) {
        int sent = 0;
        for (String url : DigestDeduper.unseen(already, urls)) {
            if (store.markDigest(userId, DigestDeduper.urlHash(url))) {
                log.info("Digest url for {} {}", userId, url);
                sent++;
            }
        }
        return sent;
    }
}
