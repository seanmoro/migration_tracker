package com.spectralogic.migrationtracker.api;

import com.spectralogic.migrationtracker.api.dto.ExportOptions;
import com.spectralogic.migrationtracker.api.dto.Forecast;
import com.spectralogic.migrationtracker.api.dto.PhaseProgress;
import com.spectralogic.migrationtracker.model.MigrationData;
import com.spectralogic.migrationtracker.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/phases/{phaseId}/progress")
    public ResponseEntity<PhaseProgress> getPhaseProgress(@PathVariable String phaseId) {
        return ResponseEntity.ok(service.getPhaseProgress(phaseId));
    }

    @GetMapping("/phases/{phaseId}/data")
    public ResponseEntity<List<MigrationData>> getPhaseData(
            @PathVariable String phaseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(service.getPhaseData(phaseId, dateFrom, to));
    }

    @GetMapping("/phases/{phaseId}/forecast")
    public ResponseEntity<Forecast> getForecast(@PathVariable String phaseId) {
        return ResponseEntity.ok(service.getForecast(phaseId));
    }

    @PostMapping("/phases/{phaseId}/export")
    public ResponseEntity<byte[]> exportPhase(@PathVariable String phaseId, @RequestBody ExportOptions options) {
        try {
            byte[] exportData = service.exportPhase(phaseId, options);
            String format = options.getFormat() != null ? options.getFormat().toLowerCase() : "json";
            
            HttpHeaders headers = new HttpHeaders();
            String contentType;
            String filename;
            
            switch (format) {
                case "csv":
                    contentType = "text/csv";
                    filename = "phase-export-" + phaseId + ".csv";
                    break;
                case "html":
                    contentType = "text/html";
                    filename = "phase-report-" + phaseId + ".html";
                    break;
                case "pdf":
                    // For now, PDF returns HTML (can be printed to PDF from browser)
                    // TODO: Implement proper PDF generation once PDFBox 3.0 font API is resolved
                    contentType = "text/html";
                    filename = "phase-report-" + phaseId + ".html";
                    break;
                case "json":
                default:
                    contentType = "application/json";
                    filename = "phase-export-" + phaseId + ".json";
                    break;
            }
            
            headers.setContentType(MediaType.parseMediaType(contentType));
            headers.setContentDispositionFormData("attachment", filename);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(exportData);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
