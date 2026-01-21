package com.spectralogic.migrationtracker.api.dto;

import java.time.LocalDate;

public class ExportOptions {
    private String format; // "json", "csv", "excel", "pdf", "html"
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Boolean includeCharts;
    private Boolean includeForecast;
    private Boolean includeRawData;

    public ExportOptions() {
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(LocalDate dateFrom) {
        this.dateFrom = dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public void setDateTo(LocalDate dateTo) {
        this.dateTo = dateTo;
    }

    public Boolean getIncludeCharts() {
        return includeCharts;
    }

    public void setIncludeCharts(Boolean includeCharts) {
        this.includeCharts = includeCharts;
    }

    public Boolean getIncludeForecast() {
        return includeForecast;
    }

    public void setIncludeForecast(Boolean includeForecast) {
        this.includeForecast = includeForecast;
    }

    public Boolean getIncludeRawData() {
        return includeRawData;
    }

    public void setIncludeRawData(Boolean includeRawData) {
        this.includeRawData = includeRawData;
    }
}
