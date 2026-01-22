package com.spectralogic.migrationtracker.repository;

import com.spectralogic.migrationtracker.model.BucketData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class BucketDataRepository {

    private static final Logger logger = LoggerFactory.getLogger(BucketDataRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public BucketDataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureTableExists() {
        try {
            // Check if table exists
            String checkTableSql = "SELECT name FROM sqlite_master WHERE type='table' AND name='bucket_data'";
            List<String> tables = jdbcTemplate.query(checkTableSql, (rs, rowNum) -> rs.getString("name"));
            
            if (tables.isEmpty()) {
                logger.info("Creating bucket_data table");
                String createTableSql = 
                    "CREATE TABLE bucket_data (" +
                    "  id TEXT PRIMARY KEY," +
                    "  created_at TEXT NOT NULL," +
                    "  last_updated TEXT NOT NULL," +
                    "  timestamp TEXT NOT NULL," +
                    "  migration_phase_id TEXT NOT NULL," +
                    "  bucket_name TEXT NOT NULL," +
                    "  source TEXT NOT NULL," +
                    "  storage_domain TEXT," +
                    "  object_count INTEGER," +
                    "  size_bytes INTEGER," +
                    "  user_id TEXT," +
                    "  FOREIGN KEY (migration_phase_id) REFERENCES migration_phase(id)" +
                    ")";
                jdbcTemplate.execute(createTableSql);
                logger.info("bucket_data table created successfully");
            } else {
                // Check if storage_domain column exists, add it if not (migration)
                try {
                    jdbcTemplate.query("SELECT storage_domain FROM bucket_data LIMIT 1", (rs, rowNum) -> rs.getString("storage_domain"));
                } catch (Exception e) {
                    logger.info("Adding storage_domain column to bucket_data table");
                    jdbcTemplate.execute("ALTER TABLE bucket_data ADD COLUMN storage_domain TEXT");
                    logger.info("storage_domain column added successfully");
                }
            }
        } catch (Exception e) {
            logger.error("Error ensuring bucket_data table exists: {}", e.getMessage(), e);
        }
    }

    @SuppressWarnings("null")
    private final RowMapper<BucketData> rowMapper = new RowMapper<BucketData>() {
        @Override
        @SuppressWarnings("null")
        public BucketData mapRow(ResultSet rs, int rowNum) throws SQLException {
            BucketData data = new BucketData();
            data.setId(rs.getString("id"));
            String createdAtStr = rs.getString("created_at");
            data.setCreatedAt(createdAtStr != null ? LocalDate.parse(createdAtStr) : LocalDate.now());
            String lastUpdatedStr = rs.getString("last_updated");
            data.setLastUpdated(lastUpdatedStr != null ? LocalDate.parse(lastUpdatedStr) : LocalDate.now());
            String timestampStr = rs.getString("timestamp");
            data.setTimestamp(timestampStr != null ? LocalDate.parse(timestampStr) : LocalDate.now());
            data.setMigrationPhaseId(rs.getString("migration_phase_id"));
            data.setBucketName(rs.getString("bucket_name"));
            data.setSource(rs.getString("source"));
            // Handle storage_domain column (may not exist in older databases)
            try {
                String storageDomain = rs.getString("storage_domain");
                data.setStorageDomain(storageDomain);
            } catch (Exception e) {
                // Column doesn't exist, ignore
                data.setStorageDomain(null);
            }
            data.setObjectCount(rs.getLong("object_count"));
            data.setSizeBytes(rs.getLong("size_bytes"));
            data.setUserId(rs.getString("user_id"));
            return data;
        }
    };

    @SuppressWarnings("null")
    public List<BucketData> findByPhaseId(String phaseId) {
        return jdbcTemplate.query(
            "SELECT * FROM bucket_data WHERE migration_phase_id = ? ORDER BY timestamp DESC, bucket_name",
            rowMapper,
            phaseId
        );
    }

    @SuppressWarnings("null")
    public List<BucketData> findByPhaseIdAndBucketName(String phaseId, String bucketName) {
        return jdbcTemplate.query(
            "SELECT * FROM bucket_data WHERE migration_phase_id = ? AND bucket_name = ? ORDER BY timestamp DESC",
            rowMapper,
            phaseId,
            bucketName
        );
    }

    @SuppressWarnings("null")
    public List<BucketData> findByPhaseIdAndDateRange(String phaseId, LocalDate from, LocalDate to) {
        return jdbcTemplate.query(
            "SELECT * FROM bucket_data WHERE migration_phase_id = ? AND timestamp >= ? AND timestamp <= ? ORDER BY timestamp DESC, bucket_name",
            rowMapper,
            phaseId,
            from.toString(),
            to.toString()
        );
    }

    @SuppressWarnings("null")
    public Optional<BucketData> findById(String id) {
        List<BucketData> results = jdbcTemplate.query(
            "SELECT * FROM bucket_data WHERE id = ?",
            rowMapper,
            id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @SuppressWarnings("null")
    public List<BucketData> findByBucketName(String bucketName) {
        return jdbcTemplate.query(
            "SELECT * FROM bucket_data WHERE bucket_name = ? ORDER BY timestamp DESC",
            rowMapper,
            bucketName
        );
    }

    public BucketData save(BucketData data) {
        if (data.getId() == null || findById(data.getId()).isEmpty()) {
            // Insert
            jdbcTemplate.update(
                "INSERT INTO bucket_data (id, created_at, last_updated, timestamp, migration_phase_id, bucket_name, source, storage_domain, object_count, size_bytes, user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                data.getId(),
                data.getCreatedAt().toString(),
                data.getLastUpdated().toString(),
                data.getTimestamp().toString(),
                data.getMigrationPhaseId(),
                data.getBucketName(),
                data.getSource(),
                data.getStorageDomain(),
                data.getObjectCount(),
                data.getSizeBytes(),
                data.getUserId()
            );
        } else {
            // Update
            data.setLastUpdated(LocalDate.now());
            jdbcTemplate.update(
                "UPDATE bucket_data SET timestamp = ?, object_count = ?, size_bytes = ?, last_updated = ? WHERE id = ?",
                data.getTimestamp().toString(),
                data.getObjectCount(),
                data.getSizeBytes(),
                data.getLastUpdated().toString(),
                data.getId()
            );
        }
        return data;
    }

    public void deleteByPhaseId(String phaseId) {
        jdbcTemplate.update("DELETE FROM bucket_data WHERE migration_phase_id = ?", phaseId);
    }
}
