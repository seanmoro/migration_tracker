package com.spectralogic.migrationtracker.repository;

import com.spectralogic.migrationtracker.model.MigrationData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class MigrationDataRepository {

    private final JdbcTemplate jdbcTemplate;

    public MigrationDataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<MigrationData> rowMapper = new RowMapper<MigrationData>() {
        @Override
        public MigrationData mapRow(ResultSet rs, int rowNum) throws SQLException {
            MigrationData data = new MigrationData();
            data.setId(rs.getString("id"));
            // SQLite stores dates as strings
            String createdAtStr = rs.getString("created_at");
            data.setCreatedAt(createdAtStr != null ? LocalDate.parse(createdAtStr) : LocalDate.now());
            String lastUpdatedStr = rs.getString("last_updated");
            data.setLastUpdated(lastUpdatedStr != null ? LocalDate.parse(lastUpdatedStr) : LocalDate.now());
            String timestampStr = rs.getString("timestamp");
            data.setTimestamp(timestampStr != null ? LocalDate.parse(timestampStr) : LocalDate.now());
            data.setMigrationPhaseId(rs.getString("migration_phase_id"));
            data.setUserId(rs.getString("user_id"));
            data.setSourceObjects(rs.getLong("source_objects"));
            data.setSourceSize(rs.getLong("source_size"));
            data.setTargetObjects(rs.getLong("target_objects"));
            data.setTargetSize(rs.getLong("target_size"));
            data.setType(rs.getString("type"));
            
            // Handle target_scratch_tapes - may be null, string, or integer in SQLite
            Integer scratchTapes = null;
            try {
                Object scratchTapesObj = rs.getObject("target_scratch_tapes");
                if (scratchTapesObj != null) {
                    if (scratchTapesObj instanceof Integer) {
                        scratchTapes = (Integer) scratchTapesObj;
                    } else if (scratchTapesObj instanceof Long) {
                        scratchTapes = ((Long) scratchTapesObj).intValue();
                    } else if (scratchTapesObj instanceof String) {
                        String str = (String) scratchTapesObj;
                        if (!str.isEmpty()) {
                            scratchTapes = Integer.parseInt(str);
                        }
                    } else {
                        // Try to convert via string
                        scratchTapes = Integer.parseInt(scratchTapesObj.toString());
                    }
                }
            } catch (Exception e) {
                // If conversion fails, leave as null
                scratchTapes = null;
            }
            data.setTargetScratchTapes(scratchTapes);
            return data;
        }
    };

    public List<MigrationData> findByPhaseId(String phaseId) {
        return jdbcTemplate.query(
            "SELECT * FROM migration_data WHERE migration_phase_id = ? ORDER BY timestamp DESC",
            rowMapper,
            phaseId
        );
    }

    public List<MigrationData> findByPhaseIdAndDateRange(String phaseId, LocalDate from, LocalDate to) {
        return jdbcTemplate.query(
            "SELECT * FROM migration_data WHERE migration_phase_id = ? AND timestamp >= ? AND timestamp <= ? ORDER BY timestamp DESC",
            rowMapper,
            phaseId,
            from,
            to
        );
    }

    public Optional<MigrationData> findById(String id) {
        List<MigrationData> results = jdbcTemplate.query(
            "SELECT * FROM migration_data WHERE id = ?",
            rowMapper,
            id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<MigrationData> findLatestByPhaseId(String phaseId) {
        List<MigrationData> results = jdbcTemplate.query(
            "SELECT * FROM migration_data WHERE migration_phase_id = ? ORDER BY timestamp DESC LIMIT 1",
            rowMapper,
            phaseId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<MigrationData> findReferenceByPhaseId(String phaseId) {
        List<MigrationData> results = jdbcTemplate.query(
            "SELECT * FROM migration_data WHERE migration_phase_id = ? AND type = 'REFERENCE' ORDER BY timestamp DESC LIMIT 1",
            rowMapper,
            phaseId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public MigrationData save(MigrationData data) {
        if (data.getId() == null || findById(data.getId()).isEmpty()) {
            // Insert
            jdbcTemplate.update(
                "INSERT INTO migration_data (id, created_at, last_updated, timestamp, migration_phase_id, user_id, source_objects, source_size, target_objects, target_size, type, target_scratch_tapes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                data.getId(),
                data.getCreatedAt(),
                data.getLastUpdated(),
                data.getTimestamp(),
                data.getMigrationPhaseId(),
                data.getUserId(),
                data.getSourceObjects(),
                data.getSourceSize(),
                data.getTargetObjects(),
                data.getTargetSize(),
                data.getType(),
                data.getTargetScratchTapes()
            );
        } else {
            // Update
            data.setLastUpdated(LocalDate.now());
            jdbcTemplate.update(
                "UPDATE migration_data SET timestamp = ?, source_objects = ?, source_size = ?, target_objects = ?, target_size = ?, type = ?, target_scratch_tapes = ?, last_updated = ? WHERE id = ?",
                data.getTimestamp(),
                data.getSourceObjects(),
                data.getSourceSize(),
                data.getTargetObjects(),
                data.getTargetSize(),
                data.getType(),
                data.getTargetScratchTapes(),
                data.getLastUpdated(),
                data.getId()
            );
        }
        return data;
    }
}
