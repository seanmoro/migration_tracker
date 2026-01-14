package com.spectralogic.migrationtracker.api.dto;

public class Bucket {
    private String name;
    private String source; // "blackpearl" or "rio"
    private Long objectCount;
    private Long sizeBytes;

    public Bucket() {
    }

    public Bucket(String name, String source, Long objectCount, Long sizeBytes) {
        this.name = name;
        this.source = source;
        this.objectCount = objectCount;
        this.sizeBytes = sizeBytes;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
}
