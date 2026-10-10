package com.gyanwire.usage;

import com.gyanwire.persistence.FlowStore;
import com.gyanwire.persistence.postgres.model.PlanEntity;
import com.gyanwire.persistence.postgres.model.SubscriptionEntity;
import com.gyanwire.persistence.postgres.model.UsageEventEntity;
import com.gyanwire.persistence.postgres.repository.PlanRepository;
import com.gyanwire.persistence.postgres.repository.SubscriptionRepository;
import com.gyanwire.persistence.postgres.repository.UsageEventRepository;
import com.gyanwire.persistence.postgres.repository.UserRepository;
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
    public static final int UNLIMITED = Integer.MAX_VALUE;
    public static final String DEFAULT_PLAN_ID = "free";

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UsageEventRepository usageEventRepository;
    private final UserRepository userRepository;
    private final FlowStore flowStore;

    public UsageService(
            PlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            UsageEventRepository usageEventRepository,
            UserRepository userRepository,
            FlowStore flowStore
    ) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.usageEventRepository = usageEventRepository;
        this.userRepository = userRepository;
        this.flowStore = flowStore;
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
            anon.put("allowsHighModel", false);
            return anon;
        }
        Map<String, Object> plan;
        List<SubscriptionEntity> active = subscriptionRepository.findActiveForUser(userId);
        if (!active.isEmpty()) {
            plan = planRepository.findById(active.get(0).getPlanId())
                    .map(this::toPlanMap)
                    .orElseGet(this::freePlanMap);
        } else {
            plan = planRepository.findById(DEFAULT_PLAN_ID).map(this::toPlanMap).orElseGet(this::freePlanMap);
        }
        int bonus = 0;
        try {
            bonus = flowStore.bonusSearches(userId);
        } catch (Exception ignored) {
            bonus = 0;
        }
        plan.put("dailySearchLimit", ((Number) plan.get("dailySearchLimit")).intValue() + bonus);
        if (isAdmin(userId)) {
            plan.put("dailyIdeaLimit", UNLIMITED);
        }
        return plan;
    }

    @Transactional(readOnly = true)
    public boolean isAdmin(UUID userId) {
        if (userId == null) {
            return false;
        }
        return userRepository.findActiveById(userId)
                .map(user -> "admin".equalsIgnoreCase(user.getRole()))
                .orElse(false);
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

    @Transactional(readOnly = true)
    public long countToday(UUID userId, String ipHash, String kind) {
        Instant start = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = start.plusSeconds(86400);
        if (userId != null) {
            return usageEventRepository.countUserKind(userId, kind, start, end);
        }
        if (ipHash == null || ipHash.isBlank()) {
            return 0;
        }
        return usageEventRepository.countAnonKind(ipHash, kind, start, end);
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
        recordUsage(userId, ipHash, "search", cost);
    }

    @Transactional
    public void recordUsage(UUID userId, String ipHash, String kind, BigDecimal cost) {
        UsageEventEntity event = new UsageEventEntity();
        event.setUserId(userId);
        event.setIpHash(userId == null ? ipHash : null);
        event.setKind(kind);
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
        out.put("dailyBriefLimit", plan.getDailyBriefLimit());
        out.put("dailyIdeaLimit", plan.getDailyIdeaLimit());
        out.put("canRoadmap", plan.isCanRoadmap());
        out.put("projectLimit", plan.getProjectLimit());
        out.put("maxPlanVariants", plan.getMaxPlanVariants());
        out.put("allowsHighModel", plan.isAllowsHighModel());
        return out;
    }

    @Transactional(readOnly = true)
    public boolean allowsHighModel(UUID userId) {
        if (userId == null) {
            return false;
        }
        return Boolean.TRUE.equals(resolveUserPlan(userId).get("allowsHighModel"));
    }

    @Transactional(readOnly = true)
    public int maxPlanVariants(UUID userId) {
        Map<String, Object> plan = resolveUserPlan(userId);
        Object raw = plan.get("maxPlanVariants");
        return raw instanceof Number n ? Math.max(1, n.intValue()) : 1;
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
            out.put("dailyBriefLimit", 1);
            out.put("dailyIdeaLimit", 3);
            out.put("canRoadmap", false);
            out.put("projectLimit", 1);
            out.put("maxPlanVariants", 1);
            out.put("allowsHighModel", false);
            return out;
        });
    }
}
