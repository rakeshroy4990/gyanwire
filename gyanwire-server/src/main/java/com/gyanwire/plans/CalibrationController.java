package com.gyanwire.plans;

import com.gyanwire.auth.security.AuthUserPrincipal;
import com.gyanwire.config.PlanLimitException;
import com.gyanwire.controller.dto.StandardApiResponse;
import com.gyanwire.research.ResearchException;
import com.gyanwire.usage.UsageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
public class CalibrationController {

    private final CalibrationService calibration;
    private final UsageService usage;

    public CalibrationController(CalibrationService calibration, UsageService usage) {
        this.calibration = calibration;
        this.usage = usage;
    }

    @PostMapping("/api/plan/actuals")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> actuals(@RequestBody Map<String, Object> body) {
        UUID ideaId = uuid(body.get("ideaId"));
        int weekNo = body.get("weekNo") instanceof Number n ? n.intValue() : -1;
        String optionId = String.valueOf(body.getOrDefault("optionId", ""));
        String taskType = String.valueOf(body.getOrDefault("taskType", ""));
        double hours = body.get("hoursActual") instanceof Number n ? n.doubleValue() : -1;
        Double baseline = body.get("baselineHours") instanceof Number n ? n.doubleValue() : null;
        Map<String, Object> data = calibration.recordActual(requireUser().id(), ideaId, weekNo, optionId, taskType, hours, baseline);
        return ResponseEntity.ok(StandardApiResponse.success("Saved.", data));
    }

    @PostMapping("/api/plan/variants/meter")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> meterVariants(@RequestBody Map<String, Object> body) {
        int count = body.get("count") instanceof Number n ? n.intValue() : 0;
        int max = usage.maxPlanVariants(requireUser().id());
        if (count > max) {
            throw new PlanLimitException(
                    "Free keeps one plan variant. Upgrade to compare A / B / C.",
                    "LIMIT_REACHED",
                    Map.of("upgradeUrl", "/pricing", "maxPlanVariants", max, "requested", count)
            );
        }
        return ResponseEntity.ok(StandardApiResponse.success("OK", Map.of("allowed", true, "maxPlanVariants", max)));
    }

    private static AuthUserPrincipal requireUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUserPrincipal principal)) {
            throw new ResearchException("Sign in to continue.", "AUTH_UNAUTHORIZED", 401);
        }
        return principal;
    }

    private static UUID uuid(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(raw));
        } catch (Exception e) {
            return null;
        }
    }
}
