package com.gyanwire.controller;

import com.gyanwire.billing.service.RazorpayBillingService;
import com.gyanwire.research.engine.LlmService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final LlmService llmService;
    private final RazorpayBillingService billingService;
    private final String jwtSecret;
    private final String persistenceProvider;

    public HealthController(
            LlmService llmService,
            RazorpayBillingService billingService,
            @Value("${app.auth.jwt.secret}") String jwtSecret,
            @Value("${app.persistence.provider:postgres}") String persistenceProvider
    ) {
        this.llmService = llmService;
        this.billingService = billingService;
        this.jwtSecret = jwtSecret;
        this.persistenceProvider = persistenceProvider;
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        data.put("engine", "gyanwire");
        data.put("engineReady", true);
        data.put("authReady", jwtSecret != null && jwtSecret.length() >= 16);
        data.put("persistenceProvider", persistenceProvider);
        data.put("llmConfigured", llmService.isConfigured());
        data.put("billingConfigured", billingService.isConfigured());
        data.put("service", "gyanwire-server");
        return ResponseEntity.ok(data);
    }
}
