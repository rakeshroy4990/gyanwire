package com.gyanwire.persistence.postgres.repository;

import com.gyanwire.persistence.postgres.model.BillingEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BillingEventRepository extends JpaRepository<BillingEventEntity, UUID> {
    boolean existsByEventId(String eventId);
}
