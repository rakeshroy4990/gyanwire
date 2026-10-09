package com.gyanwire.billing.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.auth.security.AuthUserPrincipal;
import com.gyanwire.billing.BillingException;
import com.gyanwire.billing.service.RazorpayBillingService;
import com.gyanwire.controller.dto.StandardApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final RazorpayBillingService billingService;
    private final ObjectMapper objectMapper;

    public BillingController(RazorpayBillingService billingService, ObjectMapper objectMapper) {
        this.billingService = billingService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/checkout")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> checkout(@RequestBody Map<String, Object> body) {
        if (!billingService.isConfigured()) {
            throw new BillingException("Billing is not configured yet.", "BILLING_UNCONFIGURED", 503);
        }
        AuthUserPrincipal user = requireUser();
        String planId = String.valueOf(body.getOrDefault("planId", "")).trim().toLowerCase();
        String interval = String.valueOf(body.getOrDefault("interval", "monthly")).trim().toLowerCase();
        if ("student".equals(planId)) {
            if (!"monthly".equals(interval) || !RazorpayBillingService.studentEmail(user.email())) {
                throw new BillingException("Student checkout needs a .ac.in or .edu email and a monthly plan.", "VALIDATION_ERROR", 400);
            }
        } else if (!Set.of("pro", "team").contains(planId) || !Set.of("monthly", "annual").contains(interval)) {
            throw new BillingException("Choose Pro or Team and a billing interval.", "VALIDATION_ERROR", 400);
        }
        Map<String, Object> data = billingService.createCheckout(user.id(), user.email(), planId, interval);
        return ResponseEntity.ok(StandardApiResponse.success("Checkout ready.", data));
    }

    @PostMapping("/cancel")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> cancel() {
        AuthUserPrincipal user = requireUser();
        Map<String, Object> data = billingService.cancelSubscription(user.id());
        return ResponseEntity.ok(StandardApiResponse.success("Subscription will end at the current period.", data));
    }

    @GetMapping("/offers")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> offers() {
        return ResponseEntity.ok(StandardApiResponse.success("OK", billingService.offers()));
    }

    @GetMapping("/status")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> status() {
        AuthUserPrincipal user = requireUser();
        return ResponseEntity.ok(StandardApiResponse.success("OK", billingService.getBillingStatus(user.id())));
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature
    ) throws Exception {
        billingService.verifyWebhookSignature(rawBody, signature);
        JsonNode event = objectMapper.readTree(rawBody);
        Map<String, Object> result = billingService.handleWebhookEvent(event);
        Map<String, Object> out = new java.util.HashMap<>(result);
        out.put("success", true);
        return ResponseEntity.ok(out);
    }

    private static AuthUserPrincipal requireUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUserPrincipal p)) {
            throw new BillingException("Not authenticated.", "AUTH_UNAUTHORIZED", 401);
        }
        return p;
    }
}
