package com.gyanwire.plans;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.llm.LlmRequest;
import com.gyanwire.llm.LlmResult;
import com.gyanwire.llm.LlmService;
import com.gyanwire.llm.ModelTier;
import com.gyanwire.llm.PlanWeekRules;
import com.gyanwire.persistence.postgres.model.PlanCacheEntity;
import com.gyanwire.persistence.postgres.repository.PlanCacheRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
public class PlanDraftService {

    public static final String PROMPT_VERSION = "plan-a.v1";

    private final LlmService llm;
    private final PlanCacheRepository cache;
    private final ObjectMapper mapper;

    public PlanDraftService(LlmService llm, PlanCacheRepository cache, ObjectMapper mapper) {
        this.llm = llm;
        this.cache = cache;
        this.mapper = mapper;
    }

    public JsonNode stageA(UUID userId, String newsTitle, String industry) {
        String key = key(newsTitle, industry);
        PlanCacheEntity existing = cache.findById(key).orElse(null);
        if (existing != null) {
            existing.setHits(existing.getHits() + 1);
            cache.save(existing);
            try {
                return mapper.readTree(existing.getPayload());
            } catch (Exception ex) {
                return null;
            }
        }
        if (!llm.stageEnabled("plan_a")) {
            return null;
        }
        String system = "Return JSON only with keys idea, offer, chapters, toolCategories. Chapters is an array of four strings: offer, practice, ship, decide. No user name or email.";
        String input = "News: " + safe(newsTitle) + "\nIndustry: " + safe(industry);
        LlmResult result = llm.call(new LlmRequest("plan_a", ModelTier.MAIN, system, input, userId, null));
        if (!result.ok() || result.json() == null || result.json().path("idea").asText("").isBlank()) {
            return null;
        }
        try {
            PlanCacheEntity row = new PlanCacheEntity();
            row.setKey(key);
            row.setStage("plan_a");
            row.setPayload(mapper.writeValueAsString(result.json()));
            row.setPromptVersion(PROMPT_VERSION);
            row.setCreatedAt(Instant.now());
            row.setHits(0);
            cache.save(row);
        } catch (Exception ignored) {
            // The caller can still use the fresh JSON.
        }
        return result.json();
    }

    public JsonNode stageB(UUID userId, String stageAJson, int budgetTotal, Set<String> catalogIds) {
        if (!llm.stageEnabled("plan_b")) {
            return null;
        }
        String system = "Return JSON only: {\"weeks\":[{\"title\":\"\",\"toolId\":\"\",\"costInr\":0,\"steps\":[\"\"]}]}. Twelve weeks. Titles must differ. Costs are estimates copied from the input.";
        String input = "Shared plan:\n" + stageAJson + "\nBudget total: " + budgetTotal + "\nTool ids: " + catalogIds;
        LlmRequest request = new LlmRequest("plan_b", ModelTier.LIGHT, system, input, userId, null, "high", 1200);
        LlmResult result = llm.callValidated(request, node -> PlanWeekRules.valid(node, catalogIds, budgetTotal));
        return result.ok() ? result.json() : null;
    }

    public static String key(String newsTitle, String industry) {
        String raw = (newsTitle == null ? "" : newsTitle.trim().toLowerCase()) + "|" + (industry == null ? "" : industry.trim()) + "|" + PROMPT_VERSION;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return raw;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\n', ' ').trim();
    }
}
