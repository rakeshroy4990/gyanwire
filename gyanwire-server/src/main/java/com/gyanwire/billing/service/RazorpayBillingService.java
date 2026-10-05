package com.gyanwire.billing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.billing.BillingException;
import com.gyanwire.persistence.postgres.model.BillingEventEntity;
import com.gyanwire.persistence.postgres.model.PlanEntity;
import com.gyanwire.persistence.postgres.model.SubscriptionEntity;
import com.gyanwire.persistence.postgres.repository.BillingEventRepository;
import com.gyanwire.persistence.postgres.repository.PlanRepository;
import com.gyanwire.persistence.postgres.repository.SubscriptionRepository;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Subscription;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class RazorpayBillingService {

    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private final String proMonthly;
    private final String proAnnual;
    private final String teamMonthly;
    private final String teamAnnual;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final BillingEventRepository billingEventRepository;
    private final ObjectMapper objectMapper;

    public RazorpayBillingService(
            @Value("${app.razorpay.key-id:}") String keyId,
            @Value("${app.razorpay.key-secret:}") String keySecret,
            @Value("${app.razorpay.webhook-secret:}") String webhookSecret,
            @Value("${app.razorpay.plan.pro.monthly:}") String proMonthly,
            @Value("${app.razorpay.plan.pro.annual:}") String proAnnual,
            @Value("${app.razorpay.plan.team.monthly:}") String teamMonthly,
            @Value("${app.razorpay.plan.team.annual:}") String teamAnnual,
            SubscriptionRepository subscriptionRepository,
            PlanRepository planRepository,
            BillingEventRepository billingEventRepository,
            ObjectMapper objectMapper
    ) {
        this.keyId = keyId == null ? "" : keyId.trim();
        this.keySecret = keySecret == null ? "" : keySecret.trim();
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        this.proMonthly = proMonthly == null ? "" : proMonthly.trim();
        this.proAnnual = proAnnual == null ? "" : proAnnual.trim();
        this.teamMonthly = teamMonthly == null ? "" : teamMonthly.trim();
        this.teamAnnual = teamAnnual == null ? "" : teamAnnual.trim();
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.billingEventRepository = billingEventRepository;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return !keyId.isBlank() && !keySecret.isBlank();
    }

    private RazorpayClient client() {
        if (!isConfigured()) {
            throw new BillingException("Billing is not configured yet.", "BILLING_UNCONFIGURED", 503);
        }
        try {
            return new RazorpayClient(keyId, keySecret);
        } catch (RazorpayException e) {
            throw new BillingException("Unable to initialize Razorpay.", "BILLING_ERROR", 500);
        }
    }

    private String resolveRazorpayPlanId(String planId, String interval) {
        String plan = planId == null ? "" : planId.trim().toLowerCase();
        String intv = interval == null ? "monthly" : interval.trim().toLowerCase();
        if ("free".equals(plan)) {
            throw new BillingException("Free plan does not require checkout.", "BILLING_FREE_PLAN", 400);
        }
        String id = switch (plan + ":" + intv) {
            case "pro:monthly" -> proMonthly;
            case "pro:annual" -> proAnnual;
            case "team:monthly" -> teamMonthly;
            case "team:annual" -> teamAnnual;
            default -> "";
        };
        if (id.isBlank()) {
            if (!"pro".equals(plan) && !"team".equals(plan)) {
                throw new BillingException("Unsupported plan or billing interval.", "BILLING_PLAN_INVALID", 400);
            }
            throw new BillingException("Missing Razorpay plan id env for " + plan + "/" + intv,
                    "BILLING_PLAN_UNCONFIGURED", 503);
        }
        return id;
    }

    public void verifyWebhookSignature(byte[] rawBody, String signature) {
        if (webhookSecret.isBlank()) {
            throw new BillingException("RAZORPAY_WEBHOOK_SECRET is not set.", "BILLING_WEBHOOK_UNCONFIGURED", 503);
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(rawBody));
            String actual = signature == null ? "" : signature.trim();
            if (expected.length() != actual.length() || !MessageDigestEquals(expected, actual)) {
                throw new BillingException("Invalid Razorpay webhook signature.", "BILLING_WEBHOOK_INVALID", 400);
            }
        } catch (BillingException e) {
            throw e;
        } catch (Exception e) {
            throw new BillingException("Invalid Razorpay webhook signature.", "BILLING_WEBHOOK_INVALID", 400);
        }
    }

    private static boolean MessageDigestEquals(String a, String b) {
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) return false;
        int r = 0;
        for (int i = 0; i < x.length; i++) r |= x[i] ^ y[i];
        return r == 0;
    }

    @Transactional
    public Map<String, Object> createCheckout(UUID userId, String email, String planId, String interval) {
        String normalizedPlan = planId == null ? "" : planId.trim().toLowerCase();
        String intv = interval == null ? "monthly" : interval.trim().toLowerCase();
        String razorpayPlanId = resolveRazorpayPlanId(normalizedPlan, intv);
        try {
            JSONObject req = new JSONObject();
            req.put("plan_id", razorpayPlanId);
            req.put("total_count", "annual".equals(intv) ? 10 : 120);
            req.put("customer_notify", 1);
            JSONObject notes = new JSONObject();
            notes.put("gyanwire_user_id", userId.toString());
            notes.put("gyanwire_plan_id", normalizedPlan);
            notes.put("gyanwire_interval", intv);
            req.put("notes", notes);
            Subscription subscription = client().subscriptions.create(req);
            String subscriptionId = String.valueOf(subscription.get("id"));
            String status = subscription.has("status") ? String.valueOf(subscription.get("status")) : "created";
            Instant periodEnd = null;
            if (subscription.has("current_end") && subscription.get("current_end") != null) {
                Object end = subscription.get("current_end");
                if (end instanceof Number n) {
                    periodEnd = Instant.ofEpochSecond(n.longValue());
                } else {
                    try {
                        periodEnd = Instant.ofEpochSecond(Long.parseLong(String.valueOf(end)));
                    } catch (Exception ignored) {
                        periodEnd = null;
                    }
                }
            }
            upsertSubscription(userId, normalizedPlan, status, subscriptionId, periodEnd, false);
            Map<String, Object> out = new HashMap<>();
            out.put("subscriptionId", subscriptionId);
            out.put("keyId", keyId);
            out.put("planId", normalizedPlan);
            out.put("interval", intv);
            out.put("email", email);
            return out;
        } catch (RazorpayException e) {
            throw new BillingException(e.getMessage(), "BILLING_ERROR", 500);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getBillingStatus(UUID userId) {
        Optional<SubscriptionEntity> opt = subscriptionRepository.findFirstByUserIdOrderByUpdatedAtDesc(userId);
        if (opt.isEmpty()) {
            Map<String, Object> none = new HashMap<>();
            none.put("planId", "free");
            none.put("planName", "Free");
            none.put("status", "none");
            none.put("provider", null);
            none.put("providerSubscriptionId", null);
            none.put("currentPeriodEnd", null);
            none.put("cancelAtPeriodEnd", false);
            return none;
        }
        SubscriptionEntity s = opt.get();
        String planName = planRepository.findById(s.getPlanId()).map(PlanEntity::getName).orElse(s.getPlanId());
        Map<String, Object> out = new HashMap<>();
        out.put("planId", s.getPlanId());
        out.put("planName", planName);
        out.put("status", s.getStatus());
        out.put("provider", s.getProvider());
        out.put("providerSubscriptionId", s.getProviderSubscriptionId());
        out.put("currentPeriodEnd", s.getCurrentPeriodEnd());
        out.put("cancelAtPeriodEnd", s.isCancelAtPeriodEnd());
        return out;
    }

    @Transactional
    public Map<String, Object> cancelSubscription(UUID userId) {
        Map<String, Object> status = getBillingStatus(userId);
        String providerSubId = status.get("providerSubscriptionId") == null ? null : String.valueOf(status.get("providerSubscriptionId"));
        if (providerSubId == null || "free".equals(status.get("planId"))) {
            throw new BillingException("No active paid subscription to cancel.", "BILLING_NO_SUBSCRIPTION", 400);
        }
        try {
            JSONObject opts = new JSONObject();
            opts.put("cancel_at_cycle_end", 1);
            client().subscriptions.cancel(providerSubId, opts);
        } catch (RazorpayException e) {
            throw new BillingException(e.getMessage(), "BILLING_ERROR", 500);
        }
        subscriptionRepository.findFirstByProviderAndProviderSubscriptionId("razorpay", providerSubId)
                .ifPresent(s -> {
                    s.setCancelAtPeriodEnd(true);
                    subscriptionRepository.save(s);
                });
        return getBillingStatus(userId);
    }

    @Transactional
    public Map<String, Object> handleWebhookEvent(JsonNode event) {
        String eventId = event.path("id").asText("").trim();
        String eventType = event.path("event").asText("").trim();
        if (eventId.isBlank() || eventType.isBlank()) {
            throw new BillingException("Webhook payload missing id or event.", "BILLING_WEBHOOK_MALFORMED", 400);
        }
        if (billingEventRepository.existsByEventId(eventId)) {
            return Map.of("duplicate", true, "eventType", eventType);
        }
        BillingEventEntity be = new BillingEventEntity();
        be.setEventId(eventId);
        be.setEventType(eventType);
        try {
            be.setPayloadJson(objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            be.setPayloadJson("{}");
        }
        billingEventRepository.save(be);

        JsonNode subscriptionEntity = event.path("payload").path("subscription").path("entity");
        if (subscriptionEntity.isMissingNode() || subscriptionEntity.isNull()) {
            return Map.of("duplicate", false, "eventType", eventType, "ignored", true);
        }
        String providerSubscriptionId = subscriptionEntity.path("id").asText("").trim();
        if (providerSubscriptionId.isBlank()) {
            return Map.of("duplicate", false, "eventType", eventType, "ignored", true);
        }
        String userIdRaw = subscriptionEntity.path("notes").path("gyanwire_user_id").asText("").trim();
        UUID userId = null;
        try {
            if (!userIdRaw.isBlank()) userId = UUID.fromString(userIdRaw);
        } catch (Exception ignored) {}
        String planId = subscriptionEntity.path("notes").path("gyanwire_plan_id").asText("pro").trim().toLowerCase();
        if (!"pro".equals(planId) && !"team".equals(planId)) planId = "pro";
        Instant periodEnd = subscriptionEntity.hasNonNull("current_end")
                ? Instant.ofEpochSecond(subscriptionEntity.path("current_end").asLong())
                : null;

        switch (eventType) {
            case "subscription.activated", "subscription.charged" ->
                    upsertSubscription(userId, planId, "active", providerSubscriptionId, periodEnd, false);
            case "subscription.halted" ->
                    upsertSubscription(userId, planId, "halted", providerSubscriptionId, periodEnd, false);
            case "subscription.cancelled" ->
                    upsertSubscription(userId, planId, "cancelled", providerSubscriptionId, periodEnd, false);
            case "subscription.completed" ->
                    upsertSubscription(userId, planId, "completed", providerSubscriptionId, periodEnd, false);
            default -> {
                return Map.of("duplicate", false, "eventType", eventType, "ignored", true);
            }
        }
        return Map.of("duplicate", false, "eventType", eventType, "ignored", false);
    }

    private void upsertSubscription(
            UUID userId,
            String planId,
            String status,
            String providerSubscriptionId,
            Instant currentPeriodEnd,
            boolean cancelAtPeriodEnd
    ) {
        Optional<SubscriptionEntity> existing = subscriptionRepository
                .findFirstByProviderAndProviderSubscriptionId("razorpay", providerSubscriptionId);
        if (existing.isPresent()) {
            SubscriptionEntity s = existing.get();
            if (userId != null) s.setUserId(userId);
            s.setPlanId(planId);
            s.setStatus(status);
            s.setCurrentPeriodEnd(currentPeriodEnd);
            s.setCancelAtPeriodEnd(cancelAtPeriodEnd);
            subscriptionRepository.save(s);
            return;
        }
        if (userId == null) {
            return;
        }
        subscriptionRepository.markReplacedExcept(userId, providerSubscriptionId);
        SubscriptionEntity s = new SubscriptionEntity();
        s.setUserId(userId);
        s.setPlanId(planId);
        s.setStatus(status);
        s.setProvider("razorpay");
        s.setProviderSubscriptionId(providerSubscriptionId);
        s.setCurrentPeriodEnd(currentPeriodEnd);
        s.setCancelAtPeriodEnd(cancelAtPeriodEnd);
        subscriptionRepository.save(s);
    }
}
