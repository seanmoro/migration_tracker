package com.spectralogic.migrationtracker.service;

import com.spectralogic.migrationtracker.model.MigrationData;
import com.spectralogic.migrationtracker.repository.MigrationDataRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class MigrationService {

    private final MigrationDataRepository repository;

    public MigrationService(MigrationDataRepository repository) {
        this.repository = repository;
    }

    public MigrationData gatherData(String projectId, String phaseId, LocalDate date, List<String> selectedBuckets) {
        // Validate that data doesn't already exist for this date
        List<MigrationData> existing = repository.findByPhaseId(phaseId);
        for (MigrationData data : existing) {
            if (data.getTimestamp().equals(date)) {
                throw new RuntimeException("Data already exists for date: " + date);
            }
        }

        // Create new migration data point
        // Note: In a real implementation, this would query the PostgreSQL databases
        // For now, we'll create a placeholder that needs to be populated
        MigrationData data = new MigrationData();
        data.setMigrationPhaseId(phaseId);
        data.setTimestamp(date);
        data.setType("DATA");
        
        // TODO: Query PostgreSQL databases to get actual migration stats
        // This would require connection to BlackPearl/Rio databases
        // If selectedBuckets is provided, filter by those buckets
        // For now, set default values
        data.setSourceObjects(0L);
        data.setTargetObjects(0L);
        data.setSourceSize(0L);
        data.setTargetSize(0L);
        
        // Note: When implementing actual database queries, use selectedBuckets to filter:
        // if (selectedBuckets != null && !selectedBuckets.isEmpty()) {
        //     // Query only selected buckets
        // } else {
        //     // Query all buckets
        // }

        return repository.save(data);
    }

    public List<MigrationData> getDataByPhase(String phaseId) {
        return repository.findByPhaseId(phaseId);
    }
}
