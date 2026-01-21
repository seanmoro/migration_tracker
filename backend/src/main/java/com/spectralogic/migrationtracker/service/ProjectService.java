package com.spectralogic.migrationtracker.service;

import com.spectralogic.migrationtracker.model.MigrationProject;
import com.spectralogic.migrationtracker.repository.PhaseRepository;
import com.spectralogic.migrationtracker.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectService {

    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);
    private final ProjectRepository repository;
    private final PhaseRepository phaseRepository;

    public ProjectService(ProjectRepository repository, PhaseRepository phaseRepository) {
        this.repository = repository;
        this.phaseRepository = phaseRepository;
    }

    public List<MigrationProject> findAll(boolean includeInactive) {
        if (includeInactive) {
            return repository.findAllIncludingInactive();
        }
        return repository.findAll();
    }

    public List<MigrationProject> findByCustomerId(String customerId, boolean includeInactive) {
        if (includeInactive) {
            return repository.findByCustomerIdIncludingInactive(customerId);
        }
        return repository.findByCustomerId(customerId);
    }

    public MigrationProject findById(String id) {
        return repository.findById(id)
            .orElseThrow(() -> new RuntimeException("Project not found: " + id));
    }

    public List<MigrationProject> searchByName(String name, boolean includeInactive) {
        if (includeInactive) {
            return repository.searchByNameIncludingInactive(name);
        }
        return repository.searchByName(name);
    }

    public MigrationProject create(MigrationProject project) {
        return repository.save(project);
    }

    public MigrationProject update(String id, MigrationProject project) {
        MigrationProject existing = findById(id);
        project.setId(existing.getId());
        project.setCreatedAt(existing.getCreatedAt());
        return repository.save(project);
    }

    @Transactional
    public void delete(String id) {
        // Cascade delete: deactivate all phases for this project
        logger.info("Deleting project {}. Cascading deactivation to phases.", id);
        List<com.spectralogic.migrationtracker.model.MigrationPhase> phases = phaseRepository.findByProjectId(id);
        for (com.spectralogic.migrationtracker.model.MigrationPhase phase : phases) {
            if (phase.getActive() == null || phase.getActive()) {
                phase.setActive(false);
                phaseRepository.save(phase);
                logger.debug("Deactivated phase: {}", phase.getId());
            }
        }
        
        // Delete (deactivate) the project
        repository.deleteById(id);
    }
}
