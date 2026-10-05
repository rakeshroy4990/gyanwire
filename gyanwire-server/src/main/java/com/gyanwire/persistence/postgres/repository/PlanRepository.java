package com.gyanwire.persistence.postgres.repository;

import com.gyanwire.persistence.postgres.model.PlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<PlanEntity, String> {
}
