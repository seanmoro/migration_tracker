package com.spectralogic.migrationtracker.api;

import com.spectralogic.migrationtracker.api.dto.Bucket;
import com.spectralogic.migrationtracker.api.dto.GatherDataRequest;
import com.spectralogic.migrationtracker.model.BucketData;
import com.spectralogic.migrationtracker.model.MigrationData;
import com.spectralogic.migrationtracker.service.BucketService;
import com.spectralogic.migrationtracker.service.MigrationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/migration")
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

    @GetMapping("/buckets/customer")
    public ResponseEntity<List<Bucket>> getBucketsForCustomer(
            @RequestParam String customerId,
            @RequestParam(required = false, defaultValue = "blackpearl") String databaseType) {
        return ResponseEntity.ok(bucketService.getBucketsForCustomer(customerId, databaseType));
    }

    @GetMapping("/buckets/size")
    public ResponseEntity<Bucket> getBucketSize(
            @RequestParam String customerId,
            @RequestParam String bucketName,
            @RequestParam(required = false, defaultValue = "blackpearl") String databaseType) {
        Bucket bucket = bucketService.getBucketSize(customerId, bucketName, databaseType);
        if (bucket == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(bucket);
    }

    @GetMapping("/bucket-data")
    public ResponseEntity<List<BucketData>> getBucketData(
            @RequestParam String phaseId,
            @RequestParam(required = false) String bucketName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        List<BucketData> data;
        if (bucketName != null && !bucketName.isEmpty()) {
            data = service.getBucketDataByPhaseAndBucket(phaseId, bucketName);
        } else if (dateFrom != null && dateTo != null) {
            data = service.getBucketDataByPhaseAndDateRange(phaseId, dateFrom, dateTo);
        } else {
            data = service.getBucketDataByPhase(phaseId);
        }
        return ResponseEntity.ok(data);
    }

    @DeleteMapping("/data")
    public ResponseEntity<Void> deleteDataPoint(
            @RequestParam String phaseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        service.deleteDataPoint(phaseId, date);
        return ResponseEntity.noContent().build();
    }
}
