package com.spectralogic.migrationtracker.api;

import com.spectralogic.migrationtracker.api.dto.Forecast;
import com.spectralogic.migrationtracker.api.dto.PhaseProgress;
import com.spectralogic.migrationtracker.model.MigrationData;
import com.spectralogic.migrationtracker.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/reports")
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
    public ResponseEntity<String> exportPhase(@PathVariable String phaseId, @RequestBody Object options) {
        // TODO: Implement export functionality
        return ResponseEntity.ok("Export functionality coming soon");
    }
}
