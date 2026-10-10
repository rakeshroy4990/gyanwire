package com.gyanwire.persistence.postgres.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "llm_calls")
public class LlmCallEntity {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String feature;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    @Column(name = "model")
    private String model;

    @Column(name = "stage")
    private String stage;

    @Column(name = "tier")
    private String tier;

    @Column(name = "plan_code")
    private String planCode;

    @Column(name = "ip_hash")
    private String ipHash;

    @Column(name = "cached_input_tokens", nullable = false)
    private Integer cachedInputTokens = 0;

    @Column(name = "cost_inr", precision = 12, scale = 6)
    private java.math.BigDecimal costInr;

    @Column(name = "escalated_from")
    private String escalatedFrom;

    @Column(name = "tokens_in")
    private Integer tokensIn;

    @Column(name = "tokens_out")
    private Integer tokensOut;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(nullable = false)
    private boolean ok;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (cachedInputTokens == null) {
            cachedInputTokens = 0;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getFeature() {
        return feature;
    }

    public void setFeature(String feature) {
        this.feature = feature;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }

    public String getIpHash() {
        return ipHash;
    }

    public void setIpHash(String ipHash) {
        this.ipHash = ipHash;
    }

    public Integer getCachedInputTokens() {
        return cachedInputTokens;
    }

    public void setCachedInputTokens(Integer cachedInputTokens) {
        this.cachedInputTokens = cachedInputTokens;
    }

    public java.math.BigDecimal getCostInr() {
        return costInr;
    }

    public void setCostInr(java.math.BigDecimal costInr) {
        this.costInr = costInr;
    }

    public String getEscalatedFrom() {
        return escalatedFrom;
    }

    public void setEscalatedFrom(String escalatedFrom) {
        this.escalatedFrom = escalatedFrom;
    }

    public Integer getTokensIn() {
        return tokensIn;
    }

    public void setTokensIn(Integer tokensIn) {
        this.tokensIn = tokensIn;
    }

    public Integer getTokensOut() {
        return tokensOut;
    }

    public void setTokensOut(Integer tokensOut) {
        this.tokensOut = tokensOut;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
