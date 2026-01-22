package com.spectralogic.migrationtracker.api.dto;

public class PhaseProgress {
    private String phaseId;
    private String phaseName;
    private Integer progress;
    private Long sourceObjects;
    private Long targetObjects;
    private Long sourceSize;
    private Long targetSize;
    private String eta;
    private Integer confidence;
    private Long averageRate;
    private Long sourceTapeCount;
    private Long targetTapeCount;

    // Getters and Setters
    public String getPhaseId() {
        return phaseId;
    }

    public void setPhaseId(String phaseId) {
        this.phaseId = phaseId;
    }

    public String getPhaseName() {
        return phaseName;
    }

    public void setPhaseName(String phaseName) {
        this.phaseName = phaseName;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public Long getSourceObjects() {
        return sourceObjects;
    }

    public void setSourceObjects(Long sourceObjects) {
        this.sourceObjects = sourceObjects;
    }

    public Long getTargetObjects() {
        return targetObjects;
    }

    public void setTargetObjects(Long targetObjects) {
        this.targetObjects = targetObjects;
    }

    public Long getSourceSize() {
        return sourceSize;
    }

    public void setSourceSize(Long sourceSize) {
        this.sourceSize = sourceSize;
    }

    public Long getTargetSize() {
        return targetSize;
    }

    public void setTargetSize(Long targetSize) {
        this.targetSize = targetSize;
    }

    public String getEta() {
        return eta;
    }

    public void setEta(String eta) {
        this.eta = eta;
    }

    public Integer getConfidence() {
        return confidence;
    }

    public void setConfidence(Integer confidence) {
        this.confidence = confidence;
    }

    public Long getAverageRate() {
        return averageRate;
    }

    public void setAverageRate(Long averageRate) {
        this.averageRate = averageRate;
    }

    public Long getSourceTapeCount() {
        return sourceTapeCount;
    }

    public void setSourceTapeCount(Long sourceTapeCount) {
        this.sourceTapeCount = sourceTapeCount;
    }

    public Long getTargetTapeCount() {
        return targetTapeCount;
    }

    public void setTargetTapeCount(Long targetTapeCount) {
        this.targetTapeCount = targetTapeCount;
    }
}
