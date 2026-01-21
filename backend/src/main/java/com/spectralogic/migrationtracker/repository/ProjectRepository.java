package com.spectralogic.migrationtracker.repository;

import com.spectralogic.migrationtracker.model.MigrationProject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class ProjectRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<MigrationProject> rowMapper = new RowMapper<MigrationProject>() {
        @Override
        public MigrationProject mapRow(ResultSet rs, int rowNum) throws SQLException {
            MigrationProject project = new MigrationProject();
            project.setId(rs.getString("id"));
            project.setName(rs.getString("name"));
            project.setCustomerId(rs.getString("customer_id"));
            project.setType(rs.getString("type"));
            // SQLite stores dates as strings
            String createdAtStr = rs.getString("created_at");
            project.setCreatedAt(createdAtStr != null ? LocalDate.parse(createdAtStr) : LocalDate.now());
            String lastUpdatedStr = rs.getString("last_updated");
            project.setLastUpdated(lastUpdatedStr != null ? LocalDate.parse(lastUpdatedStr) : LocalDate.now());
            project.setActive(rs.getBoolean("active"));
            return project;
        }
    };

    public List<MigrationProject> findAll() {
        return jdbcTemplate.query(
            "SELECT * FROM migration_project ORDER BY name",
            rowMapper
        );
    }

    public List<MigrationProject> findByCustomerId(String customerId) {
        return jdbcTemplate.query(
            "SELECT * FROM migration_project WHERE customer_id = ? ORDER BY name",
            rowMapper,
            customerId
        );
    }

    public Optional<MigrationProject> findById(String id) {
        List<MigrationProject> results = jdbcTemplate.query(
            "SELECT * FROM migration_project WHERE id = ?",
            rowMapper,
            id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<MigrationProject> searchByName(String name) {
        return jdbcTemplate.query(
            "SELECT * FROM migration_project WHERE name LIKE ? ORDER BY name",
            rowMapper,
            "%" + name + "%"
        );
    }

    public MigrationProject save(MigrationProject project) {
        if (project.getId() == null || findById(project.getId()).isEmpty()) {
            // Insert
            jdbcTemplate.update(
                "INSERT INTO migration_project (id, name, customer_id, type, created_at, last_updated, active) VALUES (?, ?, ?, ?, ?, ?, ?)",
                project.getId(),
                project.getName(),
                project.getCustomerId(),
                project.getType(),
                project.getCreatedAt(),
                project.getLastUpdated(),
                project.getActive() != null ? project.getActive() : true
            );
        } else {
            // Update
            project.setLastUpdated(LocalDate.now());
            jdbcTemplate.update(
                "UPDATE migration_project SET name = ?, customer_id = ?, type = ?, last_updated = ?, active = ? WHERE id = ?",
                project.getName(),
                project.getCustomerId(),
                project.getType(),
                project.getLastUpdated(),
                project.getActive(),
                project.getId()
            );
        }
        return project;
    }

    public void deleteById(String id) {
        jdbcTemplate.update(
            "UPDATE migration_project SET active = 0 WHERE id = ?",
            id
        );
    }
}
