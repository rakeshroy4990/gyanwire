package com.gyanwire.research.api;

import com.gyanwire.auth.security.AuthUserPrincipal;
import com.gyanwire.config.PlanLimitException;
import com.gyanwire.controller.dto.StandardApiResponse;
import com.gyanwire.research.ResearchException;
import com.gyanwire.research.service.IndustryNewsService;
import com.gyanwire.research.service.SearchEngineService;
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

    public ResearchController(
            IndustryNewsService industryNewsService,
            SearchEngineService searchEngineService,
            ResearchIndustryService researchIndustryService,
            UsageService usageService
    ) {
        this.industryNewsService = industryNewsService;
        this.searchEngineService = searchEngineService;
        this.researchIndustryService = researchIndustryService;
        this.usageService = usageService;
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

        Map<String, Object> result = searchEngineService.findBestResults(categories, subcategory, thoughts, limit);
        usageService.recordSearchUsage(userId, ipHash, java.math.BigDecimal.ZERO);

        Map<String, Object> usage = new HashMap<>();
        usage.put("searchesToday", ((Number) evaluation.get("used")).longValue() + 1);
        usage.put("searchLimit", evaluation.get("limit"));
        usage.put("searchesRemaining", Math.max(0, ((Number) evaluation.get("remaining")).longValue() - 1));
        usage.put("plan", Map.of("id", plan.get("id"), "name", plan.get("name")));
        result.put("usage", usage);

        return ResponseEntity.ok(StandardApiResponse.success("Here are the best matches we found.", result));
    }

    private static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUserPrincipal p) return p.id();
        return null;
    }
}
