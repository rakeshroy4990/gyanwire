package com.gyanwire.news;

import com.gyanwire.auth.security.AuthUserPrincipal;
import com.gyanwire.controller.dto.StandardApiResponse;
import com.gyanwire.usage.UsageService;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
public class NewsAdminController {

    private final NewsFetchCoordinator coordinator;
    private final UsageService usage;
    private final Environment environment;

    public NewsAdminController(NewsFetchCoordinator coordinator, UsageService usage, Environment environment) {
        this.coordinator = coordinator;
        this.usage = usage;
        this.environment = environment;
    }

    @PostMapping("/api/admin/news/ingest")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> ingest(
            @RequestParam(value = "industry", required = false) String industry
    ) {
        if (!allowed()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(StandardApiResponse.error("Admin only.", "FORBIDDEN"));
        }
        String target = industry == null || industry.isBlank() ? "Medical" : industry.trim();
        IndustryFetchGate.Outcome outcome = coordinator.fetch(
                target, null, NewsFetchCoordinator.Mode.MANUAL, Duration.ofMinutes(2));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("industry", target);
        body.put("timedOut", outcome.timedOut());
        body.put("failed", outcome.failed());
        return ResponseEntity.ok(StandardApiResponse.success("News ingest finished.", body));
    }

    private boolean allowed() {
        if (environment.acceptsProfiles(Profiles.of("dev"))) {
            return true;
        }
        UUID userId = currentUser();
        return userId != null && usage.isAdmin(userId);
    }

    private static UUID currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUserPrincipal principal) {
            return principal.id();
        }
        return null;
    }
}
