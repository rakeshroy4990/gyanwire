package com.gyanwire.service;

import com.gyanwire.controller.dto.response.ResearchIndustryResponse;
import com.gyanwire.persistence.postgres.model.ResearchIndustryEntity;
import com.gyanwire.persistence.postgres.model.ResearchIndustrySubEntity;
import com.gyanwire.persistence.postgres.repository.ResearchIndustryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ResearchIndustryService {

    private final ResearchIndustryRepository researchIndustryRepository;

    public ResearchIndustryService(ResearchIndustryRepository researchIndustryRepository) {
        this.researchIndustryRepository = researchIndustryRepository;
    }

    @Transactional(readOnly = true)
    public List<ResearchIndustryResponse> listCatalog() {
        return researchIndustryRepository.findAllByOrderBySortOrderAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    private ResearchIndustryResponse toResponse(ResearchIndustryEntity entity) {
        List<String> subs = entity.getSubs().stream()
                .map(ResearchIndustrySubEntity::getName)
                .toList();
        return new ResearchIndustryResponse(
                entity.getExternalId(),
                entity.getName(),
                entity.getLabel(),
                subs
        );
    }
}
