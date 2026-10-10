package com.gyanwire.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyanwire.persistence.postgres.model.LlmCallEntity;
import com.gyanwire.persistence.postgres.repository.LlmCallRepository;
import com.gyanwire.usage.UsageService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Predicate;

@Service("tieredLlmService")
public class LlmService {

    private final OpenAiClient openAi;
    private final LlmProperties properties;
    private final LlmCallRepository calls;
    private final ObjectProvider<UsageService> usage;
    private final ObjectMapper mapper;
    private volatile BigDecimal spentCache = BigDecimal.ZERO;
    private volatile long spentCachedAt;

    public LlmService(
            OpenAiClient openAi,
            LlmProperties properties,
            LlmCallRepository calls,
            ObjectProvider<UsageService> usage,
            ObjectMapper mapper
    ) {
        this.openAi = openAi;
        this.properties = properties;
        this.calls = calls;
        this.usage = usage;
        this.mapper = mapper;
    }

    public boolean routerEnabled() {
        return properties.isRouterEnabled();
    }

    public boolean shadow() {
        return properties.isShadow();
    }

    public boolean stageEnabled(String stage) {
        return properties.isRouterEnabled() && properties.stageEnabled(stage);
    }

    public LlmResult call(LlmRequest request) {
        return call(request, null);
    }

    public LlmResult callValidated(LlmRequest request, Predicate<JsonNode> valid) {
        LlmResult first = call(request, null);
        if (first.skipped() || (first.ok() && valid.test(first.json()))) {
            return first;
        }
        if (request.tier() == ModelTier.LIGHT && !first.skipped()) {
            LlmRequest again = new LlmRequest(
                    request.stage(),
                    ModelTier.MAIN,
                    request.system(),
                    request.input(),
                    request.userId(),
                    request.planCode(),
                    null,
                    request.maxOutputTokens()
            );
            LlmResult second = call(again, ModelTier.LIGHT.name());
            if (second.ok() && valid.test(second.json())) {
                return second;
            }
        }
        return first.ok() ? new LlmResult(
                null, first.text(), first.inputTokens(), first.cachedInputTokens(), first.outputTokens(),
                first.model(), first.latencyMs(), false, "validation failed", first.tier(), first.escalatedFrom(), false
        ) : first;
    }

    private LlmResult call(LlmRequest request, String escalatedFrom) {
        String plan = planCode(request);
        ModelTier tier = PlanPolicy.tierFor(plan, request.stage(), request.tier());
        tier = LlmSpendGuard.degrade(tier, spentToday(), BigDecimal.valueOf(properties.getDailySpendCapInr()));
        if (tier == null) {
            LlmResult skipped = LlmResult.skipped("spend cap or anonymous");
            log(request, plan, ModelTier.LIGHT, ModelCatalog.LIGHT_MODEL, 0, 0, 0, 0, false, escalatedFrom, BigDecimal.ZERO);
            return skipped;
        }
        if (tier == ModelTier.DEEP) {
            tier = ModelTier.MAIN;
        }
        String effort = request.effortOverride() == null || request.effortOverride().isBlank()
                ? ModelCatalog.effort(tier)
                : request.effortOverride();
        String model = properties.modelFor(tier);
        if (!openAi.configured()) {
            return new LlmResult(null, null, 0, 0, 0, model, 0, false, "llm not configured", tier, escalatedFrom, true);
        }
        long started = System.nanoTime();
        OpenAiClient.Exchange exchange = openAi.complete(model, request.system(), request.input(), effort, request.maxOutputTokens());
        int latency = latency(started);
        int in = exchange.inputTokens() == null ? 0 : exchange.inputTokens();
        int cached = exchange.cachedInputTokens() == null ? 0 : exchange.cachedInputTokens();
        int out = exchange.outputTokens() == null ? 0 : exchange.outputTokens();
        BigDecimal cost = CostCalculator.inr(model, in, cached, out, properties.getFxInrPerUsd());
        log(request, plan, tier, model, in, cached, out, latency, exchange.ok(), escalatedFrom, cost);
        spentCache = spentCache.add(cost == null ? BigDecimal.ZERO : cost);
        return new LlmResult(
                exchange.ok() ? exchange.json() : null,
                exchange.text(),
                in,
                cached,
                out,
                model,
                latency,
                exchange.ok(),
                exchange.error(),
                tier,
                escalatedFrom,
                false
        );
    }

    private String planCode(LlmRequest request) {
        if (request.planCode() != null && !request.planCode().isBlank()) {
            return request.planCode();
        }
        if (request.userId() == null) {
            return "anonymous";
        }
        try {
            UsageService service = usage == null ? null : usage.getIfAvailable();
            if (service == null) {
                return "free";
            }
            Object id = service.resolveUserPlan(request.userId()).get("id");
            return id == null ? "free" : String.valueOf(id);
        } catch (Exception ex) {
            return "free";
        }
    }

    public BigDecimal spentToday() {
        long now = System.currentTimeMillis();
        if (now - spentCachedAt < 30_000) {
            return spentCache;
        }
        try {
            Instant start = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
            BigDecimal sum = calls.sumCostInrSince(start);
            spentCache = sum == null ? BigDecimal.ZERO : sum;
        } catch (Exception ex) {
            spentCache = BigDecimal.ZERO;
        }
        spentCachedAt = now;
        return spentCache;
    }

    private void log(
            LlmRequest request,
            String plan,
            ModelTier tier,
            String model,
            int in,
            int cached,
            int out,
            int latency,
            boolean ok,
            String escalatedFrom,
            BigDecimal cost
    ) {
        if (calls == null) {
            return;
        }
        try {
            LlmCallEntity row = new LlmCallEntity();
            row.setId(UUID.randomUUID());
            row.setUserId(request.userId());
            row.setFeature(request.stage());
            row.setPromptVersion(request.stage() + ".v1");
            row.setModel(model);
            row.setStage(request.stage());
            row.setTier(tier == null ? null : tier.name());
            row.setPlanCode(plan);
            row.setTokensIn(in);
            row.setCachedInputTokens(cached);
            row.setTokensOut(out);
            row.setCostInr(cost);
            row.setLatencyMs(latency);
            row.setOk(ok);
            row.setEscalatedFrom(escalatedFrom);
            row.setCreatedAt(Instant.now());
            calls.save(row);
        } catch (Exception ignored) {
            // A log write must not change the completion result.
        }
    }

    private static int latency(long started) {
        long ms = (System.nanoTime() - started) / 1_000_000L;
        if (ms < 0) {
            return 0;
        }
        if (ms > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) ms;
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}
