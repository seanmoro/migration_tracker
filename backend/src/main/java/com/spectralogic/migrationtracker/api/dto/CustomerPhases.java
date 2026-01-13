package com.spectralogic.migrationtracker.api.dto;

import java.util.List;

public class CustomerPhases {
    private String customerId;
    private String customerName;
    private List<ProjectPhases> projects;

    public CustomerPhases() {
    }

    public CustomerPhases(String customerId, String customerName, List<ProjectPhases> projects) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.projects = projects;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public List<ProjectPhases> getProjects() {
        return projects;
    }

    public void setProjects(List<ProjectPhases> projects) {
        this.projects = projects;
    }
}
