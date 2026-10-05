package com.gyanwire.usage;

import com.gyanwire.auth.security.AuthUserPrincipal;
import com.gyanwire.controller.dto.StandardApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UsageService usageService;

    public MeController(UsageService usageService) {
        this.usageService = usageService;
    }

    @GetMapping("/usage")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> usage(HttpServletRequest request) {
        UUID userId = currentUserId();
        String ipHash = UsageService.hashIp(request.getRemoteAddr());
        return ResponseEntity.ok(StandardApiResponse.success("OK", usageService.getUsageSnapshot(userId, ipHash)));
    }

    private static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUserPrincipal p) {
            return p.id();
        }
        return null;
    }
}
