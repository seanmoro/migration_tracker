package com.spectralogic.migrationtracker.api.dto;

import java.util.List;

public class ProjectPhases {
    private String projectId;
    private String projectName;
    private List<PhaseProgress> phases;

    public ProjectPhases() {
    }

    public ProjectPhases(String projectId, String projectName, List<PhaseProgress> phases) {
        this.projectId = projectId;
        this.projectName = projectName;
        this.phases = phases;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public List<PhaseProgress> getPhases() {
        return phases;
    }

    public void setPhases(List<PhaseProgress> phases) {
        this.phases = phases;
    }
}
