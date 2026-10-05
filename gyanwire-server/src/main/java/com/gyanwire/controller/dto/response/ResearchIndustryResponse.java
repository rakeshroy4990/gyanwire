package com.gyanwire.controller.dto.response;

import java.util.List;
import java.util.UUID;

public class ResearchIndustryResponse {
    private UUID externalId;
    private String name;
    private String label;
    private List<String> subs;

    public ResearchIndustryResponse() {
    }

    public ResearchIndustryResponse(UUID externalId, String name, String label, List<String> subs) {
        this.externalId = externalId;
        this.name = name;
        this.label = label;
        this.subs = subs;
    }

    public UUID getExternalId() {
        return externalId;
    }

    public void setExternalId(UUID externalId) {
        this.externalId = externalId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<String> getSubs() {
        return subs;
    }

    public void setSubs(List<String> subs) {
        this.subs = subs;
    }
}
