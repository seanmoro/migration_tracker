package com.spectralogic.migrationtracker.api.dto;

import java.time.LocalDate;
import java.util.List;

public class GatherDataRequest {
    private String projectId;
    private String phaseId;
    private LocalDate date;
    private List<String> selectedBuckets;

    // Getters and Setters
    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getPhaseId() {
        return phaseId;
    }

    public void setPhaseId(String phaseId) {
        this.phaseId = phaseId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public List<String> getSelectedBuckets() {
        return selectedBuckets;
    }

    public void setSelectedBuckets(List<String> selectedBuckets) {
        this.selectedBuckets = selectedBuckets;
    }
}
