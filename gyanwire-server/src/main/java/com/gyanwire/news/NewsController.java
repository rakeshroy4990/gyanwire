package com.gyanwire.news;

import com.gyanwire.auth.security.AuthUserPrincipal;
import com.gyanwire.controller.dto.StandardApiResponse;
import com.gyanwire.research.ResearchException;
import com.gyanwire.service.ResearchIndustryService;
import com.gyanwire.usage.UsageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class NewsController {

    private final NewsQueryService news;
    private final NewsStore store;
    private final ResearchIndustryService industries;

    public NewsController(NewsQueryService news, NewsStore store, ResearchIndustryService industries) {
        this.news = news;
        this.store = store;
        this.industries = industries;
    }

    @GetMapping("/api/news/default")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> defaults(
            @RequestParam(value = "limit", defaultValue = "5") int limit
    ) {
        Map<String, Object> data = new LinkedHashMap<>(news.defaults(limit));
        data.put("intent", "products");
        data.put("engine", "gyanwire");
        data.put("usedLlm", false);
        return ResponseEntity.ok(StandardApiResponse.success("Product news ready.", data));
    }

    @GetMapping("/api/news/{industry}")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> industry(
            @PathVariable String industry,
            @RequestParam(value = "sub", required = false) String sub,
            @RequestParam(value = "limit", defaultValue = "5") int limit,
            @RequestParam(value = "intent", defaultValue = "products") String intent,
            @RequestParam(value = "window", defaultValue = "14d") String window
    ) {
        String decoded = URLDecoder.decode(industry, StandardCharsets.UTF_8);
        List<String> names = industries.listCatalog().stream().map(item -> item.getName()).toList();
        if (!names.contains(decoded)) {
            throw new ResearchException("Pick a valid research industry.", "UNKNOWN_INDUSTRY", 400);
        }
        List<String> subs = industries.listCatalog().stream()
                .filter(item -> item.getName().equals(decoded))
                .findFirst()
                .map(item -> item.getSubs())
                .orElse(List.of());
        String subTrim = sub == null || sub.isBlank() ? null : sub.trim();
        if (subTrim != null && !subs.contains(subTrim)) {
            throw new ResearchException("Pick a valid sub-combination for this industry.", "UNKNOWN_SUB", 400);
        }
        int days = NewsTexts.windowToken(window, 14);
        Map<String, Object> data = new LinkedHashMap<>(news.industry(decoded, subTrim, limit, intent, days));
        data.put("intent", intent);
        data.put("engine", "gyanwire");
        data.put("usedLlm", false);
        data.put("subs", subs);
        return ResponseEntity.ok(StandardApiResponse.success("Latest " + data.get("label") + ".", data));
    }

    @PostMapping("/api/news/feedback")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> feedback(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request
    ) {
        int vote = body.get("vote") instanceof Number n ? n.intValue() : 0;
        if (vote != 1 && vote != -1) {
            throw new ResearchException("Send a vote of 1 or -1.", "VALIDATION_ERROR", 400);
        }
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        if (reason != null && reason.isBlank()) {
            reason = null;
        }
        if (reason != null && !"not_relevant".equals(reason)) {
            throw new ResearchException("Reason must be not_relevant.", "VALIDATION_ERROR", 400);
        }
        UUID itemId = null;
        String rawId = String.valueOf(body.getOrDefault("itemId", "")).trim();
        if (!rawId.isBlank() && !rawId.startsWith("n-")) {
            try {
                itemId = UUID.fromString(rawId);
            } catch (IllegalArgumentException ex) {
                throw new ResearchException("Send a news item id.", "VALIDATION_ERROR", 400);
            }
        }
        String industry = text(body.get("industry"));
        String signal = text(body.get("signalType"));
        UUID userId = currentUser();
        String anon = userId == null ? UsageService.hashIp(request.getRemoteAddr()) : null;
        store.saveFeedback(itemId, userId, anon, vote, reason, industry, signal);
        return ResponseEntity.ok(StandardApiResponse.success("Thanks.", Map.of("vote", vote)));
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private static UUID currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUserPrincipal principal) {
            return principal.id();
        }
        return null;
    }
}
