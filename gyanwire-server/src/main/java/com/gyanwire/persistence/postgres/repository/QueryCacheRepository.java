package com.gyanwire.persistence.postgres.repository;

import com.gyanwire.persistence.postgres.model.QueryCacheEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueryCacheRepository extends JpaRepository<QueryCacheEntity, String> {
}
