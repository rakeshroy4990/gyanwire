package com.gyanwire.service;

import com.gyanwire.controller.dto.response.ResearchIndustryResponse;
import com.gyanwire.persistence.postgres.model.ResearchIndustryEntity;
import com.gyanwire.persistence.postgres.model.ResearchIndustrySubEntity;
import com.gyanwire.persistence.postgres.repository.ResearchIndustryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchIndustryServiceTest {

    @Mock
    private ResearchIndustryRepository researchIndustryRepository;

    @InjectMocks
    private ResearchIndustryService researchIndustryService;

    @Test
    void listCatalogMapsNameLabelAndSubs() throws Exception {
        ResearchIndustryEntity industry = new ResearchIndustryEntity();
        set(industry, "externalId", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        set(industry, "name", "IT");
        set(industry, "label", "IT products");

        ResearchIndustrySubEntity sub = new ResearchIndustrySubEntity();
        set(sub, "name", "AI");
        set(industry, "subs", List.of(sub));

        when(researchIndustryRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(industry));

        List<ResearchIndustryResponse> catalog = researchIndustryService.listCatalog();

        assertThat(catalog).hasSize(1);
        assertThat(catalog.get(0).getName()).isEqualTo("IT");
        assertThat(catalog.get(0).getLabel()).isEqualTo("IT products");
        assertThat(catalog.get(0).getSubs()).containsExactly("AI");
        assertThat(catalog.get(0).getExternalId())
                .isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
