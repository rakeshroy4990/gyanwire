package com.gyanwire.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.actionplan.NewsIdeasService;
import com.gyanwire.auth.security.AuthUserPrincipal;
import com.gyanwire.controller.dto.StandardApiResponse;
import com.gyanwire.research.ResearchException;
import com.gyanwire.service.ResearchIndustryService;
import com.gyanwire.usage.UsageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
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
    private final ObjectMapper mapper;
    private final NewsIdeasService newsIdeas;

    public NewsController(
            NewsQueryService news,
            NewsStore store,
            ResearchIndustryService industries,
            ObjectMapper mapper,
            NewsIdeasService newsIdeas
    ) {
        this.news = news;
        this.store = store;
        this.industries = industries;
        this.mapper = mapper;
        this.newsIdeas = newsIdeas;
    }

    @GetMapping(value = "/api/news/default", produces = {"application/json", "application/x-ndjson"})
    public void defaults(
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "country", required = false) String country,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        String code = countryCode(country);
        NewsViewer who = viewer(request);
        String accept = request.getHeader("Accept");
        boolean wantJson = accept != null && accept.contains("application/json") && !accept.contains("application/x-ndjson");
        if (wantJson || page > 1) {
            Map<String, Object> data = new LinkedHashMap<>(news.defaults(limit, code, who, page));
            data.put("intent", "products");
            data.put("engine", "gyanwire");
            data.put("usedLlm", false);
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            mapper.writeValue(response.getOutputStream(), StandardApiResponse.success(
                    "Latest " + data.get("label") + ".", data));
            return;
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/x-ndjson");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setBufferSize(64);
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Content-Type-Options", "nosniff");
        NewsNdjson.writeDefault(response.getOutputStream(), mapper, () -> news.defaults(limit, code, who, page), () -> {
            try {
                response.flushBuffer();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });
    }

    @GetMapping("/api/news/{industry}")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> industry(
            @PathVariable String industry,
            @RequestParam(value = "sub", required = false) String sub,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "intent", defaultValue = "products") String intent,
            @RequestParam(value = "window", required = false) String window,
            @RequestParam(value = "country", required = false) String country,
            HttpServletRequest request
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
        if (page < 1) {
            throw new ResearchException("Page must be 1 or greater.", "VALIDATION_ERROR", 400);
        }
        int days = NewsTexts.windowToken(window, NewsQueryService.BROWSE_LOOKBACK_DAYS);
        Map<String, Object> data = new LinkedHashMap<>(
                news.industry(decoded, subTrim, limit, intent, days, countryCode(country), viewer(request), page));
        data.put("intent", intent);
        data.put("engine", "gyanwire");
        data.put("usedLlm", false);
        data.put("subs", subs);
        return ResponseEntity.ok(StandardApiResponse.success("Latest " + data.get("label") + ".", data));
    }

    @PostMapping("/api/news/{id}/ideas")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> ideasFromNews(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        UUID newsId = parseItemId(id);
        if (newsId == null) {
            throw new ResearchException("Send a news item id.", "VALIDATION_ERROR", 400);
        }
        UUID userId = currentUser();
        if (userId == null) {
            throw new ResearchException("Sign in to get ideas.", "AUTH_REQUIRED", 401);
        }
        String ipHash = UsageService.hashIp(request.getRemoteAddr());
        Map<String, Object> data = newsIdeas.ideasFromNewsId(userId, ipHash, newsId);
        return ResponseEntity.ok(StandardApiResponse.success("Ideas ready.", data));
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
        UUID itemId = parseItemId(body.get("itemId"));
        String industry = text(body.get("industry"));
        String signal = text(body.get("signalType"));
        UUID userId = currentUser();
        String anon = userId == null ? UsageService.hashIp(request.getRemoteAddr()) : null;
        store.saveFeedback(itemId, userId, anon, vote, reason, industry, signal);
        return ResponseEntity.ok(StandardApiResponse.success("Thanks.", Map.of("vote", vote)));
    }

    @PostMapping("/api/news/dismiss")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> dismiss(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request
    ) {
        UUID itemId = parseItemId(body.get("itemId"));
        String url = text(body.get("url"));
        if (itemId == null && url == null) {
            throw new ResearchException("Send a news item id or url.", "VALIDATION_ERROR", 400);
        }
        String industry = text(body.get("industry"));
        NewsViewer who = viewer(request);
        store.saveDismissal(itemId, url, who.userId(), who.anonHash(), industry);
        news.invalidateViewer(who);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dismissed", true);
        if (itemId != null) {
            data.put("itemId", itemId.toString());
        }
        if (url != null) {
            data.put("url", url);
        }
        return ResponseEntity.ok(StandardApiResponse.success("Removed.", data));
    }

    @PostMapping("/api/news/hide-source")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> hideSource(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request
    ) {
        UUID userId = currentUser();
        if (userId == null) {
            throw new ResearchException("Sign in to hide a source.", "AUTH_REQUIRED", 401);
        }
        UUID sourceId = parseUuid(body.get("sourceId"));
        UUID itemId = parseItemId(body.get("itemId"));
        if (sourceId == null && itemId != null) {
            sourceId = store.findSourceIdForItem(itemId);
        }
        if (sourceId == null) {
            throw new ResearchException("Send a sourceId or itemId.", "VALIDATION_ERROR", 400);
        }
        store.hideSource(userId, sourceId);
        news.invalidateViewer(viewer(request));
        return ResponseEntity.ok(StandardApiResponse.success("Source hidden.", Map.of(
                "hidden", true,
                "sourceId", sourceId.toString())));
    }

    private static UUID parseUuid(Object raw) {
        String text = text(raw);
        if (text == null) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            throw new ResearchException("Send a valid id.", "VALIDATION_ERROR", 400);
        }
    }

    private NewsViewer viewer(HttpServletRequest request) {
        UUID userId = currentUser();
        String anon = userId == null ? UsageService.hashIp(request.getRemoteAddr()) : null;
        return NewsViewer.of(userId, anon);
    }

    private static UUID parseItemId(Object raw) {
        String rawId = String.valueOf(raw == null ? "" : raw).trim();
        if (rawId.isBlank() || rawId.startsWith("n-")) {
            return null;
        }
        try {
            return UUID.fromString(rawId);
        } catch (IllegalArgumentException ex) {
            throw new ResearchException("Send a news item id.", "VALIDATION_ERROR", 400);
        }
    }

    private static String countryCode(String country) {
        try {
            return CountrySignals.normalize(country);
        } catch (IllegalArgumentException ex) {
            throw new ResearchException("Pick a country from the list.", "UNKNOWN_COUNTRY", 400);
        }
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
