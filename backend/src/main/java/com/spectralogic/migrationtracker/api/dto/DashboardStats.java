package com.spectralogic.migrationtracker.api.dto;

public class DashboardStats {
    private Integer activeMigrations;
    private Long totalObjectsMigrated;
    private Integer averageProgress;
    private Integer phasesNeedingAttention;

    // Getters and Setters
    public Integer getActiveMigrations() {
        return activeMigrations;
    }

    public void setActiveMigrations(Integer activeMigrations) {
        this.activeMigrations = activeMigrations;
    }

    public Long getTotalObjectsMigrated() {
        return totalObjectsMigrated;
    }

    public void setTotalObjectsMigrated(Long totalObjectsMigrated) {
        this.totalObjectsMigrated = totalObjectsMigrated;
    }

    public Integer getAverageProgress() {
        return averageProgress;
    }

    public void setAverageProgress(Integer averageProgress) {
        this.averageProgress = averageProgress;
    }

    public Integer getPhasesNeedingAttention() {
        return phasesNeedingAttention;
    }

    public void setPhasesNeedingAttention(Integer phasesNeedingAttention) {
        this.phasesNeedingAttention = phasesNeedingAttention;
    }
}
