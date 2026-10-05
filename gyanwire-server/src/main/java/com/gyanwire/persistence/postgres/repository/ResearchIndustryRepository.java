package com.gyanwire.persistence.postgres.repository;

import com.gyanwire.persistence.postgres.model.ResearchIndustryEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResearchIndustryRepository extends JpaRepository<ResearchIndustryEntity, Long> {

    @EntityGraph(attributePaths = "subs")
    List<ResearchIndustryEntity> findAllByOrderBySortOrderAsc();
}
