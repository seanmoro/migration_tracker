package com.spectralogic.migrationtracker.service;

import com.spectralogic.migrationtracker.model.MigrationPhase;
import com.spectralogic.migrationtracker.repository.PhaseRepository;
import org.springframework.stereotype.Service;

import java.util.List;

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
}
