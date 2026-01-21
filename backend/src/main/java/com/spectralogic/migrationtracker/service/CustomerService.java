package com.spectralogic.migrationtracker.service;

import com.spectralogic.migrationtracker.model.Customer;
import com.spectralogic.migrationtracker.repository.CustomerRepository;
import com.spectralogic.migrationtracker.repository.PhaseRepository;
import com.spectralogic.migrationtracker.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerService {

    private static final Logger logger = LoggerFactory.getLogger(CustomerService.class);
    private final CustomerRepository repository;
    private final ProjectRepository projectRepository;
    private final PhaseRepository phaseRepository;

    public CustomerService(CustomerRepository repository, ProjectRepository projectRepository, PhaseRepository phaseRepository) {
        this.repository = repository;
        this.projectRepository = projectRepository;
        this.phaseRepository = phaseRepository;
    }

    public List<Customer> findAll(boolean includeInactive) {
        if (includeInactive) {
            return repository.findAllIncludingInactive();
        }
        return repository.findAll();
    }

    public Customer findById(String id) {
        return repository.findById(id)
            .orElseThrow(() -> new RuntimeException("Customer not found: " + id));
    }

    public List<Customer> searchByName(String name, boolean includeInactive) {
        if (includeInactive) {
            return repository.searchByNameIncludingInactive(name);
        }
        return repository.searchByName(name);
    }

    public Customer create(Customer customer) {
        return repository.save(customer);
    }

    @Transactional
    public Customer update(String id, Customer customer) {
        Customer existing = findById(id);
        boolean wasActive = existing.getActive() != null && existing.getActive();
        boolean willBeActive = customer.getActive() != null && customer.getActive();
        
        customer.setId(existing.getId());
        customer.setCreatedAt(existing.getCreatedAt());
        Customer updated = repository.save(customer);
        
        // Cascade deactivation: if customer is being deactivated, deactivate all projects and phases
        if (wasActive && !willBeActive) {
            logger.info("Customer {} is being deactivated. Cascading deactivation to projects and phases.", id);
            List<com.spectralogic.migrationtracker.model.MigrationProject> projects = projectRepository.findByCustomerIdIncludingInactive(id);
            for (com.spectralogic.migrationtracker.model.MigrationProject project : projects) {
                if (project.getActive() != null && project.getActive()) {
                    project.setActive(false);
                    projectRepository.save(project);
                    logger.debug("Deactivated project: {}", project.getId());
                    
                    // Deactivate all phases for this project
                    List<com.spectralogic.migrationtracker.model.MigrationPhase> phases = phaseRepository.findByProjectId(project.getId());
                    for (com.spectralogic.migrationtracker.model.MigrationPhase phase : phases) {
                        if (phase.getActive() == null || phase.getActive()) {
                            phase.setActive(false);
                            phaseRepository.save(phase);
                            logger.debug("Deactivated phase: {}", phase.getId());
                        }
                    }
                }
            }
        }
        
        return updated;
    }

    public void delete(String id) {
        repository.deleteById(id);
    }
}
