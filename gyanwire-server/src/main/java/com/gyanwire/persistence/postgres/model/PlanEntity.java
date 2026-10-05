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
}
