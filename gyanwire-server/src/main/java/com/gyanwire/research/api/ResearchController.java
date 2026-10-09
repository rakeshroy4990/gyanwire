package com.gyanwire.research.api;

import com.gyanwire.auth.security.AuthUserPrincipal;
import com.gyanwire.persistence.FlowStore;
import com.gyanwire.config.PlanLimitException;
import com.gyanwire.controller.dto.StandardApiResponse;
import com.gyanwire.research.ResearchException;
import com.gyanwire.research.brief.BriefService;
import com.gyanwire.research.industry.IndustryModes;
import com.gyanwire.research.service.IndustryNewsService;
import com.gyanwire.research.service.SearchEngineService;
import com.gyanwire.service.ResearchIndustryService;
import com.gyanwire.usage.UsageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
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
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class ResearchController {

    private final IndustryNewsService industryNewsService;
    private final SearchEngineService searchEngineService;
    private final ResearchIndustryService researchIndustryService;
    private final UsageService usageService;
    private final BriefService briefService;
    private final FlowStore flowStore;

    public ResearchController(
            IndustryNewsService industryNewsService,
            SearchEngineService searchEngineService,
            ResearchIndustryService researchIndustryService,
            UsageService usageService,
            BriefService briefService,
            FlowStore flowStore
    ) {
        this.industryNewsService = industryNewsService;
        this.searchEngineService = searchEngineService;
        this.researchIndustryService = researchIndustryService;
        this.usageService = usageService;
        this.briefService = briefService;
        this.flowStore = flowStore;
    }

    @GetMapping("/api/news/default")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> defaultNews() {
        Map<String, Object> data = industryNewsService.getDefaultProductNews(5);
        data.put("intent", "Top searched share and medical product news");
        data.put("engine", "gyanwire");
        data.put("usedLlm", false);
        data.put("isDefaultNews", true);
        return ResponseEntity.ok(StandardApiResponse.success("Product news ready.", data));
    }

    @GetMapping("/api/news/{industry}")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> industryNews(
            @PathVariable String industry,
            @RequestParam(value = "sub", required = false) String sub
    ) {
        String decoded = URLDecoder.decode(industry, StandardCharsets.UTF_8);
        List<String> names = researchIndustryService.listCatalog().stream().map(i -> i.getName()).toList();
        if (!names.contains(decoded)) {
            throw new ResearchException("Pick a valid research industry.", "UNKNOWN_INDUSTRY", 400);
        }
        List<String> subs = researchIndustryService.listCatalog().stream()
                .filter(i -> i.getName().equals(decoded))
                .findFirst()
                .map(i -> i.getSubs())
                .orElse(List.of());
        String subTrim = sub == null || sub.isBlank() ? null : sub.trim();
        if (subTrim != null && !subs.contains(subTrim)) {
            throw new ResearchException("Pick a valid sub-combination for this industry.", "UNKNOWN_SUB", 400);
        }
        Map<String, Object> data = industryNewsService.getIndustryProductNews(decoded, 5, subTrim);
        data.put("intent", "Latest " + data.get("label"));
        data.put("engine", "gyanwire");
        data.put("usedLlm", false);
        data.put("isDefaultNews", false);
        data.put("subs", subs);
        return ResponseEntity.ok(StandardApiResponse.success("Latest " + data.get("label") + ".", data));
    }

    @PostMapping("/api/search")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> search(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request
    ) {
        Object catsObj = body.get("categories");
        if (!(catsObj instanceof List<?> cats) || cats.isEmpty() || cats.size() > 6) {
            throw new ResearchException("Pick a research industry and write a clearer research question.", "VALIDATION_ERROR", 400);
        }
        List<String> categories = cats.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isBlank()).toList();
        String thoughts = String.valueOf(body.getOrDefault("thoughts", "")).trim();
        if (thoughts.length() < 8 || thoughts.length() > 2000) {
            throw new ResearchException("Pick a research industry and write a clearer research question.", "VALIDATION_ERROR", 400);
        }
        String subcategory = body.get("subcategory") == null ? null : String.valueOf(body.get("subcategory")).trim();
        if (subcategory != null && subcategory.isBlank()) subcategory = null;
        int limit = 6;
        if (body.get("limit") instanceof Number n) {
            limit = Math.max(3, Math.min(10, n.intValue()));
        }

        UUID userId = currentUserId();
        String ipHash = UsageService.hashIp(request.getRemoteAddr());
        Map<String, Object> plan = usageService.resolveUserPlan(userId);
        long used = usageService.countTodaySearches(userId, ipHash);
        Map<String, Object> evaluation = UsageService.evaluateSearchLimit(used, ((Number) plan.get("dailySearchLimit")).intValue());
        if (!(Boolean) evaluation.get("allowed")) {
            Map<String, Object> data = new HashMap<>();
            data.put("code", "LIMIT_REACHED");
            data.put("upgradeUrl", "/pricing");
            data.put("searchesToday", evaluation.get("used"));
            data.put("searchLimit", evaluation.get("limit"));
            throw new PlanLimitException(
                    "Daily search limit reached. Upgrade for more research.",
                    "LIMIT_REACHED",
                    data
            );
        }

        Map<String, Object> result = searchEngineService.findBestResults(userId, categories, subcategory, thoughts, limit, null, null);
        usageService.recordSearchUsage(userId, ipHash, java.math.BigDecimal.ZERO);
        try {
            result.put("brief", briefService.maybeBrief(userId, ipHash, thoughts, castResults(result.get("results"))));
        } catch (Exception ignored) {
            result.put("brief", Map.of());
        }
        result.put("disclaimer", IndustryModes.disclaimer(categories.isEmpty() ? "" : categories.get(0)));

        Map<String, Object> usage = new HashMap<>();
        usage.put("searchesToday", ((Number) evaluation.get("used")).longValue() + 1);
        usage.put("searchLimit", evaluation.get("limit"));
        usage.put("searchesRemaining", Math.max(0, ((Number) evaluation.get("remaining")).longValue() - 1));
        usage.put("plan", Map.of("id", plan.get("id"), "name", plan.get("name")));
        result.put("usage", usage);

        return ResponseEntity.ok(StandardApiResponse.success("Here are the best matches we found.", result));
    }

    @PostMapping(value = "/api/search/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        ParsedSearch parsed = parseSearch(body);
        UUID userId = currentUserId();
        String ipHash = UsageService.hashIp(request.getRemoteAddr());
        assertSearchAllowed(userId, ipHash);
        SseEmitter emitter = new SseEmitter(120_000L);
        new Thread(() -> {
            try {
                Map<String, Object> result = searchEngineService.findBestResults(
                        userId,
                        parsed.categories(),
                        parsed.subcategory(),
                        parsed.thoughts(),
                        parsed.limit(),
                        stage -> send(emitter, "status", Map.of("stage", stage)),
                        finding -> send(emitter, "finding", finding)
                );
                usageService.recordSearchUsage(userId, ipHash, java.math.BigDecimal.ZERO);
                result.put("brief", briefService.maybeBrief(userId, ipHash, parsed.thoughts(), castResults(result.get("results"))));
                result.put("disclaimer", IndustryModes.disclaimer(parsed.categories().isEmpty() ? "" : parsed.categories().get(0)));
                result.put("usage", usageSnapshot(userId, ipHash));
                send(emitter, "done", result);
                emitter.complete();
            } catch (Exception e) {
                send(emitter, "error", Map.of("message", e.getMessage() == null ? "Search failed." : e.getMessage()));
                emitter.complete();
            }
        }).start();
        return emitter;
    }

    @PostMapping("/api/findings/feedback")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> feedback(@RequestBody Map<String, Object> body) {
        String url = String.valueOf(body.getOrDefault("url", "")).trim();
        int vote = body.get("vote") instanceof Number n ? n.intValue() : 0;
        if (url.isBlank() || (vote != 1 && vote != -1)) {
            throw new ResearchException("Send a url and a vote of 1 or -1.", "VALIDATION_ERROR", 400);
        }
        try {
            flowStore.saveFeedback(currentUserId(), url, vote);
        } catch (Exception ignored) {
            // The vote still acknowledges so the UI can move on.
        }
        return ResponseEntity.ok(StandardApiResponse.success("Thanks.", Map.of("url", url, "vote", vote)));
    }

    @PostMapping("/api/industry-mode")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> industryMode(@RequestBody Map<String, Object> body) {
        String industry = String.valueOf(body.getOrDefault("industry", ""));
        List<Map<String, Object>> pages = castResults(body.get("results"));
        return ResponseEntity.ok(StandardApiResponse.success("OK", IndustryModes.build(industry, pages)));
    }

    @PostMapping("/api/briefs/claim-check")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> claim(@RequestBody Map<String, Object> body) {
        String sentence = String.valueOf(body.getOrDefault("sentence", "")).trim();
        if (sentence.length() < 8) {
            throw new ResearchException("Select a longer sentence.", "VALIDATION_ERROR", 400);
        }
        return ResponseEntity.ok(StandardApiResponse.success(
                "OK",
                briefService.claimCheck(currentUserId(), sentence, castResults(body.get("passages")))
        ));
    }

    private ParsedSearch parseSearch(Map<String, Object> body) {
        Object catsObj = body.get("categories");
        if (!(catsObj instanceof List<?> cats) || cats.isEmpty() || cats.size() > 6) {
            throw new ResearchException("Pick a research industry and write a clearer research question.", "VALIDATION_ERROR", 400);
        }
        List<String> categories = cats.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isBlank()).toList();
        String thoughts = String.valueOf(body.getOrDefault("thoughts", "")).trim();
        if (thoughts.length() < 8 || thoughts.length() > 2000) {
            throw new ResearchException("Pick a research industry and write a clearer research question.", "VALIDATION_ERROR", 400);
        }
        String subcategory = body.get("subcategory") == null ? null : String.valueOf(body.get("subcategory")).trim();
        if (subcategory != null && subcategory.isBlank()) {
            subcategory = null;
        }
        int limit = 6;
        if (body.get("limit") instanceof Number n) {
            limit = Math.max(3, Math.min(10, n.intValue()));
        }
        return new ParsedSearch(categories, subcategory, thoughts, limit);
    }

    private Map<String, Object> usageSnapshot(UUID userId, String ipHash) {
        Map<String, Object> plan = usageService.resolveUserPlan(userId);
        long used = usageService.countTodaySearches(userId, ipHash);
        int limit = ((Number) plan.get("dailySearchLimit")).intValue();
        Map<String, Object> usage = new HashMap<>();
        usage.put("searchesToday", used);
        usage.put("searchLimit", limit);
        usage.put("searchesRemaining", Math.max(0, limit - used));
        usage.put("plan", Map.of("id", plan.get("id"), "name", plan.get("name")));
        return usage;
    }

    private void assertSearchAllowed(UUID userId, String ipHash) {
        Map<String, Object> plan = usageService.resolveUserPlan(userId);
        long used = usageService.countTodaySearches(userId, ipHash);
        Map<String, Object> evaluation = UsageService.evaluateSearchLimit(used, ((Number) plan.get("dailySearchLimit")).intValue());
        if (!(Boolean) evaluation.get("allowed")) {
            Map<String, Object> data = new HashMap<>();
            data.put("code", "LIMIT_REACHED");
            data.put("upgradeUrl", "/pricing");
            data.put("searchesToday", evaluation.get("used"));
            data.put("searchLimit", evaluation.get("limit"));
            throw new PlanLimitException("Daily search limit reached. Upgrade for more research.", "LIMIT_REACHED", data);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castResults(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                rows.add((Map<String, Object>) map);
            }
        }
        return rows;
    }

    private static void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (Exception ignored) {
            // The client may have disconnected.
        }
    }

    private record ParsedSearch(List<String> categories, String subcategory, String thoughts, int limit) {
    }

    private static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUserPrincipal p) return p.id();
        return null;
    }
}
