package com.gyanwire.persistence.postgres.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usage_events")
public class UsageEventEntity {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "ip_hash")
    private String ipHash;

    @Column(nullable = false)
    private String kind;

    @Column(name = "cost_inr_estimate", nullable = false)
    private BigDecimal costInrEstimate = BigDecimal.ZERO;

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
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public void setIpHash(String ipHash) {
        this.ipHash = ipHash;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public void setCostInrEstimate(BigDecimal costInrEstimate) {
        this.costInrEstimate = costInrEstimate;
    }
}
