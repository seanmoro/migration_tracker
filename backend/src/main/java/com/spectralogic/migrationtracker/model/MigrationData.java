package com.spectralogic.migrationtracker.model;

import java.time.LocalDate;
import java.util.UUID;

public class MigrationData {
    private String id;
    private LocalDate createdAt;
    private LocalDate lastUpdated;
    private LocalDate timestamp;
    private String migrationPhaseId;
    private String userId;
    private Long sourceObjects;
    private Long sourceSize;
    private Long targetObjects;
    private Long targetSize;
    private String type;
    private Integer targetScratchTapes;

    public MigrationData() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDate.now();
        this.lastUpdated = LocalDate.now();
        this.type = "DATA";
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public LocalDate getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDate timestamp) {
        this.timestamp = timestamp;
    }

    public String getMigrationPhaseId() {
        return migrationPhaseId;
    }

    public void setMigrationPhaseId(String migrationPhaseId) {
        this.migrationPhaseId = migrationPhaseId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getSourceObjects() {
        return sourceObjects;
    }

    public void setSourceObjects(Long sourceObjects) {
        this.sourceObjects = sourceObjects;
    }

    public Long getSourceSize() {
        return sourceSize;
    }

    public void setSourceSize(Long sourceSize) {
        this.sourceSize = sourceSize;
    }

    public Long getTargetObjects() {
        return targetObjects;
    }

    public void setTargetObjects(Long targetObjects) {
        this.targetObjects = targetObjects;
    }

    public Long getTargetSize() {
        return targetSize;
    }

    public void setTargetSize(Long targetSize) {
        this.targetSize = targetSize;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getTargetScratchTapes() {
        return targetScratchTapes;
    }

    public void setTargetScratchTapes(Integer targetScratchTapes) {
        this.targetScratchTapes = targetScratchTapes;
    }
}
