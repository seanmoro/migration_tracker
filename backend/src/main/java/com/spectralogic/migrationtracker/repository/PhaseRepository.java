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
        // Ensure columns exist on initialization
        ensureColumnsExist();
    }
    
    /**
     * Ensure required columns exist in the migration_phase table
     * This is called on repository initialization to handle existing databases
     */
    private void ensureColumnsExist() {
        // Ensure source_tape_partition column exists
        try {
            jdbcTemplate.query("SELECT source_tape_partition FROM migration_phase LIMIT 1", (rs) -> null);
        } catch (Exception e) {
            // Column doesn't exist, add it
            try {
                jdbcTemplate.execute("ALTER TABLE migration_phase ADD COLUMN source_tape_partition TEXT");
            } catch (Exception ex) {
                // Column might already exist or table doesn't exist, ignore
            }
        }
        
        // Ensure target_tape_partition column exists
        try {
            jdbcTemplate.query("SELECT target_tape_partition FROM migration_phase LIMIT 1", (rs) -> null);
        } catch (Exception e) {
            // Column doesn't exist, add it
            try {
                jdbcTemplate.execute("ALTER TABLE migration_phase ADD COLUMN target_tape_partition TEXT");
            } catch (Exception ex) {
                // Column might already exist or table doesn't exist, ignore
            }
        }
        
        // Ensure active column exists (for existing databases)
        try {
            jdbcTemplate.query("SELECT active FROM migration_phase LIMIT 1", (rs) -> null);
        } catch (Exception e) {
            // Column doesn't exist, add it with default value of 1 (active)
            try {
                jdbcTemplate.execute("ALTER TABLE migration_phase ADD COLUMN active INTEGER DEFAULT 1");
                // Update all existing phases to be active (since they existed before the column was added)
                jdbcTemplate.update("UPDATE migration_phase SET active = 1 WHERE active IS NULL");
            } catch (Exception ex) {
                // Column might already exist or table doesn't exist, ignore
            }
        }
    }

    @SuppressWarnings("null")
    private final RowMapper<MigrationPhase> rowMapper = new RowMapper<MigrationPhase>() {
        @Override
        @SuppressWarnings("null")
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
            // Handle source_tape_partition (may not exist in older databases)
            try {
                phase.setSourceTapePartition(rs.getString("source_tape_partition"));
            } catch (SQLException e) {
                // Column doesn't exist yet, set to null
                phase.setSourceTapePartition(null);
            }
            phase.setTargetTapePartition(rs.getString("target_tape_partition"));
            // Handle active field (may not exist in older databases)
            try {
                phase.setActive(rs.getBoolean("active"));
            } catch (SQLException e) {
                // Column doesn't exist yet, default to true
                phase.setActive(true);
            }
            return phase;
        }
    };

    @SuppressWarnings("null")
    public List<MigrationPhase> findByProjectId(String projectId) {
        return jdbcTemplate.query(
            "SELECT * FROM migration_phase WHERE migration_id = ? AND (active IS NULL OR active = 1) ORDER BY name",
            rowMapper,
            projectId
        );
    }

    @SuppressWarnings("null")
    public List<MigrationPhase> findByProjectIdIncludingInactive(String projectId) {
        return jdbcTemplate.query(
            "SELECT * FROM migration_phase WHERE migration_id = ? ORDER BY name",
            rowMapper,
            projectId
        );
    }

    @SuppressWarnings("null")
    public List<MigrationPhase> findActiveByProjectId(String projectId) {
        return jdbcTemplate.query(
            "SELECT * FROM migration_phase WHERE migration_id = ? AND (active IS NULL OR active = 1) ORDER BY name",
            rowMapper,
            projectId
        );
    }

    @SuppressWarnings("null")
    public Optional<MigrationPhase> findById(String id) {
        List<MigrationPhase> results = jdbcTemplate.query(
            "SELECT * FROM migration_phase WHERE id = ?",
            rowMapper,
            id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @SuppressWarnings("null")
    public List<MigrationPhase> searchByProjectIdAndName(String projectId, String name) {
        return jdbcTemplate.query(
            "SELECT * FROM migration_phase WHERE migration_id = ? AND name LIKE ? AND (active IS NULL OR active = 1) ORDER BY name",
            rowMapper,
            projectId,
            "%" + name + "%"
        );
    }

    @SuppressWarnings("null")
    public List<MigrationPhase> searchByProjectIdAndNameIncludingInactive(String projectId, String name) {
        return jdbcTemplate.query(
            "SELECT * FROM migration_phase WHERE migration_id = ? AND name LIKE ? ORDER BY name",
            rowMapper,
            projectId,
            "%" + name + "%"
        );
    }

    public MigrationPhase save(MigrationPhase phase) {
        // Columns are ensured on repository initialization, but check again here for safety
        ensureColumnsExist();
        
        if (phase.getId() == null || findById(phase.getId()).isEmpty()) {
            // Insert
            jdbcTemplate.update(
                "INSERT INTO migration_phase (id, name, type, migration_id, source, target, created_at, last_updated, source_tape_partition, target_tape_partition, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                phase.getId(),
                phase.getName(),
                phase.getType(),
                phase.getMigrationId(),
                phase.getSource(),
                phase.getTarget(),
                phase.getCreatedAt(),
                phase.getLastUpdated(),
                phase.getSourceTapePartition(),
                phase.getTargetTapePartition(),
                phase.getActive() != null ? phase.getActive() : true
            );
        } else {
            // Update
            phase.setLastUpdated(LocalDate.now());
            jdbcTemplate.update(
                "UPDATE migration_phase SET name = ?, type = ?, source = ?, target = ?, last_updated = ?, source_tape_partition = ?, target_tape_partition = ?, active = ? WHERE id = ?",
                phase.getName(),
                phase.getType(),
                phase.getSource(),
                phase.getTarget(),
                phase.getLastUpdated(),
                phase.getSourceTapePartition(),
                phase.getTargetTapePartition(),
                phase.getActive() != null ? phase.getActive() : true,
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
