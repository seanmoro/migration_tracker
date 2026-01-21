package com.spectralogic.migrationtracker.service;

import com.spectralogic.migrationtracker.model.BucketData;
import com.spectralogic.migrationtracker.model.Customer;
import com.spectralogic.migrationtracker.model.MigrationData;
import com.spectralogic.migrationtracker.model.MigrationPhase;
import com.spectralogic.migrationtracker.model.MigrationProject;
import com.spectralogic.migrationtracker.repository.BucketDataRepository;
import com.spectralogic.migrationtracker.repository.MigrationDataRepository;
import com.spectralogic.migrationtracker.repository.PhaseRepository;
import com.spectralogic.migrationtracker.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MigrationService {

    private static final Logger logger = LoggerFactory.getLogger(MigrationService.class);
    private final MigrationDataRepository repository;
    private final BucketDataRepository bucketDataRepository;
    private final PhaseRepository phaseRepository;
    private final ProjectRepository projectRepository;
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

    public MigrationService(
            MigrationDataRepository repository,
            BucketDataRepository bucketDataRepository,
            PhaseRepository phaseRepository,
            ProjectRepository projectRepository,
            CustomerService customerService) {
        this.repository = repository;
        this.bucketDataRepository = bucketDataRepository;
        this.phaseRepository = phaseRepository;
        this.projectRepository = projectRepository;
        this.customerService = customerService;
    }

    public MigrationData gatherData(String projectId, String phaseId, LocalDate date, List<String> selectedBuckets) {
        // Validate that data doesn't already exist for this date
        List<MigrationData> existing = repository.findByPhaseId(phaseId);
        for (MigrationData data : existing) {
            if (data.getTimestamp().equals(date)) {
                throw new RuntimeException("Data already exists for date: " + date);
            }
        }

        // Get phase and project to determine customer and database connections
        MigrationPhase phase = phaseRepository.findById(phaseId)
            .orElseThrow(() -> new RuntimeException("Phase not found: " + phaseId));
        MigrationProject project = projectRepository.findById(projectId)
            .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));
        Customer customer = customerService.findById(project.getCustomerId());

        String customerName = customer.getName().toLowerCase().replaceAll("[^a-z0-9]", "_");

        // Determine source and target database types from phase
        String sourceDbType = determineDatabaseType(phase.getSource());
        String targetDbType = determineDatabaseType(phase.getTarget());

        // Query source buckets and store per-bucket data
        long totalSourceObjects = 0L;
        long totalSourceSize = 0L;
        List<BucketData> sourceBucketData = queryAndStoreBucketData(
            phaseId, date, customerName, sourceDbType, phase.getSource(), selectedBuckets, "source");
        for (BucketData bucketData : sourceBucketData) {
            totalSourceObjects += bucketData.getObjectCount();
            totalSourceSize += bucketData.getSizeBytes();
        }

        // Query target buckets and store per-bucket data
        long totalTargetObjects = 0L;
        long totalTargetSize = 0L;
        List<BucketData> targetBucketData = queryAndStoreBucketData(
            phaseId, date, customerName, targetDbType, phase.getTarget(), selectedBuckets, "target");
        for (BucketData bucketData : targetBucketData) {
            totalTargetObjects += bucketData.getObjectCount();
            totalTargetSize += bucketData.getSizeBytes();
        }

        // Create aggregate migration data point
        MigrationData data = new MigrationData();
        data.setMigrationPhaseId(phaseId);
        data.setTimestamp(date);
        data.setType("DATA");
        data.setSourceObjects(totalSourceObjects);
        data.setSourceSize(totalSourceSize);
        data.setTargetObjects(totalTargetObjects);
        data.setTargetSize(totalTargetSize);

        return repository.save(data);
    }

    private String determineDatabaseType(String storageDomain) {
        // Simple heuristic: if it contains "rio" or "Rio", assume Rio, otherwise BlackPearl
        if (storageDomain != null && storageDomain.toLowerCase().contains("rio")) {
            return "rio";
        }
        return "blackpearl";
    }

    private List<BucketData> queryAndStoreBucketData(
            String phaseId, LocalDate date, String customerName, String databaseType,
            String storageDomain, List<String> selectedBuckets, String context) {
        List<BucketData> bucketDataList = new ArrayList<>();

        try {
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

            // Create data source for customer-specific database
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.postgresql.Driver");
            dataSource.setUrl(String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseName));
            dataSource.setUsername(username);
            dataSource.setPassword(password);

            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            // Try customer-specific database first, fallback to generic
            try {
                jdbc.query("SELECT 1", (rs, rowNum) -> rs.getInt(1));
                logger.debug("Successfully connected to customer-specific database: {}", databaseName);
            } catch (Exception e) {
                logger.warn("Cannot connect to customer-specific database {}: {}. Trying generic database.", databaseName, e.getMessage());
                String genericDatabaseName = databaseType.equalsIgnoreCase("blackpearl") ? "tapesystem" : "rio_db";
                dataSource.setUrl(String.format("jdbc:postgresql://%s:%d/%s", host, port, genericDatabaseName));
                jdbc = new JdbcTemplate(dataSource);
                try {
                    jdbc.query("SELECT 1", (rs, rowNum) -> rs.getInt(1));
                    logger.info("Successfully connected to generic database: {}", genericDatabaseName);
                } catch (Exception e2) {
                    logger.error("Cannot connect to either customer-specific or generic database: {}", e2.getMessage());
                    return bucketDataList; // Return empty list
                }
            }

            // Query buckets - try multiple patterns
            List<Map<String, Object>> results = new ArrayList<>();

            // Try 1: ds3.bucket with ds3.s3_object and ds3.blob (most accurate)
            try {
                String bucketFilter = "";
                if (selectedBuckets != null && !selectedBuckets.isEmpty()) {
                    String bucketList = String.join("','", selectedBuckets);
                    bucketFilter = " AND b.name IN ('" + bucketList + "')";
                }
                results = jdbc.query(
                    "SELECT b.name, COUNT(DISTINCT so.id) as object_count, COALESCE(SUM(bl.length), 0) as size_bytes " +
                    "FROM ds3.bucket b " +
                    "LEFT JOIN ds3.s3_object so ON so.bucket_id = b.id " +
                    "LEFT JOIN ds3.blob bl ON bl.object_id = so.id " +
                    "WHERE 1=1" + bucketFilter +
                    " GROUP BY b.name ORDER BY b.name",
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
                    String bucketFilter = "";
                    if (selectedBuckets != null && !selectedBuckets.isEmpty()) {
                        String bucketList = String.join("','", selectedBuckets);
                        bucketFilter = " AND name IN ('" + bucketList + "')";
                    }
                    results = jdbc.query(
                        "SELECT name, COALESCE(object_count, 0) as object_count, COALESCE(size_bytes, 0) as size_bytes " +
                        "FROM buckets WHERE 1=1" + bucketFilter + " ORDER BY name",
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
                        String bucketFilter = "";
                        if (selectedBuckets != null && !selectedBuckets.isEmpty()) {
                            String bucketList = String.join("','", selectedBuckets);
                            bucketFilter = " AND bucket_name IN ('" + bucketList + "')";
                        }
                        results = jdbc.query(
                            "SELECT bucket_name as name, COUNT(*) as object_count, SUM(size) as size_bytes " +
                            "FROM objects WHERE 1=1" + bucketFilter + " GROUP BY bucket_name ORDER BY bucket_name",
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
                        logger.error("All bucket query patterns failed for {}: {}", context, e3.getMessage());
                    }
                }
            }

            // Store per-bucket data
            for (Map<String, Object> row : results) {
                BucketData bucketData = new BucketData();
                bucketData.setMigrationPhaseId(phaseId);
                bucketData.setTimestamp(date);
                bucketData.setBucketName((String) row.get("name"));
                bucketData.setSource(databaseType.toLowerCase());
                bucketData.setObjectCount((Long) row.get("objectCount"));
                bucketData.setSizeBytes((Long) row.get("sizeBytes"));
                bucketDataList.add(bucketDataRepository.save(bucketData));
            }

            logger.info("Stored {} bucket data points for phase {} ({})", bucketDataList.size(), phaseId, context);
        } catch (Exception e) {
            logger.error("Error querying and storing bucket data for {}: {}", context, e.getMessage(), e);
        }

        return bucketDataList;
    }

    public List<MigrationData> getDataByPhase(String phaseId) {
        return repository.findByPhaseId(phaseId);
    }

    public List<BucketData> getBucketDataByPhase(String phaseId) {
        return bucketDataRepository.findByPhaseId(phaseId);
    }

    public List<BucketData> getBucketDataByPhaseAndBucket(String phaseId, String bucketName) {
        return bucketDataRepository.findByPhaseIdAndBucketName(phaseId, bucketName);
    }

    public List<BucketData> getBucketDataByPhaseAndDateRange(String phaseId, LocalDate from, LocalDate to) {
        return bucketDataRepository.findByPhaseIdAndDateRange(phaseId, from, to);
    }
}
