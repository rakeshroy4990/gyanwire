package com.gyanwire.persistence.postgres.repository;

import com.gyanwire.persistence.postgres.model.PageCacheEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PageCacheRepository extends JpaRepository<PageCacheEntity, String> {
}
