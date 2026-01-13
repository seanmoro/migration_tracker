package com.spectralogic.migrationtracker.api;

import com.spectralogic.migrationtracker.model.MigrationProject;
import com.spectralogic.migrationtracker.service.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<MigrationProject>> getAllProjects(@RequestParam(required = false) String customerId) {
        if (customerId != null) {
            return ResponseEntity.ok(service.findByCustomerId(customerId));
        }
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MigrationProject> getProject(@PathVariable String id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/search")
    public ResponseEntity<List<MigrationProject>> searchProjects(@RequestParam String name) {
        return ResponseEntity.ok(service.searchByName(name));
    }

    @PostMapping
    public ResponseEntity<MigrationProject> createProject(@RequestBody MigrationProject project) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(project));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MigrationProject> updateProject(@PathVariable String id, @RequestBody MigrationProject project) {
        return ResponseEntity.ok(service.update(id, project));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
