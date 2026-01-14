package com.spectralogic.migrationtracker.service;

import com.spectralogic.migrationtracker.api.dto.Bucket;
import com.spectralogic.migrationtracker.config.PostgreSQLConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BucketService {

    private static final Logger logger = LoggerFactory.getLogger(BucketService.class);
    private final PostgreSQLConfig postgresConfig;

    public BucketService(PostgreSQLConfig postgresConfig) {
        this.postgresConfig = postgresConfig;
    }

    public List<Bucket> getBlackPearlBuckets() {
        List<Bucket> buckets = new ArrayList<>();
        
        try {
            JdbcTemplate jdbc = postgresConfig.getBlackPearlJdbcTemplate();
            logger.debug("Attempting to query BlackPearl buckets...");
            
            // Query buckets from BlackPearl database
            // Try multiple query patterns to find the right schema
            List<Map<String, Object>> results = new ArrayList<>();
            
            // Try 1: Direct buckets table
            try {
                logger.debug("Trying query: SELECT name FROM buckets");
                results = jdbc.query(
                    "SELECT name, COALESCE(object_count, 0) as object_count, COALESCE(size_bytes, 0) as size_bytes " +
                    "FROM buckets ORDER BY name",
                    (rs, rowNum) -> {
                        Map<String, Object> row = new HashMap<>();
                        row.put("name", rs.getString("name"));
                        row.put("objectCount", rs.getLong("object_count"));
                        row.put("sizeBytes", rs.getLong("size_bytes"));
                        return row;
                    }
                );
                logger.info("Successfully queried BlackPearl buckets from 'buckets' table: {} buckets found", results.size());
            } catch (Exception e) {
                logger.debug("Query 1 failed: {}", e.getMessage(), e);
                
                // Check if it's a connection error
                String errorMsg = e.getMessage();
                if (errorMsg != null && (errorMsg.contains("Connection refused") || 
                                         errorMsg.contains("Failed to obtain JDBC Connection") ||
                                         errorMsg.contains("authentication failed") ||
                                         errorMsg.contains("does not exist"))) {
                    logger.warn("BlackPearl database connection failed: {}. Check if PostgreSQL is running and credentials are correct.", errorMsg);
                }
                
                // Try 2: Aggregate from objects table
                try {
                    logger.debug("Trying query: SELECT bucket_name FROM objects GROUP BY bucket_name");
                    results = jdbc.query(
                        "SELECT bucket_name as name, COUNT(*) as object_count, SUM(size) as size_bytes " +
                        "FROM objects GROUP BY bucket_name ORDER BY bucket_name",
                        (rs, rowNum) -> {
                            Map<String, Object> row = new HashMap<>();
                            row.put("name", rs.getString("name"));
                            row.put("objectCount", rs.getLong("object_count"));
                            row.put("sizeBytes", rs.getLong("size_bytes"));
                            return row;
                        }
                    );
                    logger.info("Successfully queried BlackPearl buckets from 'objects' table: {} buckets found", results.size());
                } catch (Exception e2) {
                    logger.debug("Query 2 failed: {}", e2.getMessage(), e2);
                    
                    // Try 3: Check what tables exist (only if we can connect)
                    if (!e2.getMessage().contains("Connection refused") && 
                        !e2.getMessage().contains("Failed to obtain JDBC Connection")) {
                        try {
                            List<String> tables = jdbc.query(
                                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name",
                                (rs, rowNum) -> rs.getString("table_name")
                            );
                            logger.warn("Could not find buckets table. Available tables in BlackPearl: {}", tables);
                        } catch (Exception e3) {
                            logger.error("Could not list tables from BlackPearl: {}", e3.getMessage());
                        }
                    }
                }
            }

            for (Map<String, Object> row : results) {
                Bucket bucket = new Bucket();
                bucket.setName((String) row.get("name"));
                bucket.setSource("blackpearl");
                bucket.setObjectCount((Long) row.get("objectCount"));
                bucket.setSizeBytes((Long) row.get("sizeBytes"));
                buckets.add(bucket);
            }
        } catch (Exception e) {
            logger.error("Error fetching BlackPearl buckets: {}", e.getMessage(), e);
        }

        return buckets;
    }

    public List<Bucket> getRioBuckets() {
        List<Bucket> buckets = new ArrayList<>();
        
        try {
            JdbcTemplate jdbc = postgresConfig.getRioJdbcTemplate();
            logger.debug("Attempting to query Rio buckets...");
            
            // Query buckets from Rio database
            // Try multiple query patterns to find the right schema
            List<Map<String, Object>> results = new ArrayList<>();
            
            // Try 1: Direct buckets table
            try {
                logger.debug("Trying query: SELECT name FROM buckets");
                results = jdbc.query(
                    "SELECT name, COALESCE(object_count, 0) as object_count, COALESCE(size_bytes, 0) as size_bytes " +
                    "FROM buckets ORDER BY name",
                    (rs, rowNum) -> {
                        Map<String, Object> row = new HashMap<>();
                        row.put("name", rs.getString("name"));
                        row.put("objectCount", rs.getLong("object_count"));
                        row.put("sizeBytes", rs.getLong("size_bytes"));
                        return row;
                    }
                );
                logger.info("Successfully queried Rio buckets from 'buckets' table: {} buckets found", results.size());
            } catch (Exception e) {
                logger.debug("Query 1 failed: {}", e.getMessage(), e);
                
                // Check if it's a connection error
                String errorMsg = e.getMessage();
                if (errorMsg != null && (errorMsg.contains("Connection refused") || 
                                         errorMsg.contains("Failed to obtain JDBC Connection") ||
                                         errorMsg.contains("authentication failed") ||
                                         errorMsg.contains("does not exist"))) {
                    logger.warn("Rio database connection failed: {}. Check if PostgreSQL is running and credentials are correct.", errorMsg);
                }
                
                // Try 2: Aggregate from objects table
                try {
                    logger.debug("Trying query: SELECT bucket_name FROM objects GROUP BY bucket_name");
                    results = jdbc.query(
                        "SELECT bucket_name as name, COUNT(*) as object_count, SUM(size) as size_bytes " +
                        "FROM objects GROUP BY bucket_name ORDER BY bucket_name",
                        (rs, rowNum) -> {
                            Map<String, Object> row = new HashMap<>();
                            row.put("name", rs.getString("name"));
                            row.put("objectCount", rs.getLong("object_count"));
                            row.put("sizeBytes", rs.getLong("size_bytes"));
                            return row;
                        }
                    );
                    logger.info("Successfully queried Rio buckets from 'objects' table: {} buckets found", results.size());
                } catch (Exception e2) {
                    logger.debug("Query 2 failed: {}", e2.getMessage(), e2);
                    
                    // Try 3: Check what tables exist (only if we can connect)
                    if (!e2.getMessage().contains("Connection refused") && 
                        !e2.getMessage().contains("Failed to obtain JDBC Connection")) {
                        try {
                            List<String> tables = jdbc.query(
                                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name",
                                (rs, rowNum) -> rs.getString("table_name")
                            );
                            logger.warn("Could not find buckets table. Available tables in Rio: {}", tables);
                        } catch (Exception e3) {
                            logger.error("Could not list tables from Rio: {}", e3.getMessage());
                        }
                    }
                }
            }

            for (Map<String, Object> row : results) {
                Bucket bucket = new Bucket();
                bucket.setName((String) row.get("name"));
                bucket.setSource("rio");
                bucket.setObjectCount((Long) row.get("objectCount"));
                bucket.setSizeBytes((Long) row.get("sizeBytes"));
                buckets.add(bucket);
            }
        } catch (Exception e) {
            logger.error("Error fetching Rio buckets: {}", e.getMessage(), e);
        }

        return buckets;
    }

    public List<Bucket> getAllBuckets() {
        List<Bucket> allBuckets = new ArrayList<>();
        allBuckets.addAll(getBlackPearlBuckets());
        allBuckets.addAll(getRioBuckets());
        return allBuckets;
    }
}
