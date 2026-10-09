package com.gyanwire.persistence.postgres.repository;

import com.gyanwire.persistence.postgres.model.UsageEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface UsageEventRepository extends JpaRepository<UsageEventEntity, UUID> {

    @Query("""
            select count(u) from UsageEventEntity u
            where u.kind = 'search'
              and u.userId = :userId
              and u.createdAt >= :start
              and u.createdAt < :end
            """)
    long countUserSearches(
            @Param("userId") UUID userId,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    @Query("""
            select count(u) from UsageEventEntity u
            where u.kind = 'search'
              and u.userId is null
              and u.ipHash = :ipHash
              and u.createdAt >= :start
              and u.createdAt < :end
            """)
    long countAnonSearches(
            @Param("ipHash") String ipHash,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    @Query("""
            select count(u) from UsageEventEntity u
            where u.kind = :kind
              and u.userId = :userId
              and u.createdAt >= :start
              and u.createdAt < :end
            """)
    long countUserKind(
            @Param("userId") UUID userId,
            @Param("kind") String kind,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    @Query("""
            select count(u) from UsageEventEntity u
            where u.kind = :kind
              and u.userId is null
              and u.ipHash = :ipHash
              and u.createdAt >= :start
              and u.createdAt < :end
            """)
    long countAnonKind(
            @Param("ipHash") String ipHash,
            @Param("kind") String kind,
            @Param("start") Instant start,
            @Param("end") Instant end
    );
}
