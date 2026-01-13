package com.spectralogic.migrationtracker.repository;

import com.spectralogic.migrationtracker.model.MigrationPhase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class PhaseRepository {

    private final JdbcTemplate jdbcTemplate;

    public PhaseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<MigrationPhase> rowMapper = new RowMapper<MigrationPhase>() {
        @Override
        public MigrationPhase mapRow(ResultSet rs, int rowNum) throws SQLException {
            MigrationPhase phase = new MigrationPhase();
            phase.setId(rs.getString("id"));
            phase.setName(rs.getString("name"));
            phase.setType(rs.getString("type"));
            phase.setMigrationId(rs.getString("migration_id"));
            phase.setSource(rs.getString("source"));
            phase.setTarget(rs.getString("target"));
            // SQLite stores dates as strings
            String createdAtStr = rs.getString("created_at");
            phase.setCreatedAt(createdAtStr != null ? LocalDate.parse(createdAtStr) : LocalDate.now());
            String lastUpdatedStr = rs.getString("last_updated");
            phase.setLastUpdated(lastUpdatedStr != null ? LocalDate.parse(lastUpdatedStr) : LocalDate.now());
            phase.setTargetTapePartition(rs.getString("target_tape_partition"));
            return phase;
        }
    };

    public List<MigrationPhase> findByProjectId(String projectId) {
        return jdbcTemplate.query(
            "SELECT * FROM migration_phase WHERE migration_id = ? ORDER BY name",
            rowMapper,
            projectId
        );
    }

    public Optional<MigrationPhase> findById(String id) {
        List<MigrationPhase> results = jdbcTemplate.query(
            "SELECT * FROM migration_phase WHERE id = ?",
            rowMapper,
            id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<MigrationPhase> searchByProjectIdAndName(String projectId, String name) {
        return jdbcTemplate.query(
            "SELECT * FROM migration_phase WHERE migration_id = ? AND name LIKE ? ORDER BY name",
            rowMapper,
            projectId,
            "%" + name + "%"
        );
    }

    public MigrationPhase save(MigrationPhase phase) {
        if (phase.getId() == null || findById(phase.getId()).isEmpty()) {
            // Insert
            jdbcTemplate.update(
                "INSERT INTO migration_phase (id, name, type, migration_id, source, target, created_at, last_updated, target_tape_partition) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                phase.getId(),
                phase.getName(),
                phase.getType(),
                phase.getMigrationId(),
                phase.getSource(),
                phase.getTarget(),
                phase.getCreatedAt(),
                phase.getLastUpdated(),
                phase.getTargetTapePartition()
            );
        } else {
            // Update
            phase.setLastUpdated(LocalDate.now());
            jdbcTemplate.update(
                "UPDATE migration_phase SET name = ?, type = ?, source = ?, target = ?, last_updated = ?, target_tape_partition = ? WHERE id = ?",
                phase.getName(),
                phase.getType(),
                phase.getSource(),
                phase.getTarget(),
                phase.getLastUpdated(),
                phase.getTargetTapePartition(),
                phase.getId()
            );
        }
        return phase;
    }

    public void deleteById(String id) {
        jdbcTemplate.update(
            "DELETE FROM migration_phase WHERE id = ?",
            id
        );
    }
}
