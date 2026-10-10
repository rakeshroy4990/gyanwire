package com.gyanwire.controller;

import com.gyanwire.auth.security.AuthUserPrincipal;
import com.gyanwire.controller.dto.StandardApiResponse;
import com.gyanwire.llm.LlmSummaryService;
import com.gyanwire.plans.WhatIfNarrator;
import com.gyanwire.usage.UsageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
public class LlmAdminController {

    private final LlmSummaryService summary;
    private final UsageService usage;
    private final WhatIfNarrator whatIf;

    public LlmAdminController(LlmSummaryService summary, UsageService usage, WhatIfNarrator whatIf) {
        this.summary = summary;
        this.usage = usage;
        this.whatIf = whatIf;
    }

    @GetMapping("/api/admin/llm/summary")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> summary() {
        UUID userId = currentUser();
        if (userId == null || !usage.isAdmin(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(StandardApiResponse.error("Admin only.", "FORBIDDEN"));
        }
        return ResponseEntity.ok(StandardApiResponse.success("LLM spend", summary.summary()));
    }

    @PostMapping("/api/plan/whatif-text")
    public ResponseEntity<StandardApiResponse<Map<String, String>>> whatIf(@RequestBody Map<String, Object> body) {
        int cost = body.get("costDelta") instanceof Number n ? n.intValue() : 0;
        int hours = body.get("hoursDelta") instanceof Number n ? n.intValue() : 0;
        String text = whatIf.explain(currentUser(), cost, hours);
        return ResponseEntity.ok(StandardApiResponse.success("What-if", Map.of("text", text)));
    }

    private static UUID currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUserPrincipal principal) {
            return principal.id();
        }
        return null;
    }
}
