package com.spectralogic.migrationtracker.api;

import com.spectralogic.migrationtracker.api.dto.GatherDataRequest;
import com.spectralogic.migrationtracker.model.MigrationData;
import com.spectralogic.migrationtracker.service.MigrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/migration")
public class MigrationController {

    private final MigrationService service;

    public MigrationController(MigrationService service) {
        this.service = service;
    }

    @PostMapping("/gather-data")
    public ResponseEntity<MigrationData> gatherData(@RequestBody GatherDataRequest request) {
        MigrationData data = service.gatherData(
            request.getProjectId(),
            request.getPhaseId(),
            request.getDate()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(data);
    }

    @GetMapping("/data")
    public ResponseEntity<List<MigrationData>> getData(@RequestParam String phaseId) {
        return ResponseEntity.ok(service.getDataByPhase(phaseId));
    }
}
