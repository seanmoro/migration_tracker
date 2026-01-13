package com.spectralogic.migrationtracker.api.dto;

import java.time.LocalDate;

public class Forecast {
    private LocalDate eta;
    private Integer confidence;
    private Long averageRate;
    private Long remainingObjects;
    private Long remainingSize;

    // Getters and Setters
    public LocalDate getEta() {
        return eta;
    }

    public void setEta(LocalDate eta) {
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

    public Long getRemainingObjects() {
        return remainingObjects;
    }

    public void setRemainingObjects(Long remainingObjects) {
        this.remainingObjects = remainingObjects;
    }

    public Long getRemainingSize() {
        return remainingSize;
    }

    public void setRemainingSize(Long remainingSize) {
        this.remainingSize = remainingSize;
    }
}
