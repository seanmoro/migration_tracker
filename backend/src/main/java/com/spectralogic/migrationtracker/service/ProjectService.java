package com.spectralogic.migrationtracker.service;

import com.spectralogic.migrationtracker.model.MigrationProject;
import com.spectralogic.migrationtracker.repository.ProjectRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository repository;

    public ProjectService(ProjectRepository repository) {
        this.repository = repository;
    }

    public List<MigrationProject> findAll() {
        return repository.findAll();
    }

    public List<MigrationProject> findByCustomerId(String customerId) {
        return repository.findByCustomerId(customerId);
    }

    public MigrationProject findById(String id) {
        return repository.findById(id)
            .orElseThrow(() -> new RuntimeException("Project not found: " + id));
    }

    public List<MigrationProject> searchByName(String name) {
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

    public void delete(String id) {
        repository.deleteById(id);
    }
}
