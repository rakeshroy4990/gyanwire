package com.gyanwire.research.brief;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.persistence.FlowStore;
import com.gyanwire.research.engine.LlmService;
import com.gyanwire.usage.UsageService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BriefService {

    private final LlmService llmService;
    private final UsageService usage;
    private final FlowStore store;
    private final ObjectMapper mapper;

    public BriefService(LlmService llmService, UsageService usage, FlowStore store, ObjectMapper mapper) {
        this.llmService = llmService;
        this.usage = usage;
        this.store = store;
        this.mapper = mapper;
    }

    public Map<String, Object> maybeBrief(UUID userId, String ipHash, String thoughts, List<Map<String, Object>> results) {
        Map<String, Object> plan = usage.resolveUserPlan(userId);
        int limit = plan.get("dailyBriefLimit") instanceof Number n ? n.intValue() : 1;
        long used = usage.countToday(userId, ipHash, "brief");
        if (used >= limit) {
            return Map.of("limited", true, "upgradeUrl", "/pricing");
        }
        List<Map<String, Object>> passages = new ArrayList<>();
        StringBuilder user = new StringBuilder();
        user.append("Language: ").append(LanguageDetector.detect(thoughts)).append("\n");
        int n = 0;
        for (Map<String, Object> result : results) {
            if (n == 8) {
                break;
            }
            n++;
            String text = String.valueOf(result.getOrDefault("excerpt", result.getOrDefault("description", "")));
            if (text.length() > 400) {
                text = text.substring(0, 400);
            }
            passages.add(Map.of("citation", n, "findingId", String.valueOf(result.get("id")), "url", String.valueOf(result.get("url")), "text", text));
            user.append('[').append(n).append("] ").append(text).append("\n");
        }
        if (passages.isEmpty()) {
            return Map.of();
        }
        JsonNode node = llmService.completeValidated(
                userId, "cited-brief.v1", "cited-brief", LlmService.prompt("cited-brief.v1.txt"), user.toString(), CitedBriefs::validate);
        if (node == null) {
            return Map.of();
        }
        try {
            store.savePassages(DigestKey.of(thoughts), passages);
        } catch (Exception ignored) {
            // audit is best effort
        }
        usage.recordUsage(userId, ipHash, "brief", java.math.BigDecimal.ZERO);
        Map<String, Object> brief = new LinkedHashMap<>(CitedBriefs.toMap(node));
        brief.put("passages", passages);
        return brief;
    }

    public Map<String, Object> claimCheck(UUID userId, String sentence, List<Map<String, Object>> passages) {
        try {
            String user = mapper.writeValueAsString(Map.of("sentence", sentence, "passages", passages == null ? List.of() : passages));
            JsonNode node = llmService.completeValidated(
                    userId, "claim-check.v1", "claim-check", LlmService.prompt("claim-check.v1.txt"), user, ClaimChecks::validate);
            if (node == null) {
                return Map.of("stance", "unclear", "evidence", List.of());
            }
            return ClaimChecks.toMap(node);
        } catch (Exception e) {
            return Map.of("stance", "unclear", "evidence", List.of());
        }
    }

    private static final class DigestKey {
        static String of(String thoughts) {
            return Integer.toHexString(String.valueOf(thoughts).hashCode());
        }
    }
}
