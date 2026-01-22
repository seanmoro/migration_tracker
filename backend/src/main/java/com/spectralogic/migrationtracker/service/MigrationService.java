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
import java.util.List;

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

            // Query objects by storage domain - use correct schema relationship
            // Try data_policy first (most direct), then fallback to pool/tape
            long totalObjects = 0L;
            long totalSize = 0L;

            // Pattern 1: Storage Domain -> Data Persistence Rule -> Data Policy -> Bucket -> Objects
            // This is the correct relationship: storage_domain -> data_persistence_rule -> data_policy -> bucket -> s3_object
            try {
                Long count = jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT so.id) " +
                    "FROM ds3.storage_domain sd " +
                    "JOIN ds3.data_persistence_rule dpr ON dpr.storage_domain_id = sd.id " +
                    "JOIN ds3.data_policy dp ON dp.id = dpr.data_policy_id " +
                    "JOIN ds3.bucket b ON b.data_policy_id = dp.id " +
                    "JOIN ds3.s3_object so ON so.bucket_id = b.id " +
                    "WHERE sd.name ILIKE ?",
                    Long.class,
                    storageDomain
                );
                Long size = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(bl.length), 0) " +
                    "FROM ds3.storage_domain sd " +
                    "JOIN ds3.data_persistence_rule dpr ON dpr.storage_domain_id = sd.id " +
                    "JOIN ds3.data_policy dp ON dp.id = dpr.data_policy_id " +
                    "JOIN ds3.bucket b ON b.data_policy_id = dp.id " +
                    "JOIN ds3.s3_object so ON so.bucket_id = b.id " +
                    "LEFT JOIN ds3.blob bl ON bl.object_id = so.id " +
                    "WHERE sd.name ILIKE ?",
                    Long.class,
                    storageDomain
                );
                if (count != null && size != null) {
                    totalObjects = count;
                    totalSize = size;
                    logger.info("Successfully queried storage domain '{}' via data_persistence_rule: {} objects, {} bytes", storageDomain, count, size);
                }
            } catch (Exception e) {
                logger.warn("Query via data_persistence_rule failed for storage domain '{}': {}", storageDomain, e.getMessage());
                
                // Pattern 2: Storage Domain -> Storage Domain Member -> Pool/Tape -> Bucket -> Objects
                // Fallback pattern: storage_domain -> storage_domain_member -> pool.pool or tape.tape -> bucket -> s3_object
                try {
                    Long count = jdbc.queryForObject(
                        "SELECT COUNT(DISTINCT so.id) " +
                        "FROM ds3.storage_domain sd " +
                        "JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id " +
                        "LEFT JOIN pool.pool p ON p.storage_domain_member_id = sdm.id " +
                        "LEFT JOIN tape.tape t ON t.storage_domain_member_id = sdm.id " +
                        "LEFT JOIN ds3.bucket b ON (b.id = p.bucket_id OR b.id = t.bucket_id) " +
                        "JOIN ds3.s3_object so ON so.bucket_id = b.id " +
                        "WHERE sd.name ILIKE ? AND b.id IS NOT NULL",
                        Long.class,
                        storageDomain
                    );
                    Long size = jdbc.queryForObject(
                        "SELECT COALESCE(SUM(bl.length), 0) " +
                        "FROM ds3.storage_domain sd " +
                        "JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id " +
                        "LEFT JOIN pool.pool p ON p.storage_domain_member_id = sdm.id " +
                        "LEFT JOIN tape.tape t ON t.storage_domain_member_id = sdm.id " +
                        "LEFT JOIN ds3.bucket b ON (b.id = p.bucket_id OR b.id = t.bucket_id) " +
                        "JOIN ds3.s3_object so ON so.bucket_id = b.id " +
                        "LEFT JOIN ds3.blob bl ON bl.object_id = so.id " +
                        "WHERE sd.name ILIKE ? AND b.id IS NOT NULL",
                        Long.class,
                        storageDomain
                    );
                    if (count != null && size != null) {
                        totalObjects = count;
                        totalSize = size;
                        logger.info("Successfully queried storage domain '{}' via pool/tape: {} objects, {} bytes", storageDomain, count, size);
                    }
                } catch (Exception e2) {
                    logger.warn("Query via pool/tape failed for storage domain '{}': {}", storageDomain, e2.getMessage());
                    
                    // Pattern 3: Fallback - Direct bucket name matching
                    try {
                        Long count = jdbc.queryForObject(
                            "SELECT COUNT(DISTINCT so.id) " +
                            "FROM ds3.bucket b " +
                            "JOIN ds3.s3_object so ON so.bucket_id = b.id " +
                            "WHERE b.name ILIKE ? OR b.name ILIKE ?",
                            Long.class,
                            storageDomain,
                            storageDomain + "%"
                        );
                        Long size = jdbc.queryForObject(
                            "SELECT COALESCE(SUM(bl.length), 0) " +
                            "FROM ds3.bucket b " +
                            "JOIN ds3.s3_object so ON so.bucket_id = b.id " +
                            "LEFT JOIN ds3.blob bl ON bl.object_id = so.id " +
                            "WHERE b.name ILIKE ? OR b.name ILIKE ?",
                            Long.class,
                            storageDomain,
                            storageDomain + "%"
                        );
                        if (count != null && size != null) {
                            totalObjects = count;
                            totalSize = size;
                            logger.info("Successfully queried storage domain '{}' from bucket name (fallback): {} objects, {} bytes", storageDomain, count, size);
                        }
                    } catch (Exception e3) {
                        logger.warn("Query by bucket name (fallback) failed for {}: {}", context, e3.getMessage());
                    }
                }
            }

            // If we got totals, create a single aggregate bucket data entry
            // Note: We're not storing per-bucket data anymore, just aggregate by storage domain
            // This matches how reporting works - query by storage domain, not by individual buckets
            if (totalObjects > 0 || totalSize > 0) {
                BucketData bucketData = new BucketData();
                bucketData.setMigrationPhaseId(phaseId);
                bucketData.setTimestamp(date);
                bucketData.setBucketName(storageDomain); // Use storage domain name as bucket name
                bucketData.setSource(databaseType.toLowerCase());
                bucketData.setObjectCount(totalObjects);
                bucketData.setSizeBytes(totalSize);
                bucketDataList.add(bucketDataRepository.save(bucketData));
                logger.info("Stored aggregate data for storage domain '{}' ({}): {} objects, {} bytes", storageDomain, context, totalObjects, totalSize);
            } else {
                logger.warn("No objects found for storage domain '{}' ({})", storageDomain, context);
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
