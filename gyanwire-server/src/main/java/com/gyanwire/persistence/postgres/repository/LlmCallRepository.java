package com.gyanwire.persistence.postgres.repository;

import com.gyanwire.persistence.postgres.model.LlmCallEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LlmCallRepository extends JpaRepository<LlmCallEntity, UUID> {

    @Query("select coalesce(sum(c.tokensIn), 0) from LlmCallEntity c where c.userId = :userId and c.createdAt >= :start")
    long sumUserTokensIn(@Param("userId") UUID userId, @Param("start") Instant start);

    @Query("select coalesce(sum(c.tokensOut), 0) from LlmCallEntity c where c.userId = :userId and c.createdAt >= :start")
    long sumUserTokensOut(@Param("userId") UUID userId, @Param("start") Instant start);

    @Query("select coalesce(sum(c.tokensIn), 0) from LlmCallEntity c where c.userId is null and c.createdAt >= :start")
    long sumAnonTokensIn(@Param("start") Instant start);

    @Query("select coalesce(sum(c.tokensOut), 0) from LlmCallEntity c where c.userId is null and c.createdAt >= :start")
    long sumAnonTokensOut(@Param("start") Instant start);

    @Query("select coalesce(sum(c.costInr), 0) from LlmCallEntity c where c.createdAt >= :start")
    BigDecimal sumCostInrSince(@Param("start") Instant start);

    List<LlmCallEntity> findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(Instant start);
}
