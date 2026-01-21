package com.spectralogic.migrationtracker.service;

import com.spectralogic.migrationtracker.api.dto.Bucket;
import com.spectralogic.migrationtracker.config.PostgreSQLConfig;
import com.spectralogic.migrationtracker.model.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BucketService {

    private static final Logger logger = LoggerFactory.getLogger(BucketService.class);
    private final PostgreSQLConfig postgresConfig;
    private final CustomerService customerService;

    @Value("${postgres.blackpearl.host:localhost}")
    private String blackpearlHost;

    @Value("${postgres.blackpearl.port:5432}")
    private int blackpearlPort;

    @Value("${postgres.blackpearl.username:postgres}")
    private String blackpearlUsername;

    @Value("${postgres.blackpearl.password:}")
    private String blackpearlPassword;

    @Value("${postgres.rio.host:localhost}")
    private String rioHost;

    @Value("${postgres.rio.port:5432}")
    private int rioPort;

    @Value("${postgres.rio.username:postgres}")
    private String rioUsername;

    @Value("${postgres.rio.password:}")
    private String rioPassword;

    public BucketService(PostgreSQLConfig postgresConfig, CustomerService customerService) {
        this.postgresConfig = postgresConfig;
        this.customerService = customerService;
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
        // Temporarily ignoring Rio - uncomment when ready
        // allBuckets.addAll(getRioBuckets());
        return allBuckets;
    }

    /**
     * Get buckets for a specific customer from their customer-specific database
     */
    public List<Bucket> getBucketsForCustomer(String customerId, String databaseType) {
        List<Bucket> buckets = new ArrayList<>();
        
        try {
            Customer customer = customerService.findById(customerId);
            String customerName = customer.getName().toLowerCase().replaceAll("[^a-z0-9]", "_");
            
            // Construct customer-specific database name
            String databaseName;
            String host;
            int port;
            String username;
            String password;
            
            if (databaseType.equalsIgnoreCase("blackpearl")) {
                databaseName = "tapesystem_" + customerName;
                host = blackpearlHost;
                port = blackpearlPort;
                username = blackpearlUsername;
                password = blackpearlPassword != null ? blackpearlPassword : "";
            } else {
                databaseName = "rio_db_" + customerName;
                host = rioHost;
                port = rioPort;
                username = rioUsername;
                password = rioPassword != null ? rioPassword : "";
            }
            
            logger.info("Querying buckets from {} database: {}@{}:{}/{}", 
                databaseType, username, host, port, databaseName);
            
            // Create data source for customer-specific database
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.postgresql.Driver");
            dataSource.setUrl(String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseName));
            dataSource.setUsername(username);
            dataSource.setPassword(password);
            
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            
            // Try customer-specific database first, fallback to generic
            String actualDatabaseName = databaseName;
            try {
                jdbc.query("SELECT 1", (rs, rowNum) -> rs.getInt(1));
                logger.debug("Successfully connected to customer-specific database: {}", databaseName);
            } catch (Exception e) {
                logger.warn("Cannot connect to customer-specific database {}: {}. Trying generic database as fallback.", databaseName, e.getMessage());
                String genericDatabaseName = databaseType.equalsIgnoreCase("blackpearl") ? "tapesystem" : "rio_db";
                try {
                    dataSource.setUrl(String.format("jdbc:postgresql://%s:%d/%s", host, port, genericDatabaseName));
                    jdbc = new JdbcTemplate(dataSource);
                    jdbc.query("SELECT 1", (rs, rowNum) -> rs.getInt(1));
                    actualDatabaseName = genericDatabaseName;
                    logger.info("Successfully connected to generic database: {}. Using this for bucket queries.", genericDatabaseName);
                } catch (Exception e2) {
                    logger.error("Cannot connect to either customer-specific database {} or generic database {}: {}. Please ensure the database has been restored.", 
                        databaseName, genericDatabaseName, e2.getMessage());
                    return buckets; // Return empty list
                }
            }
            
            logger.info("Querying buckets from database: {}@{}:{}/{}", username, host, port, actualDatabaseName);
            
            // Query buckets - try multiple patterns
            List<Map<String, Object>> results = new ArrayList<>();
            
            // Try 1: ds3.bucket with ds3.s3_object and ds3.blob (most accurate for BlackPearl)
            try {
                results = jdbc.query(
                    "SELECT b.name, COUNT(DISTINCT so.id) as object_count, COALESCE(SUM(bl.length), 0) as size_bytes " +
                    "FROM ds3.bucket b " +
                    "LEFT JOIN ds3.s3_object so ON so.bucket_id = b.id " +
                    "LEFT JOIN ds3.blob bl ON bl.object_id = so.id " +
                    "GROUP BY b.name ORDER BY b.name",
                    (rs, rowNum) -> {
                        Map<String, Object> row = new HashMap<>();
                        row.put("name", rs.getString("name"));
                        row.put("objectCount", rs.getLong("object_count"));
                        row.put("sizeBytes", rs.getLong("size_bytes"));
                        return row;
                    }
                );
                logger.info("Successfully queried buckets from ds3.bucket: {} buckets found", results.size());
            } catch (Exception e) {
                logger.debug("Query ds3.bucket failed: {}", e.getMessage());
                
                // Try 2: Direct buckets table
                try {
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
                    logger.info("Successfully queried buckets from 'buckets' table: {} buckets found", results.size());
                } catch (Exception e2) {
                    logger.debug("Query buckets table failed: {}", e2.getMessage());
                    
                    // Try 3: Aggregate from objects table
                    try {
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
                        logger.info("Successfully queried buckets from 'objects' table: {} buckets found", results.size());
                    } catch (Exception e3) {
                        logger.error("All bucket query patterns failed for customer {}: {}", customerId, e3.getMessage());
                    }
                }
            }
            
            for (Map<String, Object> row : results) {
                Bucket bucket = new Bucket();
                bucket.setName((String) row.get("name"));
                bucket.setSource(databaseType.toLowerCase());
                bucket.setObjectCount((Long) row.get("objectCount"));
                bucket.setSizeBytes((Long) row.get("sizeBytes"));
                buckets.add(bucket);
            }
        } catch (Exception e) {
            logger.error("Error fetching buckets for customer {}: {}", customerId, e.getMessage(), e);
        }
        
        return buckets;
    }

    /**
     * Get bucket size for a specific customer and bucket name
     */
    public Bucket getBucketSize(String customerId, String bucketName, String databaseType) {
        List<Bucket> buckets = getBucketsForCustomer(customerId, databaseType);
        return buckets.stream()
            .filter(b -> b.getName().equals(bucketName))
            .findFirst()
            .orElse(null);
    }
}
