package com.spectralogic.migrationtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spectralogic.migrationtracker.api.dto.ExportOptions;
import com.spectralogic.migrationtracker.api.dto.Forecast;
import com.spectralogic.migrationtracker.api.dto.PhaseProgress;
import com.spectralogic.migrationtracker.config.PostgreSQLConfig;
import com.spectralogic.migrationtracker.model.Customer;
import com.spectralogic.migrationtracker.model.MigrationData;
import com.spectralogic.migrationtracker.model.MigrationPhase;
import com.spectralogic.migrationtracker.model.MigrationProject;
import com.spectralogic.migrationtracker.repository.MigrationDataRepository;
import com.spectralogic.migrationtracker.repository.PhaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReportService {

    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);
    
    private final PhaseRepository phaseRepository;
    private final MigrationDataRepository dataRepository;
    private final ProjectService projectService;
    private final CustomerService customerService;
    private final PostgreSQLConfig postgresConfig;

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

    public ReportService(PhaseRepository phaseRepository, MigrationDataRepository dataRepository,
                         ProjectService projectService, CustomerService customerService,
                         PostgreSQLConfig postgresConfig) {
        this.phaseRepository = phaseRepository;
        this.dataRepository = dataRepository;
        this.projectService = projectService;
        this.customerService = customerService;
        this.postgresConfig = postgresConfig;
    }

    public PhaseProgress getPhaseProgress(String phaseId) {
        MigrationPhase phase = phaseRepository.findById(phaseId)
            .orElseThrow(() -> new RuntimeException("Phase not found: " + phaseId));

        PhaseProgress progress = new PhaseProgress();
        progress.setPhaseId(phaseId);
        progress.setPhaseName(phase.getName());

        // Get customer ID from phase -> project -> customer
        MigrationProject project = projectService.findById(phase.getMigrationId());
        Customer customer = customerService.findById(project.getCustomerId());
        
        // Determine database type from phase source (default to blackpearl)
        String databaseType = determineDatabaseType(phase.getSource());
        
        logger.info("Querying progress for phase '{}' (source: '{}', target: '{}') for customer '{}'", 
            phase.getName(), phase.getSource(), phase.getTarget(), customer.getName());
        
        // Query PostgreSQL for current object counts from source and target storage domains
        long sourceObjects = queryObjectCountByStorageDomain(customer.getId(), phase.getSource(), databaseType);
        long sourceSize = querySizeByStorageDomain(customer.getId(), phase.getSource(), databaseType);
        long targetObjects = queryObjectCountByStorageDomain(customer.getId(), phase.getTarget(), databaseType);
        long targetSize = querySizeByStorageDomain(customer.getId(), phase.getTarget(), databaseType);
        
        // If queries returned 0, fallback to latest migration_data (if available)
        // This ensures we show something even if PostgreSQL queries fail
        if (sourceObjects == 0 && targetObjects == 0) {
            logger.warn("PostgreSQL queries returned 0 for phase '{}'. Falling back to stored migration_data.", phaseId);
            Optional<MigrationData> latest = dataRepository.findLatestByPhaseId(phaseId);
            if (latest.isPresent()) {
                MigrationData last = latest.get();
                sourceObjects = last.getSourceObjects() != null ? last.getSourceObjects() : 0L;
                sourceSize = last.getSourceSize() != null ? last.getSourceSize() : 0L;
                targetObjects = last.getTargetObjects() != null ? last.getTargetObjects() : 0L;
                targetSize = last.getTargetSize() != null ? last.getTargetSize() : 0L;
                logger.info("Using stored migration_data: source={} objects, target={} objects", sourceObjects, targetObjects);
            }
        }
        
        progress.setSourceObjects(sourceObjects);
        progress.setSourceSize(sourceSize);
        progress.setTargetObjects(targetObjects);
        progress.setTargetSize(targetSize);
        
        // Calculate progress: (target objects) / (source objects) * 100
        int progressPercent = sourceObjects > 0 
            ? (int) ((targetObjects * 100) / sourceObjects)
            : 0;
        
        progress.setProgress(Math.max(0, Math.min(100, progressPercent)));
        
        logger.info("Phase progress calculated: {}% (source: {} objects, target: {} objects)", 
            progressPercent, sourceObjects, targetObjects);

        return progress;
    }
    
    /**
     * Determine database type from storage domain name
     */
    private String determineDatabaseType(String storageDomain) {
        if (storageDomain != null && storageDomain.toLowerCase().contains("rio")) {
            return "rio";
        }
        return "blackpearl";
    }
    
    /**
     * Query PostgreSQL for object count by storage domain name
     */
    private long queryObjectCountByStorageDomain(String customerId, String storageDomainName, String databaseType) {
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
            } catch (Exception e) {
                logger.warn("Cannot connect to customer-specific database {}: {}. Trying generic database as fallback.", databaseName, e.getMessage());
                String genericDatabaseName = databaseType.equalsIgnoreCase("blackpearl") ? "tapesystem" : "rio_db";
                try {
                    dataSource.setUrl(String.format("jdbc:postgresql://%s:%d/%s", host, port, genericDatabaseName));
                    jdbc = new JdbcTemplate(dataSource);
                    jdbc.query("SELECT 1", (rs, rowNum) -> rs.getInt(1));
                    actualDatabaseName = genericDatabaseName;
                } catch (Exception e2) {
                    logger.error("Cannot connect to either customer-specific database {} or generic database {}: {}", 
                        databaseName, genericDatabaseName, e2.getMessage());
                    return 0L;
                }
            }
            
            // Query object count by storage domain
            // Try multiple query patterns to find the right schema
            // Use case-insensitive matching (ILIKE) for better compatibility
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
                    storageDomainName
                );
                if (count != null && count > 0) {
                    logger.info("Found {} objects for storage domain '{}' in database {} (pattern 1 - via data_persistence_rule)", count, storageDomainName, actualDatabaseName);
                    return count;
                }
            } catch (Exception e) {
                logger.warn("Query pattern 1 (via data_persistence_rule) failed for storage domain '{}': {}", storageDomainName, e.getMessage());
            }
            
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
                    storageDomainName
                );
                if (count != null && count > 0) {
                    logger.info("Found {} objects for storage domain '{}' in database {} (pattern 2 - via pool/tape)", count, storageDomainName, actualDatabaseName);
                    return count;
                }
            } catch (Exception e) {
                logger.warn("Query pattern 2 (via pool/tape) failed for storage domain '{}': {}", storageDomainName, e.getMessage());
            }
            
            // Pattern 3: Fallback - Direct bucket name matching (in case storage domain name matches bucket name)
            try {
                Long count = jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT so.id) " +
                    "FROM ds3.bucket b " +
                    "JOIN ds3.s3_object so ON so.bucket_id = b.id " +
                    "WHERE b.name ILIKE ? OR b.name ILIKE ?",
                    Long.class,
                    storageDomainName,
                    storageDomainName + "%"
                );
                if (count != null && count > 0) {
                    logger.info("Found {} objects for storage domain '{}' in database {} (pattern 3 - bucket name)", count, storageDomainName, actualDatabaseName);
                    return count;
                }
            } catch (Exception e) {
                logger.warn("Query pattern 3 (bucket name) failed for storage domain '{}': {}", storageDomainName, e.getMessage());
            }
            
            // Pattern 3: Query by storage domain name directly (if it's stored as a column in bucket)
            try {
                Long count = jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT so.id) " +
                    "FROM ds3.bucket b " +
                    "JOIN ds3.s3_object so ON so.bucket_id = b.id " +
                    "WHERE b.storage_domain ILIKE ? OR b.storage_domain_name ILIKE ?",
                    Long.class,
                    storageDomainName,
                    storageDomainName
                );
                if (count != null && count > 0) {
                    logger.info("Found {} objects for storage domain '{}' in database {} (pattern 3)", count, storageDomainName, actualDatabaseName);
                    return count;
                }
            } catch (Exception e) {
                logger.warn("Query pattern 3 failed for storage domain '{}': {}", storageDomainName, e.getMessage());
            }
            
            // Pattern 4: Fallback - try exact match (case-sensitive) as last resort
            try {
                Long count = jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT so.id) " +
                    "FROM ds3.storage_domain sd " +
                    "JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id " +
                    "JOIN ds3.bucket b ON (b.id = sdm.bucket_id OR b.id = sdm.tape_partition_id OR b.id = sdm.pool_partition_id) " +
                    "JOIN ds3.s3_object so ON so.bucket_id = b.id " +
                    "WHERE sd.name = ?",
                    Long.class,
                    storageDomainName
                );
                if (count != null && count > 0) {
                    logger.info("Found {} objects for storage domain '{}' in database {} (pattern 4 - exact match)", count, storageDomainName, actualDatabaseName);
                    return count;
                }
            } catch (Exception e) {
                logger.warn("Query pattern 4 (exact match) failed for storage domain '{}': {}", storageDomainName, e.getMessage());
            }
            
            logger.error("Could not query object count for storage domain '{}' in database {}. All query patterns failed.", storageDomainName, actualDatabaseName);
            return 0L;
        } catch (Exception e) {
            logger.error("Error querying object count for storage domain '{}': {}", storageDomainName, e.getMessage(), e);
            return 0L;
        }
    }
    
    /**
     * Query PostgreSQL for total size by storage domain name
     */
    private long querySizeByStorageDomain(String customerId, String storageDomainName, String databaseType) {
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
            } catch (Exception e) {
                logger.warn("Cannot connect to customer-specific database {}: {}. Trying generic database as fallback.", databaseName, e.getMessage());
                String genericDatabaseName = databaseType.equalsIgnoreCase("blackpearl") ? "tapesystem" : "rio_db";
                try {
                    dataSource.setUrl(String.format("jdbc:postgresql://%s:%d/%s", host, port, genericDatabaseName));
                    jdbc = new JdbcTemplate(dataSource);
                    jdbc.query("SELECT 1", (rs, rowNum) -> rs.getInt(1));
                    actualDatabaseName = genericDatabaseName;
                } catch (Exception e2) {
                    logger.error("Cannot connect to either customer-specific database {} or generic database {}: {}", 
                        databaseName, genericDatabaseName, e2.getMessage());
                    return 0L;
                }
            }
            
            // Query total size by storage domain
            // Try multiple query patterns to find the right schema
            // Use case-insensitive matching (ILIKE) for better compatibility
            // Pattern 1: Storage Domain -> Data Persistence Rule -> Data Policy -> Bucket -> Objects
            // This is the correct relationship: storage_domain -> data_persistence_rule -> data_policy -> bucket -> s3_object
            try {
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
                    storageDomainName
                );
                if (size != null && size > 0) {
                    logger.info("Found {} bytes for storage domain '{}' in database {} (pattern 1 - via data_persistence_rule)", size, storageDomainName, actualDatabaseName);
                    return size;
                }
            } catch (Exception e) {
                logger.warn("Query pattern 1 (via data_persistence_rule) failed for storage domain '{}': {}", storageDomainName, e.getMessage());
            }
            
            // Pattern 2: Storage Domain -> Storage Domain Member -> Pool/Tape -> Bucket -> Objects
            // Fallback pattern: storage_domain -> storage_domain_member -> pool.pool or tape.tape -> bucket -> s3_object
            try {
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
                    storageDomainName
                );
                if (size != null && size > 0) {
                    logger.info("Found {} bytes for storage domain '{}' in database {} (pattern 2 - via pool/tape)", size, storageDomainName, actualDatabaseName);
                    return size;
                }
            } catch (Exception e) {
                logger.warn("Query pattern 2 (via pool/tape) failed for storage domain '{}': {}", storageDomainName, e.getMessage());
            }
            
            // Pattern 3: Fallback - Direct bucket name matching
            try {
                Long size = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(bl.length), 0) " +
                    "FROM ds3.bucket b " +
                    "JOIN ds3.s3_object so ON so.bucket_id = b.id " +
                    "LEFT JOIN ds3.blob bl ON bl.object_id = so.id " +
                    "WHERE b.name ILIKE ? OR b.name ILIKE ?",
                    Long.class,
                    storageDomainName,
                    storageDomainName + "%"
                );
                if (size != null && size > 0) {
                    logger.info("Found {} bytes for storage domain '{}' in database {} (pattern 3 - bucket name)", size, storageDomainName, actualDatabaseName);
                    return size;
                }
            } catch (Exception e) {
                logger.warn("Query pattern 3 (bucket name) failed for storage domain '{}': {}", storageDomainName, e.getMessage());
            }
            
            // Pattern 3: Query by storage domain name directly (if it's stored as a column in bucket)
            try {
                Long size = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(bl.length), 0) " +
                    "FROM ds3.bucket b " +
                    "JOIN ds3.s3_object so ON so.bucket_id = b.id " +
                    "LEFT JOIN ds3.blob bl ON bl.object_id = so.id " +
                    "WHERE b.storage_domain ILIKE ? OR b.storage_domain_name ILIKE ?",
                    Long.class,
                    storageDomainName,
                    storageDomainName
                );
                if (size != null && size > 0) {
                    logger.info("Found {} bytes for storage domain '{}' in database {} (pattern 3)", size, storageDomainName, actualDatabaseName);
                    return size;
                }
            } catch (Exception e) {
                logger.warn("Query pattern 3 failed for storage domain '{}': {}", storageDomainName, e.getMessage());
            }
            
            // Pattern 4: Fallback - try exact match (case-sensitive) as last resort
            try {
                Long size = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(bl.length), 0) " +
                    "FROM ds3.storage_domain sd " +
                    "JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id " +
                    "JOIN ds3.bucket b ON (b.id = sdm.bucket_id OR b.id = sdm.tape_partition_id OR b.id = sdm.pool_partition_id) " +
                    "JOIN ds3.s3_object so ON so.bucket_id = b.id " +
                    "LEFT JOIN ds3.blob bl ON bl.object_id = so.id " +
                    "WHERE sd.name = ?",
                    Long.class,
                    storageDomainName
                );
                if (size != null && size > 0) {
                    logger.info("Found {} bytes for storage domain '{}' in database {} (pattern 4 - exact match)", size, storageDomainName, actualDatabaseName);
                    return size;
                }
            } catch (Exception e) {
                logger.warn("Query pattern 4 (exact match) failed for storage domain '{}': {}", storageDomainName, e.getMessage());
            }
            
            logger.error("Could not query size for storage domain '{}' in database {}. All query patterns failed.", storageDomainName, actualDatabaseName);
            return 0L;
        } catch (Exception e) {
            logger.error("Error querying size for storage domain '{}': {}", storageDomainName, e.getMessage(), e);
            return 0L;
        }
    }

    public List<MigrationData> getPhaseData(String phaseId, LocalDate from, LocalDate to) {
        if (from != null && to != null) {
            return dataRepository.findByPhaseIdAndDateRange(phaseId, from, to);
        }
        return dataRepository.findByPhaseId(phaseId);
    }

    public Forecast getForecast(String phaseId) {
        Optional<MigrationData> reference = dataRepository.findReferenceByPhaseId(phaseId);
        Optional<MigrationData> latest = dataRepository.findLatestByPhaseId(phaseId);
        List<MigrationData> allData = dataRepository.findByPhaseId(phaseId);

        Forecast forecast = new Forecast();
        
        if (reference.isPresent() && latest.isPresent() && allData.size() >= 2) {
            MigrationData ref = reference.get();
            MigrationData last = latest.get();
            
            long daysBetween = ChronoUnit.DAYS.between(ref.getTimestamp(), last.getTimestamp());
            long objectsMigrated = last.getTargetObjects() - ref.getTargetObjects();
            long objectsRemaining = ref.getSourceObjects() - last.getTargetObjects();
            
            if (daysBetween > 0 && objectsRemaining > 0) {
                long rate = objectsMigrated / daysBetween;
                long daysRemaining = rate > 0 ? objectsRemaining / rate : 0;
                
                forecast.setAverageRate(rate);
                forecast.setRemainingObjects(objectsRemaining);
                forecast.setRemainingSize(ref.getSourceSize() - last.getTargetSize());
                forecast.setEta(LocalDate.now().plusDays(daysRemaining));
                
                // Confidence based on data points
                int confidence = Math.min(95, 50 + (allData.size() * 5));
                forecast.setConfidence(confidence);
            } else {
                forecast.setAverageRate(0L);
                forecast.setRemainingObjects(0L);
                forecast.setRemainingSize(0L);
                forecast.setEta(LocalDate.now());
                forecast.setConfidence(0);
            }
        } else {
            forecast.setAverageRate(0L);
            forecast.setRemainingObjects(0L);
            forecast.setRemainingSize(0L);
            forecast.setEta(LocalDate.now());
            forecast.setConfidence(0);
        }

        return forecast;
    }

    public byte[] exportPhase(String phaseId, ExportOptions options) throws IOException {
        MigrationPhase phase = phaseRepository.findById(phaseId)
            .orElseThrow(() -> new RuntimeException("Phase not found: " + phaseId));

        PhaseProgress progress = getPhaseProgress(phaseId);
        List<MigrationData> data = getPhaseData(phaseId, options.getDateFrom(), options.getDateTo());
        Forecast forecast = getForecast(phaseId);

        String format = options.getFormat() != null ? options.getFormat().toLowerCase() : "json";

        switch (format) {
            case "csv":
                return exportAsCsv(phase, progress, data, forecast, options);
            case "json":
            default:
                return exportAsJson(phase, progress, data, forecast, options);
        }
    }

    private byte[] exportAsJson(MigrationPhase phase, PhaseProgress progress, List<MigrationData> data, Forecast forecast, ExportOptions options) throws IOException {
        Map<String, Object> export = new HashMap<>();
        export.put("phase", Map.of(
            "id", phase.getId(),
            "name", phase.getName(),
            "type", phase.getType(),
            "source", phase.getSource(),
            "target", phase.getTarget(),
            "createdAt", phase.getCreatedAt().toString(),
            "lastUpdated", phase.getLastUpdated().toString()
        ));
        export.put("progress", Map.of(
            "phaseId", progress.getPhaseId(),
            "phaseName", progress.getPhaseName(),
            "progress", progress.getProgress(),
            "sourceObjects", progress.getSourceObjects(),
            "targetObjects", progress.getTargetObjects(),
            "sourceSize", progress.getSourceSize(),
            "targetSize", progress.getTargetSize()
        ));

        if (options.getIncludeForecast() != null && options.getIncludeForecast()) {
            export.put("forecast", Map.of(
                "averageRate", forecast.getAverageRate(),
                "remainingObjects", forecast.getRemainingObjects(),
                "remainingSize", forecast.getRemainingSize(),
                "eta", forecast.getEta().toString(),
                "confidence", forecast.getConfidence()
            ));
        }

        if (options.getIncludeRawData() != null && options.getIncludeRawData()) {
            export.put("data", data);
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        return mapper.writeValueAsBytes(export);
    }

    private byte[] exportAsCsv(MigrationPhase phase, PhaseProgress progress, List<MigrationData> data, Forecast forecast, ExportOptions options) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8));

        // Write header
        writer.println("Phase Export: " + phase.getName());
        writer.println("Phase ID: " + phase.getId());
        writer.println("Source: " + phase.getSource());
        writer.println("Target: " + phase.getTarget());
        writer.println("Progress: " + progress.getProgress() + "%");
        writer.println("Source Objects: " + progress.getSourceObjects());
        writer.println("Target Objects: " + progress.getTargetObjects());
        writer.println("Source Size: " + progress.getSourceSize());
        writer.println("Target Size: " + progress.getTargetSize());
        writer.println();

        if (options.getIncludeForecast() != null && options.getIncludeForecast()) {
            writer.println("Forecast:");
            writer.println("Average Rate: " + forecast.getAverageRate() + " objects/day");
            writer.println("Remaining Objects: " + forecast.getRemainingObjects());
            writer.println("Remaining Size: " + forecast.getRemainingSize());
            writer.println("ETA: " + forecast.getEta());
            writer.println("Confidence: " + forecast.getConfidence() + "%");
            writer.println();
        }

        if (options.getIncludeRawData() != null && options.getIncludeRawData() && !data.isEmpty()) {
            writer.println("Data Points:");
            writer.println("Timestamp,Source Objects,Target Objects,Source Size,Target Size,Type");
            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
            for (MigrationData point : data) {
                writer.printf("%s,%d,%d,%d,%d,%s%n",
                    point.getTimestamp().format(formatter),
                    point.getSourceObjects(),
                    point.getTargetObjects(),
                    point.getSourceSize(),
                    point.getTargetSize(),
                    point.getType()
                );
            }
        }

        writer.flush();
        return baos.toByteArray();
    }
}
