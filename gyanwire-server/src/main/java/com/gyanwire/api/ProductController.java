package com.gyanwire.api;

import com.gyanwire.auth.security.AuthUserPrincipal;
import com.gyanwire.ideas.IdeaService;
import com.gyanwire.persistence.FlowStore;
import com.gyanwire.plans.LearningPlanService;
import com.gyanwire.profile.ProfileService;
import com.gyanwire.projects.ProjectService;
import com.gyanwire.referrals.ReferralService;
import com.gyanwire.research.ResearchException;
import com.gyanwire.sources.SourcePackService;
import com.gyanwire.controller.dto.StandardApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
public class ProductController {

    private final ProfileService profiles;
    private final IdeaService ideas;
    private final LearningPlanService plans;
    private final ProjectService projects;
    private final SourcePackService packs;
    private final ReferralService referrals;
    private final FlowStore store;
    private final McpService mcp;

    public ProductController(
            ProfileService profiles,
            IdeaService ideas,
            LearningPlanService plans,
            ProjectService projects,
            SourcePackService packs,
            ReferralService referrals,
            FlowStore store,
            McpService mcp
    ) {
        this.profiles = profiles;
        this.ideas = ideas;
        this.plans = plans;
        this.projects = projects;
        this.packs = packs;
        this.referrals = referrals;
        this.store = store;
        this.mcp = mcp;
    }

    @GetMapping("/api/me/profile")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> getProfile() {
        return ResponseEntity.ok(StandardApiResponse.success("OK", profiles.get(requireUser().id())));
    }

    @PutMapping("/api/me/profile")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> putProfile(@Valid @RequestBody ProfileRequest body) {
        return ResponseEntity.ok(StandardApiResponse.success("Profile saved.", profiles.save(requireUser().id(), body.toMap())));
    }

    @DeleteMapping("/api/me/profile/data")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> deleteProfile() {
        profiles.delete(requireUser().id());
        return ResponseEntity.ok(StandardApiResponse.success("Profile deleted.", Map.of("deleted", true)));
    }

    @PostMapping("/api/ideas/from-news")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> fromNews(@RequestBody Map<String, Object> body) {
        String url = String.valueOf(body.getOrDefault("url", "")).trim();
        if (url.length() < 8 || !url.startsWith("http")) {
            throw new ResearchException("Send a news url.", "VALIDATION_ERROR", 400);
        }
        return ResponseEntity.ok(StandardApiResponse.success("Ideas ready.", ideas.fromNews(requireUser().id(), body)));
    }

    @GetMapping("/api/ideas")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> listIdeas() {
        return ResponseEntity.ok(StandardApiResponse.success("OK", Map.of("ideas", ideas.list(requireUser().id()))));
    }

    @PostMapping("/api/ideas/{id}/feedback")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> ideaFeedback(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body
    ) {
        Integer feedback = body.get("feedback") instanceof Number n ? n.intValue() : null;
        Boolean tried = body.get("tried") instanceof Boolean b ? b : null;
        ideas.feedback(requireUser().id(), id, feedback, tried);
        return ResponseEntity.ok(StandardApiResponse.success("Saved.", Map.of("id", id.toString())));
    }

    @PostMapping("/api/plans/skill")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> skill(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(StandardApiResponse.success("Budget ready.", plans.skillPlan(requireUser().id(), uuid(body.get("ideaId")))));
    }

    @PostMapping("/api/plans/weekly")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> weekly(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(StandardApiResponse.success("Weeks ready.", plans.weekly(requireUser().id(), uuid(body.get("ideaId")))));
    }

    @PostMapping("/api/plans/checkin")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> checkin(@RequestBody Map<String, Object> body) {
        UUID taskId = uuid(body.get("taskId"));
        if (taskId == null) {
            throw new ResearchException("Send a taskId.", "VALIDATION_ERROR", 400);
        }
        plans.checkin(requireUser().id(), taskId, String.valueOf(body.getOrDefault("state", "")));
        return ResponseEntity.ok(StandardApiResponse.success("Checked in.", Map.of("taskId", taskId.toString())));
    }

    @PostMapping("/api/plans/business-outline")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> outline(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(StandardApiResponse.success("Outline ready.", plans.outline(requireUser().id(), uuid(body.get("ideaId")))));
    }

    @GetMapping(value = "/api/plans/business-outline/{ideaId}.md", produces = "text/markdown")
    public ResponseEntity<String> outlineMarkdown(@PathVariable UUID ideaId) {
        return ResponseEntity.ok(plans.outlineMarkdown(requireUser().id(), ideaId));
    }

    @GetMapping("/api/projects")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> projects() {
        return ResponseEntity.ok(StandardApiResponse.success("OK", Map.of("projects", projects.list(requireUser().id()))));
    }

