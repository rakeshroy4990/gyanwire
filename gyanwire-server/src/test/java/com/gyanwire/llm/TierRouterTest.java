package com.gyanwire.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.persistence.postgres.model.PlanCacheEntity;
import com.gyanwire.persistence.postgres.repository.PlanCacheRepository;
import com.gyanwire.plans.PlanDraftService;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;
import com.gyanwire.persistence.postgres.model.LlmCallEntity;
import com.gyanwire.persistence.postgres.repository.LlmCallRepository;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TierRouterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void costMatchesAHandExample() {
        BigDecimal cost = CostCalculator.inr("gpt-6-luna", 1000, 200, 500, 96.5);
        assertThat(cost).isEqualByComparingTo("0.032038");
        assertThat(CostCalculator.inr("gpt-4o-mini", 10, 0, 10, 96.5)).isNull();
    }

    @Test
    void spendGuardDowngradesThenStops() {
        assertThat(LlmSpendGuard.degrade(ModelTier.MAIN, new BigDecimal("240"), new BigDecimal("300")))
                .isEqualTo(ModelTier.LIGHT);
        assertThat(LlmSpendGuard.degrade(ModelTier.MAIN, new BigDecimal("300"), new BigDecimal("300")))
                .isNull();
        assertThat(LlmSpendGuard.degrade(ModelTier.LIGHT, new BigDecimal("100"), new BigDecimal("300")))
                .isEqualTo(ModelTier.LIGHT);
    }

    @Test
    void planPolicyKeepsFreeOnLunaAndDeepOnSol() {
        assertThat(PlanPolicy.tierFor("anonymous", "rerank", ModelTier.MAIN)).isNull();
        assertThat(PlanPolicy.tierFor("free", "rerank", ModelTier.MAIN)).isEqualTo(ModelTier.LIGHT);
        assertThat(PlanPolicy.tierFor("pro", "hardcase", ModelTier.DEEP)).isEqualTo(ModelTier.MAIN);
        assertThat(PlanPolicy.tierFor("team", "plan_a", ModelTier.LIGHT)).isEqualTo(ModelTier.MAIN);
        assertThat(PlanPolicy.tierFor("pro", "whatif", ModelTier.LIGHT)).isEqualTo(ModelTier.LIGHT);
    }

    @Test
    void escalationRetriesOnceThenFallsBack() {
        AtomicInteger calls = new AtomicInteger();
        StructuredCall.Outcome invalid = StructuredCall.run(ModelTier.LIGHT, tier -> {
            calls.incrementAndGet();
            return mapper.createObjectNode().put("ok", false);
        }, node -> node.path("ok").asBoolean());
        assertThat(invalid.usedFallback()).isTrue();
        assertThat(calls.get()).isEqualTo(2);
        assertThat(invalid.escalatedFrom()).isEqualTo("LIGHT");

        StructuredCall.Outcome recovered = StructuredCall.run(ModelTier.LIGHT, tier -> {
            var node = mapper.createObjectNode();
            node.put("ok", tier == ModelTier.MAIN);
            return node;
        }, node -> node.path("ok").asBoolean());
        assertThat(recovered.usedFallback()).isFalse();
        assertThat(recovered.tier()).isEqualTo(ModelTier.MAIN);
    }

    @Test
    void tieredRequestOmitsTemperatureAndRetries429() throws Exception {
        AtomicInteger posts = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> body = new java.util.concurrent.atomic.AtomicReference<>();
        OpenAiClient client = new OpenAiClient("sk-test", "https://api.openai.com/v1", mapper, (uri, json, key) -> {
            body.set(json);
            int n = posts.incrementAndGet();
            if (n == 1) {
                return new LlmClient.LlmHttpResult(429, "{\"error\":\"slow\"}");
            }
            return new LlmClient.LlmHttpResult(200, """
                    {"usage":{"prompt_tokens":4,"completion_tokens":2,"prompt_tokens_details":{"cached_tokens":1}},
                     "choices":[{"message":{"content":"{\\"ok\\":true}"}}]}
                    """);
        }, () -> { });
        OpenAiClient.Exchange exchange = client.complete("gpt-6-luna", "rules", "notes", "low", null);
        assertThat(posts.get()).isEqualTo(2);
        assertThat(body.get()).doesNotContain("temperature");
        assertThat(body.get()).contains("reasoning_effort");
        assertThat(exchange.ok()).isTrue();
        assertThat(exchange.cachedInputTokens()).isEqualTo(1);
    }

    @Test
    void gatesAndWeekRulesAndWhatIfNumbers() throws Exception {
        assertThat(QuerySharpener.messy("UPI")).isTrue();
        assertThat(QuerySharpener.messy("India UPI merchant subsidy")).isFalse();
        assertThat(RerankGate.shouldRerank(java.util.List.of(java.util.Map.of("score", 90), java.util.Map.of("score", 70)))).isFalse();
        assertThat(WhatIfNumbers.onlyKnownNumbers("Cost changes by 12 rupees", WhatIfNumbers.allowed(12, 3))).isTrue();
        assertThat(WhatIfNumbers.onlyKnownNumbers("Cost changes by 99 rupees", WhatIfNumbers.allowed(12, 3))).isFalse();
        var weeks = mapper.readTree("""
                {"weeks":[
                  {"title":"a","toolId":"notes","costInr":40},
                  {"title":"b","toolId":"notes","costInr":40},
                  {"title":"c","toolId":"sheet","costInr":60},
                  {"title":"d","toolId":"sheet","costInr":60},
                  {"title":"e","toolId":"notes","costInr":40},
                  {"title":"f","toolId":"sheet","costInr":60},
                  {"title":"g","toolId":"notes","costInr":40},
                  {"title":"h","toolId":"sheet","costInr":60},
                  {"title":"i","toolId":"notes","costInr":40},
                  {"title":"j","toolId":"sheet","costInr":60},
                  {"title":"k","toolId":"notes","costInr":40},
                  {"title":"l","toolId":"sheet","costInr":60}
                ]}
                """);
        assertThat(PlanWeekRules.valid(weeks, Set.of("notes", "sheet"), 100)).isTrue();
    }

    @Test
    void secondPlanRequestDoesNotCallTheModel() throws Exception {
        LlmService llm = mock(LlmService.class);
        PlanCacheRepository cache = mock(PlanCacheRepository.class);
        PlanDraftService drafts = new PlanDraftService(llm, cache, mapper);
        when(cache.findById(any())).thenReturn(Optional.empty());
        when(llm.stageEnabled("plan_a")).thenReturn(true);
        var json = mapper.readTree("{\"idea\":\"UPI checklist\",\"offer\":\"A checklist\",\"chapters\":[\"a\",\"b\",\"c\",\"d\"],\"toolCategories\":[\"notes\"]}");
        when(llm.call(any())).thenReturn(new LlmResult(json, "", 1, 0, 1, "gpt-6.1-sol", 1, true, null, ModelTier.MAIN, null, false));
        assertThat(drafts.stageA(null, "UPI subsidy extended", "IT").path("idea").asText()).contains("UPI");
        PlanCacheEntity stored = new PlanCacheEntity();
        stored.setKey(PlanDraftService.key("UPI subsidy extended", "IT"));
        stored.setPayload(mapper.writeValueAsString(json));
        stored.setHits(0);
        when(cache.findById(stored.getKey())).thenReturn(Optional.of(stored));
        drafts.stageA(null, "UPI subsidy extended", "IT");
        verify(llm, times(1)).call(any());
        assertThat(stored.getHits()).isEqualTo(1);
    }

    @Test
    void loggedRowStoresRupeesAndNotThePrompt() {
        LlmCallRepository calls = mock(LlmCallRepository.class);
        when(calls.sumCostInrSince(any())).thenReturn(BigDecimal.ZERO);
        OpenAiClient client = new OpenAiClient("sk-test", "https://api.openai.com/v1", mapper, (uri, json, key) ->
                new LlmClient.LlmHttpResult(200, """
                        {"usage":{"prompt_tokens":1000,"completion_tokens":500,"prompt_tokens_details":{"cached_tokens":200}},
                         "choices":[{"message":{"content":"{\\"idea\\":\\"checklist\\"}"}}]}
                        """), () -> { });
        LlmProperties properties = new LlmProperties();
        properties.setRouterEnabled(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<com.gyanwire.usage.UsageService> usage = mock(ObjectProvider.class);
        LlmService llm = new LlmService(client, properties, calls, usage, mapper);
        LlmResult result = llm.call(new LlmRequest(
                "plan_a", ModelTier.MAIN, "secret system prompt", "private user notes", null, "pro"));
        assertThat(result.ok()).isTrue();
        ArgumentCaptor<LlmCallEntity> saved = ArgumentCaptor.forClass(LlmCallEntity.class);
        verify(calls).save(saved.capture());
        LlmCallEntity row = saved.getValue();
        assertThat(row.getCostInr()).isEqualByComparingTo("0.638830");
        assertThat(row.getStage()).isEqualTo("plan_a");
        assertThat(row.getTier()).isEqualTo("MAIN");
        assertThat(row.getPlanCode()).isEqualTo("pro");
        assertThat(row.getPromptVersion()).isEqualTo("plan_a.v1");
        assertThat(row.toString()).doesNotContain("secret system prompt").doesNotContain("private user notes");
    }
}
