package com.spectralogic.migrationtracker.api;

import com.spectralogic.migrationtracker.api.dto.CustomerPhases;
import com.spectralogic.migrationtracker.api.dto.DashboardStats;
import com.spectralogic.migrationtracker.api.dto.PhaseProgress;
import com.spectralogic.migrationtracker.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/stats")
    public ResponseEntity<DashboardStats> getStats() {
        return ResponseEntity.ok(service.getStats());
    }

    @GetMapping("/active-phases")
    public ResponseEntity<List<PhaseProgress>> getActivePhases() {
        return ResponseEntity.ok(service.getActivePhases());
    }

    @GetMapping("/recent-activity")
    public ResponseEntity<List<Object>> getRecentActivity() {
        return ResponseEntity.ok(service.getRecentActivity());
    }

    @GetMapping("/phases-needing-attention")
    public ResponseEntity<List<PhaseProgress>> getPhasesNeedingAttention() {
        return ResponseEntity.ok(service.getPhasesNeedingAttention());
    }

    @GetMapping("/active-phases-by-customer")
    public ResponseEntity<List<CustomerPhases>> getActivePhasesByCustomer() {
        return ResponseEntity.ok(service.getActivePhasesByCustomer());
    }
}
