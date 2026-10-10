package com.gyanwire.persistence.postgres.repository;

import com.gyanwire.persistence.postgres.model.PlanCacheEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanCacheRepository extends JpaRepository<PlanCacheEntity, String> {
}
