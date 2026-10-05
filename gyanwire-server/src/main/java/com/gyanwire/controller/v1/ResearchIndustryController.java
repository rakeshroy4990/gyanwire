package com.gyanwire.controller.v1;

import com.gyanwire.controller.dto.StandardApiResponse;
import com.gyanwire.controller.dto.response.ResearchIndustryResponse;
import com.gyanwire.service.ResearchIndustryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/industries", "/api/industries"})
public class ResearchIndustryController {

    private final ResearchIndustryService researchIndustryService;

    public ResearchIndustryController(ResearchIndustryService researchIndustryService) {
        this.researchIndustryService = researchIndustryService;
    }

    @GetMapping
    @PreAuthorize("permitAll()")
    public ResponseEntity<StandardApiResponse<List<ResearchIndustryResponse>>> list() {
        List<ResearchIndustryResponse> data = researchIndustryService.listCatalog();
        return ResponseEntity.ok(StandardApiResponse.success(
                "Research industries and sub-combinations.",
                data
        ));
    }
}
