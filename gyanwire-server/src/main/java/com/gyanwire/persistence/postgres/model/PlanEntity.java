package com.gyanwire.persistence.postgres.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "plans")
public class PlanEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "daily_search_limit", nullable = false)
    private int dailySearchLimit;

    @Column(name = "can_save", nullable = false)
    private boolean canSave;

    @Column(name = "can_export", nullable = false)
    private boolean canExport;

    @Column(name = "can_alert", nullable = false)
    private boolean canAlert;

    @Column(nullable = false)
    private int seats = 1;

    @Column(name = "daily_brief_limit", nullable = false)
    private int dailyBriefLimit = 1;

    @Column(name = "daily_idea_limit", nullable = false)
    private int dailyIdeaLimit = 3;

    @Column(name = "can_roadmap", nullable = false)
    private boolean canRoadmap;

    @Column(name = "project_limit")
    private Integer projectLimit;

    @Column(name = "max_plan_variants", nullable = false)
    private int maxPlanVariants = 1;

    @Column(name = "allows_high_model", nullable = false)
    private boolean allowsHighModel;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getDailySearchLimit() {
        return dailySearchLimit;
    }

    public boolean isCanSave() {
        return canSave;
    }

    public boolean isCanExport() {
        return canExport;
    }

    public boolean isCanAlert() {
        return canAlert;
    }

    public int getSeats() {
        return seats;
    }

    public int getDailyBriefLimit() {
        return dailyBriefLimit;
    }

    public int getDailyIdeaLimit() {
        return dailyIdeaLimit;
    }

    public boolean isCanRoadmap() {
        return canRoadmap;
    }

    public Integer getProjectLimit() {
        return projectLimit;
    }

    public int getMaxPlanVariants() {
        return maxPlanVariants;
    }

    public boolean isAllowsHighModel() {
        return allowsHighModel;
    }
}
