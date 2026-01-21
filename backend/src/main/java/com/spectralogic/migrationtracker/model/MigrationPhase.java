package com.spectralogic.migrationtracker.model;

import java.time.LocalDate;
import java.util.UUID;

public class MigrationPhase {
    private String id;
    private String name;
    private String type;
    private String migrationId;
    private String source;
    private String target;
    private LocalDate createdAt;
    private LocalDate lastUpdated;
    private String sourceTapePartition;
    private String targetTapePartition;
    private Boolean active;

    public MigrationPhase() {
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMigrationId() {
        return migrationId;
    }

    public void setMigrationId(String migrationId) {
        this.migrationId = migrationId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
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

    public String getSourceTapePartition() {
        return sourceTapePartition;
    }

    public void setSourceTapePartition(String sourceTapePartition) {
        this.sourceTapePartition = sourceTapePartition;
    }

    public String getTargetTapePartition() {
        return targetTapePartition;
    }

    public void setTargetTapePartition(String targetTapePartition) {
        this.targetTapePartition = targetTapePartition;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