    @PostMapping("/api/projects")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> createProject(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(StandardApiResponse.success("Project saved.", projects.create(requireUser().id(), String.valueOf(body.getOrDefault("name", "")))));
    }

    @PostMapping("/api/projects/{id}/items")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> addItem(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String kind = String.valueOf(body.getOrDefault("kind", "note"));
        Object ref = body.get("ref");
        if (!(ref instanceof Map<?, ?> map)) {
            throw new ResearchException("Send a ref object.", "VALIDATION_ERROR", 400);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) map;
        projects.addItem(requireUser().id(), id, kind, typed);
        return ResponseEntity.ok(StandardApiResponse.success("Saved.", Map.of("projectId", id.toString())));
    }

    @GetMapping(value = "/api/projects/{id}/export.md", produces = "text/markdown")
    public ResponseEntity<String> exportMd(@PathVariable UUID id) {
        return ResponseEntity.ok(projects.markdown(requireUser().id(), id));
    }

    @GetMapping(value = "/api/projects/{id}/export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportPdf(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=gyanwire-project.pdf")
                .body(projects.pdf(requireUser().id(), id));
    }

    @PutMapping("/api/source-packs/{id}")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> togglePack(@PathVariable String id, @RequestBody Map<String, Object> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        packs.toggle(requireUser().id(), id, enabled);
        return ResponseEntity.ok(StandardApiResponse.success("Pack updated.", Map.of("id", id, "enabled", enabled)));
    }

    @PostMapping("/api/saved-queries")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> saveQuery(@RequestBody Map<String, Object> body) {
        String query = String.valueOf(body.getOrDefault("query", "")).trim();
        if (query.length() < 3) {
            throw new ResearchException("Write a query to watch.", "VALIDATION_ERROR", 400);
        }
        store.saveQuery(requireUser().id(), query, String.valueOf(body.getOrDefault("industry", "")));
        if (Boolean.TRUE.equals(body.get("weeklyIdeas"))) {
            store.setDigestOptIn(requireUser().id(), true);
        }
        return ResponseEntity.ok(StandardApiResponse.success("Watch saved.", Map.of("query", query)));
    }

    @GetMapping("/api/me/referral")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> referral() {
        return ResponseEntity.ok(StandardApiResponse.success("OK", referrals.mine(requireUser().id())));
    }

    @PostMapping("/api/referrals/redeem")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> redeem(@RequestBody Map<String, Object> body) {
        String code = String.valueOf(body.getOrDefault("code", "")).trim();
        if (code.length() < 6) {
            throw new ResearchException("Send a referral code.", "VALIDATION_ERROR", 400);
        }
        return ResponseEntity.ok(StandardApiResponse.success("Credit applied.", referrals.redeem(requireUser().id(), code)));
    }

    @PostMapping("/api/mcp")
    public ResponseEntity<Map<String, Object>> mcp(
            @RequestHeader(value = "X-Api-Key", required = false) String key,
            @RequestBody Map<String, Object> body
    ) {
        return ResponseEntity.ok(mcp.handle(key, body));
    }

    private static AuthUserPrincipal requireUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUserPrincipal principal)) {
            throw new ResearchException("Sign in to continue.", "AUTH_UNAUTHORIZED", 401);
        }
        return principal;
    }

    private static UUID uuid(Object value) {
        if (value == null || String.valueOf(value).isBlank() || "null".equals(String.valueOf(value))) {
            return null;
        }
        return UUID.fromString(String.valueOf(value));
    }

    public record ProfileRequest(
            @NotNull @AssertTrue Boolean consent,
            @NotBlank @Pattern(regexp = "student|fresher|working|self_employed|founder") String persona,
            @NotBlank @Pattern(regexp = "first_income|side_income|start_business|switch_job|learn") String goal90d,
            @NotBlank String capitalBand,
            @NotBlank String incomeBand,
            @Min(5) @Max(15) int investPct,
            Integer hoursPerWeek,
            java.util.List<String> industries,
            java.util.List<String> languages,
            java.util.List<String> assets,
            java.util.List<String> constraints,
            String locationTier,
            String state,
            String city,
            String riskAppetite,
            String education,
            java.util.List<Map<String, Object>> skills
    ) {
        Map<String, Object> toMap() {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("consent", consent);
            map.put("persona", persona);
            map.put("goal90d", goal90d);
            map.put("capitalBand", capitalBand);
            map.put("incomeBand", incomeBand);
            map.put("investPct", investPct);
            map.put("hoursPerWeek", hoursPerWeek);
            map.put("industries", industries);
            map.put("languages", languages);
            map.put("assets", assets);
            map.put("constraints", constraints);
            map.put("locationTier", locationTier);
            map.put("state", state);
            map.put("city", city);
            map.put("riskAppetite", riskAppetite);
            map.put("education", education);
            map.put("skills", skills);
            return map;
        }
    }
}
