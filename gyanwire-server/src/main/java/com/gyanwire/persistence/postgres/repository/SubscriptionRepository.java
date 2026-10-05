package com.gyanwire.persistence.postgres.repository;

import com.gyanwire.persistence.postgres.model.SubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {

    @Query("""
            select s from SubscriptionEntity s
            where s.userId = :userId
              and s.status in ('active', 'trialing', 'past_due')
              and (s.currentPeriodEnd is null or s.currentPeriodEnd > CURRENT_TIMESTAMP)
            order by s.updatedAt desc
            """)
    List<SubscriptionEntity> findActiveForUser(@Param("userId") UUID userId);

    Optional<SubscriptionEntity> findFirstByProviderAndProviderSubscriptionId(
            String provider,
            String providerSubscriptionId
    );

    Optional<SubscriptionEntity> findFirstByUserIdOrderByUpdatedAtDesc(UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update SubscriptionEntity s
            set s.status = 'replaced', s.updatedAt = CURRENT_TIMESTAMP
            where s.userId = :userId
              and s.status in ('active', 'trialing', 'past_due', 'created', 'authenticated')
              and (s.providerSubscriptionId is null or s.providerSubscriptionId <> :keepId)
            """)
    int markReplacedExcept(@Param("userId") UUID userId, @Param("keepId") String keepId);
}
