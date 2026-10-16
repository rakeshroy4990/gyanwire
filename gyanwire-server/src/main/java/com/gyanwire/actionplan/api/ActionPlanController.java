package com.gyanwire.actionplan.api;

import com.gyanwire.actionplan.ActionPlanPipeline;
import com.gyanwire.actionplan.ActionPlanStore;
import com.gyanwire.actionplan.StepDetailService;
import com.gyanwire.auth.security.AuthUserPrincipal;
import com.gyanwire.controller.dto.StandardApiResponse;
import com.gyanwire.research.ResearchException;
import com.gyanwire.usage.UsageService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
public class ActionPlanController {

    private final ActionPlanPipeline pipeline;
    private final ActionPlanStore store;
    private final StepDetailService stepDetails;

    public ActionPlanController(ActionPlanPipeline pipeline, ActionPlanStore store, StepDetailService stepDetails) {
        this.pipeline = pipeline;
        this.store = store;
        this.stepDetails = stepDetails;
    }

    @PostMapping("/api/action-plans")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> create(
            @Valid @RequestBody CreateRequest body,
            HttpServletRequest request
    ) {
        UUID userId = optionalUserId();
        String ipHash = UsageService.hashIp(request.getRemoteAddr());
        Integer budget = body.budgetMinutes();
        boolean demo = Boolean.TRUE.equals(body.demo());
        boolean save = Boolean.TRUE.equals(body.save()) && userId != null;
        Map<String, Object> plan = pipeline.generate(
                userId,
                ipHash,
                body.newsUrl(),
                body.title() == null ? "" : body.title(),
                body.source() == null ? "" : body.source(),
                budget,
                body.pastedText(),
                demo,
                save,
                body.format());
        return ResponseEntity.ok(StandardApiResponse.success("Action plan ready.", plan));
    }

    @GetMapping("/api/action-plans/{id}")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> get(@PathVariable UUID id) {
        UUID userId = optionalUserId();
        Map<String, Object> plan = store.findPlan(id, userId);
        if (plan == null && userId != null) {
            plan = store.findPlan(id, null);
        }
        if (plan == null) {
            throw new ResearchException("Plan not found.", "NOT_FOUND", 404);
        }
        return ResponseEntity.ok(StandardApiResponse.success("OK", plan));
    }

    @PatchMapping("/api/action-plans/{id}/steps/{stepKey}")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> toggleStep(
            @PathVariable UUID id,
            @PathVariable String stepKey,
            @RequestBody Map<String, Object> body
    ) {
        UUID userId = requireUser().id();
        boolean done = Boolean.TRUE.equals(body.get("done")) || "true".equalsIgnoreCase(String.valueOf(body.get("done")));
        store.setStepDone(id, userId, stepKey, done);
        Map<String, Object> plan = store.findPlan(id, userId);
        return ResponseEntity.ok(StandardApiResponse.success("Updated.", plan == null ? Map.of("done", done) : plan));
    }

    @GetMapping("/api/action-plans")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> list(
            @RequestParam(name = "mine", defaultValue = "false") boolean mine
    ) {
        if (!mine) {
            return ResponseEntity.ok(StandardApiResponse.success("OK", Map.of("plans", java.util.List.of())));
        }
        UUID userId = requireUser().id();
        return ResponseEntity.ok(StandardApiResponse.success("OK", Map.of("plans", store.listMine(userId))));
    }

    private static UUID optionalUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUserPrincipal principal) {
            return principal.id();
        }
        return null;
    }

    private static AuthUserPrincipal requireUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUserPrincipal principal)) {
            throw new ResearchException("Sign in to continue.", "AUTH_UNAUTHORIZED", 401);
        }
        return principal;
    }

    @GetMapping("/api/action-plans/{id}/steps/{stepKey}")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> stepDetail(
            @PathVariable UUID id,
            @PathVariable String stepKey,
            @RequestParam(name = "path", defaultValue = "open_source") String path
    ) {
        UUID userId = optionalUserId();
        Map<String, Object> plan = store.findPlan(id, userId);
        if (plan == null && userId != null) {
            plan = store.findPlan(id, null);
        }
        if (plan == null) {
            throw new ResearchException("Plan not found.", "NOT_FOUND", 404);
        }
        Map<String, Object> detail = stepDetails.build(plan, stepKey, path);
        return ResponseEntity.ok(StandardApiResponse.success("OK", detail));
    }

    @PostMapping("/api/action-plans/{id}/steps/{stepKey}/timing")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> stepTiming(
            @PathVariable UUID id,
            @PathVariable String stepKey,
            @RequestBody Map<String, Object> body
    ) {
        UUID userId = requireUser().id();
        int minutes = body.get("minutes") instanceof Number n ? n.intValue() : -1;
        if (minutes < 0 || minutes > 24 * 60) {
            throw new ResearchException("Send minutes between 0 and 1440.", "VALIDATION_ERROR", 400);
        }
        String toolId = body.get("toolId") == null ? null : String.valueOf(body.get("toolId"));
        store.saveTiming(userId, id, stepKey, toolId, minutes);
        return ResponseEntity.ok(StandardApiResponse.success("Saved.", Map.of(
                "planId", id.toString(),
                "stepKey", stepKey,
                "minutes", minutes)));
    }

    @GetMapping("/api/action-plans/tools")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> tools(
            @RequestParam(name = "step", required = false) String step,
            @RequestParam(name = "format", required = false) String format
    ) {
        return ResponseEntity.ok(StandardApiResponse.success("OK", Map.of(
                "tools", stepDetails.toolsFor(step, format))));
    }

    public record CreateRequest(
            @NotBlank String newsUrl,
            String title,
            String source,
            String type,
            Integer budgetMinutes,
            String pastedText,
            Boolean demo,
            Boolean save,
            String format
    ) {
    }
}
