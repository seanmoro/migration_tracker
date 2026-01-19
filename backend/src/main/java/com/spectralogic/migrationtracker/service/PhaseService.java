package com.spectralogic.migrationtracker.service;

import com.spectralogic.migrationtracker.model.MigrationPhase;
import com.spectralogic.migrationtracker.repository.PhaseRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PhaseService {

    private final PhaseRepository repository;

    public PhaseService(PhaseRepository repository) {
        this.repository = repository;
    }

    public List<MigrationPhase> findByProjectId(String projectId) {
        return repository.findByProjectId(projectId);
    }

    public MigrationPhase findById(String id) {
        return repository.findById(id)
            .orElseThrow(() -> new RuntimeException("Phase not found: " + id));
    }

    public List<MigrationPhase> searchByProjectIdAndName(String projectId, String name) {
        return repository.searchByProjectIdAndName(projectId, name);
    }

    public MigrationPhase create(MigrationPhase phase) {
        return repository.save(phase);
    }

    public MigrationPhase update(String id, MigrationPhase phase) {
        MigrationPhase existing = findById(id);
        phase.setId(existing.getId());
        phase.setCreatedAt(existing.getCreatedAt());
        return repository.save(phase);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    /**
     * Get default phase values based on existing phases in the project
     * Returns the most common source, target, and tape partition from existing phases
     */
    public Map<String, String> getDefaultValues(String projectId) {
        Map<String, String> defaults = new HashMap<>();
        
        List<MigrationPhase> phases = repository.findByProjectId(projectId);
        
        if (phases.isEmpty()) {
            // No existing phases, return empty defaults
            return defaults;
        }
        
        // Find most common source
        Map<String, Integer> sourceCounts = new HashMap<>();
        for (MigrationPhase phase : phases) {
            if (phase.getSource() != null && !phase.getSource().isEmpty()) {
                sourceCounts.put(phase.getSource(), sourceCounts.getOrDefault(phase.getSource(), 0) + 1);
            }
        }
        if (!sourceCounts.isEmpty()) {
            defaults.put("source", sourceCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(""));
        }
        
        // Find most common target
        Map<String, Integer> targetCounts = new HashMap<>();
        for (MigrationPhase phase : phases) {
            if (phase.getTarget() != null && !phase.getTarget().isEmpty()) {
                targetCounts.put(phase.getTarget(), targetCounts.getOrDefault(phase.getTarget(), 0) + 1);
            }
        }
        if (!targetCounts.isEmpty()) {
            defaults.put("target", targetCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(""));
        }
        
        // Find most common source tape partition
        Map<String, Integer> sourceTapePartitionCounts = new HashMap<>();
        for (MigrationPhase phase : phases) {
            if (phase.getSourceTapePartition() != null && !phase.getSourceTapePartition().isEmpty()) {
                sourceTapePartitionCounts.put(phase.getSourceTapePartition(), 
                    sourceTapePartitionCounts.getOrDefault(phase.getSourceTapePartition(), 0) + 1);
            }
        }
        if (!sourceTapePartitionCounts.isEmpty()) {
            defaults.put("sourceTapePartition", sourceTapePartitionCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(""));
        }
        
        // Find most common target tape partition
        Map<String, Integer> targetTapePartitionCounts = new HashMap<>();
        for (MigrationPhase phase : phases) {
            if (phase.getTargetTapePartition() != null && !phase.getTargetTapePartition().isEmpty()) {
                targetTapePartitionCounts.put(phase.getTargetTapePartition(), 
                    targetTapePartitionCounts.getOrDefault(phase.getTargetTapePartition(), 0) + 1);
            }
        }
        if (!targetTapePartitionCounts.isEmpty()) {
            defaults.put("targetTapePartition", targetTapePartitionCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(""));
        }
        
        return defaults;
    }
}
