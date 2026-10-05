package com.gyanwire.usage;

import com.gyanwire.persistence.postgres.model.PlanEntity;
import com.gyanwire.persistence.postgres.model.SubscriptionEntity;
import com.gyanwire.persistence.postgres.model.UsageEventEntity;
import com.gyanwire.persistence.postgres.repository.PlanRepository;
import com.gyanwire.persistence.postgres.repository.SubscriptionRepository;
import com.gyanwire.persistence.postgres.repository.UsageEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UsageService {

    public static final int ANON_DAILY_SEARCH_LIMIT = 2;
    public static final String DEFAULT_PLAN_ID = "free";

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UsageEventRepository usageEventRepository;

    public UsageService(
            PlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            UsageEventRepository usageEventRepository
    ) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.usageEventRepository = usageEventRepository;
    }

    public static String hashIp(String ip) {
        try {
            String raw = (ip == null || ip.isBlank()) ? "unknown" : ip.trim();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "unknown";
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resolveUserPlan(UUID userId) {
        if (userId == null) {
            Map<String, Object> anon = freePlanMap();
            anon.put("id", "anonymous");
            anon.put("name", "Anonymous");
            anon.put("dailySearchLimit", ANON_DAILY_SEARCH_LIMIT);
            return anon;
        }
        List<SubscriptionEntity> active = subscriptionRepository.findActiveForUser(userId);
        if (!active.isEmpty()) {
            return planRepository.findById(active.get(0).getPlanId())
                    .map(this::toPlanMap)
                    .orElseGet(this::freePlanMap);
        }
        return planRepository.findById(DEFAULT_PLAN_ID).map(this::toPlanMap).orElseGet(this::freePlanMap);
    }

    @Transactional(readOnly = true)
    public long countTodaySearches(UUID userId, String ipHash) {
        Instant start = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = start.plusSeconds(86400);
        if (userId != null) {
            return usageEventRepository.countUserSearches(userId, start, end);
        }
        if (ipHash == null || ipHash.isBlank()) return 0;
        return usageEventRepository.countAnonSearches(ipHash, start, end);
    }

    public static Map<String, Object> evaluateSearchLimit(long used, int limit) {
        long safeUsed = Math.max(0, used);
        int safeLimit = Math.max(0, limit);
        long remaining = Math.max(0, safeLimit - safeUsed);
        Map<String, Object> out = new HashMap<>();
        out.put("allowed", safeUsed < safeLimit);
        out.put("used", safeUsed);
        out.put("limit", safeLimit);
        out.put("remaining", remaining);
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getUsageSnapshot(UUID userId, String ipHash) {
        Map<String, Object> plan = resolveUserPlan(userId);
        long used = countTodaySearches(userId, ipHash);
        Map<String, Object> evaluation = evaluateSearchLimit(used, ((Number) plan.get("dailySearchLimit")).intValue());
        Map<String, Object> out = new HashMap<>();
        out.put("plan", plan);
        out.put("searchesToday", evaluation.get("used"));
        out.put("searchesRemaining", evaluation.get("remaining"));
        out.put("searchLimit", evaluation.get("limit"));
        return out;
    }

    @Transactional
    public void recordSearchUsage(UUID userId, String ipHash, BigDecimal cost) {
        UsageEventEntity event = new UsageEventEntity();
        event.setUserId(userId);
        event.setIpHash(userId == null ? ipHash : null);
        event.setKind("search");
        event.setCostInrEstimate(cost == null ? BigDecimal.ZERO : cost);
        usageEventRepository.save(event);
    }

    private Map<String, Object> toPlanMap(PlanEntity plan) {
        Map<String, Object> out = new HashMap<>();
        out.put("id", plan.getId());
        out.put("name", plan.getName());
        out.put("dailySearchLimit", plan.getDailySearchLimit());
        out.put("canSave", plan.isCanSave());
        out.put("canExport", plan.isCanExport());
        out.put("canAlert", plan.isCanAlert());
        out.put("seats", plan.getSeats());
        return out;
    }

    private Map<String, Object> freePlanMap() {
        return planRepository.findById(DEFAULT_PLAN_ID).map(this::toPlanMap).orElseGet(() -> {
            Map<String, Object> out = new HashMap<>();
            out.put("id", "free");
            out.put("name", "Free");
            out.put("dailySearchLimit", 5);
            out.put("canSave", false);
            out.put("canExport", false);
            out.put("canAlert", false);
            out.put("seats", 1);
            return out;
        });
    }
}
