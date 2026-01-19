package com.spectralogic.migrationtracker.api.dto;

public class CreatePhaseRequest {
    private String name;
    private String projectId;
    private String type;
    private String source;
    private String target;
    private String sourceTapePartition;
    private String targetTapePartition;

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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
}
