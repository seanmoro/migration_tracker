package com.spectralogic.migrationtracker.model;

import java.time.LocalDate;
import java.util.UUID;

public class MigrationProject {
    private String id;
    private String name;
    private String customerId;
    private String type;
    private LocalDate createdAt;
    private LocalDate lastUpdated;
    private Boolean active;

    public MigrationProject() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDate.now();
        this.lastUpdated = LocalDate.now();
        this.active = true;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDate createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDate getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDate lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
