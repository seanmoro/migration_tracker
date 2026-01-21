package com.spectralogic.migrationtracker.model;

import java.time.LocalDate;
import java.util.UUID;

public class BucketData {
    private String id;
    private LocalDate createdAt;
    private LocalDate lastUpdated;
    private LocalDate timestamp;
    private String migrationPhaseId;
    private String bucketName;
    private String source; // "blackpearl" or "rio"
    private Long objectCount;
    private Long sizeBytes;
    private String userId;

    public BucketData() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDate.now();
        this.lastUpdated = LocalDate.now();
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

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Long getObjectCount() {
        return objectCount;
    }

    public void setObjectCount(Long objectCount) {
        this.objectCount = objectCount;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
