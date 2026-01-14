package com.spectralogic.migrationtracker.api;

import com.spectralogic.migrationtracker.api.dto.Bucket;
import com.spectralogic.migrationtracker.api.dto.GatherDataRequest;
import com.spectralogic.migrationtracker.model.MigrationData;
import com.spectralogic.migrationtracker.service.BucketService;
import com.spectralogic.migrationtracker.service.MigrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/migration")
public class MigrationController {

    private final MigrationService service;
    private final BucketService bucketService;

    public MigrationController(MigrationService service, BucketService bucketService) {
        this.service = service;
        this.bucketService = bucketService;
    }

    @PostMapping("/gather-data")
    public ResponseEntity<MigrationData> gatherData(@RequestBody GatherDataRequest request) {
        MigrationData data = service.gatherData(
            request.getProjectId(),
            request.getPhaseId(),
            request.getDate(),
            request.getSelectedBuckets()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(data);
    }

    @GetMapping("/data")
    public ResponseEntity<List<MigrationData>> getData(@RequestParam String phaseId) {
        return ResponseEntity.ok(service.getDataByPhase(phaseId));
    }

    @GetMapping("/buckets")
    public ResponseEntity<List<Bucket>> getBuckets(
            @RequestParam(required = false) String source) {
        if ("blackpearl".equalsIgnoreCase(source)) {
            return ResponseEntity.ok(bucketService.getBlackPearlBuckets());
        } else if ("rio".equalsIgnoreCase(source)) {
            return ResponseEntity.ok(bucketService.getRioBuckets());
        } else {
            return ResponseEntity.ok(bucketService.getAllBuckets());
        }
    }
}
