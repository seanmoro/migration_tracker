package com.spectralogic.migrationtracker.api;

import com.spectralogic.migrationtracker.model.MigrationPhase;
import com.spectralogic.migrationtracker.service.PhaseService;
import com.spectralogic.migrationtracker.service.StorageDomainService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/phases")
public class PhaseController {

    private final PhaseService service;
    private final StorageDomainService storageDomainService;

    public PhaseController(PhaseService service, StorageDomainService storageDomainService) {
        this.service = service;
        this.storageDomainService = storageDomainService;
    }

    @GetMapping
    public ResponseEntity<List<MigrationPhase>> getPhases(
            @RequestParam String projectId,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(service.findByProjectId(projectId, includeInactive));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MigrationPhase> getPhase(@PathVariable String id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/search")
    public ResponseEntity<List<MigrationPhase>> searchPhases(
            @RequestParam String projectId,
            @RequestParam String name,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(service.searchByProjectIdAndName(projectId, name, includeInactive));
    }

    @PostMapping
    public ResponseEntity<MigrationPhase> createPhase(@RequestBody MigrationPhase phase) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(phase));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MigrationPhase> updatePhase(@PathVariable String id, @RequestBody MigrationPhase phase) {
        return ResponseEntity.ok(service.update(id, phase));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePhase(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/defaults")
    public ResponseEntity<Map<String, String>> getDefaultValues(@RequestParam String projectId) {
        return ResponseEntity.ok(service.getDefaultValues(projectId));
    }

    @GetMapping("/storage-domains")
    public ResponseEntity<StorageDomainService.StorageDomains> getStorageDomains(
            @RequestParam String customerId,
            @RequestParam String databaseType) {
        return ResponseEntity.ok(storageDomainService.getStorageDomains(customerId, databaseType));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<MigrationPhase> toggleStatus(@PathVariable String id, @RequestParam boolean active) {
        MigrationPhase phase = service.findById(id);
        phase.setActive(active);
        return ResponseEntity.ok(service.update(id, phase));
    }
}
