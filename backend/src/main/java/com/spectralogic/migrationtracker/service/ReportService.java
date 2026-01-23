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
import com.spectralogic.migrationtracker.repository.BucketDataRepository;
import com.spectralogic.migrationtracker.repository.MigrationDataRepository;
import com.spectralogic.migrationtracker.repository.PhaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final BucketDataRepository bucketDataRepository;



    public ReportService(PhaseRepository phaseRepository, MigrationDataRepository dataRepository,
                         ProjectService projectService, CustomerService customerService,
                         PostgreSQLConfig postgresConfig, BucketDataRepository bucketDataRepository) {
        this.phaseRepository = phaseRepository;
        this.dataRepository = dataRepository;
        this.projectService = projectService;
        this.customerService = customerService;
        this.postgresConfig = postgresConfig;
        this.bucketDataRepository = bucketDataRepository;
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
        
        // Get buckets for this phase (from BucketData records) to filter queries
        // Only include actual bucket names, not storage domain names (which are used for aggregate data)
        List<com.spectralogic.migrationtracker.model.BucketData> allBucketData = bucketDataRepository.findByPhaseId(phaseId);
        logger.debug("Found {} BucketData records for phase '{}'", allBucketData.size(), phaseId);
        
        List<String> phaseBuckets = allBucketData.stream()
            .map(bd -> bd.getBucketName())
            .filter(bucketName -> {
                boolean isNotSource = !bucketName.equals(phase.getSource());
                boolean isNotTarget = !bucketName.equals(phase.getTarget());
                if (!isNotSource || !isNotTarget) {
                    logger.debug("Filtering out bucket name '{}' (matches source '{}' or target '{}')", 
                        bucketName, phase.getSource(), phase.getTarget());
                }
                return isNotSource && isNotTarget;
            })
            .distinct()
            .collect(java.util.stream.Collectors.toList());
        
        // If no buckets found in BucketData (or only storage domain names), query all buckets (backward compatibility)
        // Otherwise, filter by the buckets that were selected during data gathering
        List<String> bucketsToQuery = phaseBuckets.isEmpty() ? null : phaseBuckets;
        
        logger.info("Phase '{}' has {} buckets to filter (from {} BucketData records): {}", 
            phaseId, bucketsToQuery != null ? bucketsToQuery.size() : 0, allBucketData.size(), bucketsToQuery);
        
        // Read from SQLite - use bucket_data table to filter by selected buckets
        // This ensures we only count objects from the buckets that were selected during data gathering
        
        // Determine source and target database types
        String sourceDbType = determineDatabaseType(phase.getSource());
        String targetDbType = determineDatabaseType(phase.getTarget());
        
        // Get latest bucket_data records for each bucket (group by bucket_name, max timestamp)
        // Filter by selected buckets if available
        // Note: allBucketData was already retrieved above for bucket filtering
        
        // Get latest timestamp for this phase
        Optional<MigrationData> latest = dataRepository.findLatestByPhaseId(phaseId);
        LocalDate latestTimestamp = latest.isPresent() ? latest.get().getTimestamp() : null;
        
        // Get reference/baseline timestamp
        Optional<MigrationData> reference = dataRepository.findReferenceByPhaseId(phaseId);
        List<MigrationData> allData = dataRepository.findByPhaseId(phaseId);
        if (reference.isEmpty() && !allData.isEmpty()) {
            MigrationData firstData = allData.get(allData.size() - 1);
            reference = Optional.of(firstData);
            logger.info("No REFERENCE point found for phase '{}', using first DATA point (timestamp: {}) as baseline", 
                phaseId, firstData.getTimestamp());
        }
        LocalDate baselineTimestamp = reference.isPresent() ? reference.get().getTimestamp() : null;
        
        // Filter bucket_data by selected buckets (exclude storage domain names)
        // Sum source buckets (where source matches sourceDbType)
        // Sum target buckets (where source matches targetDbType)
        long sourceObjects = 0L;
        long sourceSize = 0L;
        long targetObjects = 0L;
        long targetSize = 0L;
        long baselineSourceObjects = 0L;
        long baselineSourceSize = 0L;
        long baselineTargetObjects = 0L;
        long baselineTargetSize = 0L;
        
        if (latestTimestamp != null) {
            // Get latest bucket data (for current state)
            Map<String, com.spectralogic.migrationtracker.model.BucketData> latestBucketData = new java.util.HashMap<>();
            for (com.spectralogic.migrationtracker.model.BucketData bd : allBucketData) {
                if (bd.getTimestamp().equals(latestTimestamp)) {
                    // Include storage_domain in key to distinguish source vs target records for same bucket
                    String storageDomainKey = bd.getStorageDomain() != null ? bd.getStorageDomain() : bd.getSource();
                    String key = bd.getBucketName() + "|" + storageDomainKey;
                    // Keep only the latest record for each bucket+storage_domain combination
                    if (!latestBucketData.containsKey(key) || 
                        bd.getLastUpdated().isAfter(latestBucketData.get(key).getLastUpdated())) {
                        latestBucketData.put(key, bd);
                    }
                }
            }
            
            // Sum source buckets (filter by selected buckets and storage domain)
            for (com.spectralogic.migrationtracker.model.BucketData bd : latestBucketData.values()) {
                // Check if this bucket is in the selected buckets list
                boolean isSelectedBucket = bucketsToQuery == null || bucketsToQuery.contains(bd.getBucketName());
                
                // Check if this is a source bucket (matches source storage domain)
                // If storage_domain is null (old data), fall back to database type matching
                boolean isSourceBucket = false;
                if (bd.getStorageDomain() != null) {
                    isSourceBucket = bd.getStorageDomain().equals(phase.getSource()) && 
                        !bd.getBucketName().equals(phase.getSource()) && 
                        !bd.getBucketName().equals(phase.getTarget());
                    logger.debug("Bucket '{}' (storage_domain='{}', source='{}'): isSourceBucket={} (matches phase source '{}')", 
                        bd.getBucketName(), bd.getStorageDomain(), bd.getSource(), isSourceBucket, phase.getSource());
                } else {
                    // Fallback for old data: match by database type
                    isSourceBucket = bd.getSource().equalsIgnoreCase(sourceDbType) && 
                        !bd.getBucketName().equals(phase.getSource()) && 
                        !bd.getBucketName().equals(phase.getTarget());
                    logger.debug("Bucket '{}' (storage_domain=null, source='{}'): isSourceBucket={} (matches sourceDbType '{}')", 
                        bd.getBucketName(), bd.getSource(), isSourceBucket, sourceDbType);
                }
                
                if (isSelectedBucket && isSourceBucket) {
                    long count = bd.getObjectCount() != null ? bd.getObjectCount() : 0L;
                    long size = bd.getSizeBytes() != null ? bd.getSizeBytes() : 0L;
                    sourceObjects += count;
                    sourceSize += size;
                    logger.debug("Added source bucket '{}': {} objects, {} bytes", bd.getBucketName(), count, size);
                }
                
                // Check if this is a target bucket (matches target storage domain)
                boolean isTargetBucket = false;
                if (bd.getStorageDomain() != null) {
                    isTargetBucket = bd.getStorageDomain().equals(phase.getTarget()) && 
                        !bd.getBucketName().equals(phase.getSource()) && 
                        !bd.getBucketName().equals(phase.getTarget());
                    logger.debug("Bucket '{}' (storage_domain='{}', source='{}'): isTargetBucket={} (matches phase target '{}')", 
                        bd.getBucketName(), bd.getStorageDomain(), bd.getSource(), isTargetBucket, phase.getTarget());
                } else {
                    // Fallback for old data: match by database type
                    isTargetBucket = bd.getSource().equalsIgnoreCase(targetDbType) && 
                        !bd.getBucketName().equals(phase.getSource()) && 
                        !bd.getBucketName().equals(phase.getTarget());
                    logger.debug("Bucket '{}' (storage_domain=null, source='{}'): isTargetBucket={} (matches targetDbType '{}')", 
                        bd.getBucketName(), bd.getSource(), isTargetBucket, targetDbType);
                }
                
                if (isSelectedBucket && isTargetBucket) {
                    long count = bd.getObjectCount() != null ? bd.getObjectCount() : 0L;
                    long size = bd.getSizeBytes() != null ? bd.getSizeBytes() : 0L;
                    targetObjects += count;
                    targetSize += size;
                    logger.debug("Added target bucket '{}': {} objects, {} bytes", bd.getBucketName(), count, size);
                }
                
                if (!isSelectedBucket) {
                    logger.debug("Bucket '{}' filtered out (not in selected buckets: {})", bd.getBucketName(), bucketsToQuery);
                }
            }
            
            logger.info("Latest bucket data (timestamp: {}): source={} objects ({} bytes), target={} objects ({} bytes), bucketsToQuery={}", 
                latestTimestamp, sourceObjects, sourceSize, targetObjects, targetSize, bucketsToQuery);
            
            // Log all source buckets being counted for this phase
            logger.info("Source buckets counted for phase '{}' (storage domain '{}'):", phaseId, phase.getSource());
            for (com.spectralogic.migrationtracker.model.BucketData bd : latestBucketData.values()) {
                boolean isSelectedBucket = bucketsToQuery == null || bucketsToQuery.contains(bd.getBucketName());
                boolean isSourceBucket = false;
                if (bd.getStorageDomain() != null) {
                    isSourceBucket = bd.getStorageDomain().equals(phase.getSource()) && 
                        !bd.getBucketName().equals(phase.getSource()) && 
                        !bd.getBucketName().equals(phase.getTarget());
                } else {
                    isSourceBucket = bd.getSource().equalsIgnoreCase(sourceDbType) && 
                        !bd.getBucketName().equals(phase.getSource()) && 
                        !bd.getBucketName().equals(phase.getTarget());
                }
                if (isSelectedBucket && isSourceBucket) {
                    logger.info("  - Source bucket: '{}' in storage domain '{}': {} objects, {} bytes", 
                        bd.getBucketName(), bd.getStorageDomain(), bd.getObjectCount(), bd.getSizeBytes());
                }
            }
            
            // Compare with migration_data for validation
            if (latest.isPresent()) {
                MigrationData last = latest.get();
                long migrationSourceObjects = last.getSourceObjects() != null ? last.getSourceObjects() : 0L;
                long migrationTargetObjects = last.getTargetObjects() != null ? last.getTargetObjects() : 0L;
                logger.info("Comparison - bucket_data: source={}, target={} | migration_data: source={}, target={}", 
                    sourceObjects, targetObjects, migrationSourceObjects, migrationTargetObjects);
                if (sourceObjects != migrationSourceObjects || targetObjects != migrationTargetObjects) {
                    logger.warn("Mismatch detected! bucket_data and migration_data totals differ. " +
                        "This may indicate: 1) Bucket filtering excluded some data, 2) Data gathering inconsistency, " +
                        "3) Storage domain mismatch in bucket_data records.");
                }
            }
        }
        
        if (baselineTimestamp != null && !baselineTimestamp.equals(latestTimestamp)) {
            // Get baseline bucket data
            Map<String, com.spectralogic.migrationtracker.model.BucketData> baselineBucketData = new java.util.HashMap<>();
            for (com.spectralogic.migrationtracker.model.BucketData bd : allBucketData) {
                if (bd.getTimestamp().equals(baselineTimestamp)) {
                    // Include storage_domain in key to distinguish source vs target records for same bucket
                    String storageDomainKey = bd.getStorageDomain() != null ? bd.getStorageDomain() : bd.getSource();
                    String key = bd.getBucketName() + "|" + storageDomainKey;
                    if (!baselineBucketData.containsKey(key) || 
                        bd.getLastUpdated().isAfter(baselineBucketData.get(key).getLastUpdated())) {
                        baselineBucketData.put(key, bd);
                    }
                }
            }
            
            // Sum baseline source and target buckets
            for (com.spectralogic.migrationtracker.model.BucketData bd : baselineBucketData.values()) {
                boolean isSelectedBucket = bucketsToQuery == null || bucketsToQuery.contains(bd.getBucketName());
                
                // Check if this is a source bucket (matches source storage domain)
                boolean isSourceBucket = false;
                if (bd.getStorageDomain() != null) {
                    isSourceBucket = bd.getStorageDomain().equals(phase.getSource()) && 
                        !bd.getBucketName().equals(phase.getSource()) && 
                        !bd.getBucketName().equals(phase.getTarget());
                } else {
                    // Fallback for old data: match by database type
                    isSourceBucket = bd.getSource().equalsIgnoreCase(sourceDbType) && 
                        !bd.getBucketName().equals(phase.getSource()) && 
                        !bd.getBucketName().equals(phase.getTarget());
                }
                
                // Check if this is a target bucket (matches target storage domain)
                boolean isTargetBucket = false;
                if (bd.getStorageDomain() != null) {
                    isTargetBucket = bd.getStorageDomain().equals(phase.getTarget()) && 
                        !bd.getBucketName().equals(phase.getSource()) && 
                        !bd.getBucketName().equals(phase.getTarget());
                } else {
                    // Fallback for old data: match by database type
                    isTargetBucket = bd.getSource().equalsIgnoreCase(targetDbType) && 
                        !bd.getBucketName().equals(phase.getSource()) && 
                        !bd.getBucketName().equals(phase.getTarget());
                }
                
                if (isSelectedBucket && isSourceBucket) {
                    baselineSourceObjects += bd.getObjectCount() != null ? bd.getObjectCount() : 0L;
                    baselineSourceSize += bd.getSizeBytes() != null ? bd.getSizeBytes() : 0L;
                }
                if (isSelectedBucket && isTargetBucket) {
                    baselineTargetObjects += bd.getObjectCount() != null ? bd.getObjectCount() : 0L;
                    baselineTargetSize += bd.getSizeBytes() != null ? bd.getSizeBytes() : 0L;
                }
            }
            
            logger.info("Baseline bucket data (timestamp: {}): source={} objects ({} bytes), target={} objects ({} bytes)", 
                baselineTimestamp, baselineSourceObjects, baselineSourceSize, baselineTargetObjects, baselineTargetSize);
        } else if (baselineTimestamp != null) {
            // Same timestamp - use current values as baseline
            baselineSourceObjects = sourceObjects;
            baselineSourceSize = sourceSize;
            baselineTargetObjects = targetObjects;
            baselineTargetSize = targetSize;
            logger.info("Baseline and latest are same timestamp, using current values as baseline");
        }
        
        // Fallback: if bucket_data calculation resulted in 0, use migration_data (backward compatibility)
        // This can happen if:
        // 1. No bucket_data exists
        // 2. bucket_data exists but doesn't match filters (storage_domain mismatch, etc.)
        // 3. Selected buckets filter excluded all data
        if ((sourceObjects == 0 && targetObjects == 0) && latest.isPresent()) {
            logger.warn("Bucket data calculation resulted in 0 objects for phase '{}'. " +
                "This might indicate: 1) No bucket_data exists, 2) Storage domain mismatch, 3) Bucket filter issue. " +
                "Falling back to migration_data aggregate.", phaseId);
            MigrationData last = latest.get();
            long migrationSourceObjects = last.getSourceObjects() != null ? last.getSourceObjects() : 0L;
            long migrationSourceSize = last.getSourceSize() != null ? last.getSourceSize() : 0L;
            long migrationTargetObjects = last.getTargetObjects() != null ? last.getTargetObjects() : 0L;
            long migrationTargetSize = last.getTargetSize() != null ? last.getTargetSize() : 0L;
            
            // Only use migration_data if it has actual values
            if (migrationSourceObjects > 0 || migrationTargetObjects > 0) {
                logger.info("Using migration_data fallback: source={} objects, target={} objects", 
                    migrationSourceObjects, migrationTargetObjects);
                sourceObjects = migrationSourceObjects;
                sourceSize = migrationSourceSize;
                targetObjects = migrationTargetObjects;
                targetSize = migrationTargetSize;
                
                if (reference.isPresent()) {
                    MigrationData ref = reference.get();
                    baselineSourceObjects = ref.getSourceObjects() != null ? ref.getSourceObjects() : 0L;
                    baselineSourceSize = ref.getSourceSize() != null ? ref.getSourceSize() : 0L;
                    baselineTargetObjects = ref.getTargetObjects() != null ? ref.getTargetObjects() : 0L;
                    baselineTargetSize = ref.getTargetSize() != null ? ref.getTargetSize() : 0L;
                }
            } else {
                logger.warn("Migration_data also has 0 objects. No data available for phase '{}'.", phaseId);
            }
        } else if (sourceObjects > 0 && targetObjects == 0 && latest.isPresent()) {
            // Source has data but target is 0 - check if migration_data has target data
            MigrationData last = latest.get();
            long migrationTargetObjects = last.getTargetObjects() != null ? last.getTargetObjects() : 0L;
            if (migrationTargetObjects > 0) {
                // Log diagnostic information about bucket_data records
                logger.warn("Bucket data shows target=0 but migration_data shows target={}. " +
                    "This might indicate a storage_domain mismatch. Phase target: '{}'. Using migration_data for target.", 
                    migrationTargetObjects, phase.getTarget());
                
                // Log all bucket_data records for this phase to help diagnose
                logger.debug("All bucket_data records for phase '{}' (latest timestamp: {}):", phaseId, latestTimestamp);
                for (com.spectralogic.migrationtracker.model.BucketData bd : allBucketData) {
                    if (bd.getTimestamp().equals(latestTimestamp)) {
                        logger.debug("  - Bucket: '{}', Storage Domain: '{}', Source: '{}', Objects: {}, Size: {}",
                            bd.getBucketName(), bd.getStorageDomain(), bd.getSource(), 
                            bd.getObjectCount(), bd.getSizeBytes());
                    }
                }
                
                targetObjects = migrationTargetObjects;
                targetSize = last.getTargetSize() != null ? last.getTargetSize() : 0L;
            }
        }
        
        // Get tape counts from latest migration_data
        long sourceTapeCount = 0L;
        long targetTapeCount = 0L;
        if (latest.isPresent()) {
            MigrationData last = latest.get();
            sourceTapeCount = last.getSourceTapeCount() != null ? last.getSourceTapeCount() : 0L;
            targetTapeCount = last.getTargetTapeCount() != null ? last.getTargetTapeCount() : 0L;
        }
        
        // Use baseline source for display (what we're migrating from)
        // Use current target for display (what we've migrated to)
        long displaySourceObjects = baselineSourceObjects > 0 ? baselineSourceObjects : sourceObjects;
        long displaySourceSize = baselineSourceSize > 0 ? baselineSourceSize : sourceSize;
        progress.setSourceObjects(displaySourceObjects);
        progress.setSourceSize(displaySourceSize);
        progress.setSourceTapeCount(sourceTapeCount);
        progress.setTargetObjects(targetObjects);
        progress.setTargetSize(targetSize);
        progress.setTargetTapeCount(targetTapeCount);
        
        logger.info("Final progress values for phase '{}': sourceObjects={} ({} bytes), targetObjects={} ({} bytes), sourceTapes={}, targetTapes={}", 
            phaseId, displaySourceObjects, displaySourceSize, targetObjects, targetSize, sourceTapeCount, targetTapeCount);
        
        // Calculate progress using delta method:
        // Progress = (target_delta) / (objects_to_migrate) * 100
        // Where:
        //   - target_delta = current_target - baseline_target (objects added since migration started)
        //   - objects_to_migrate = baseline_source - baseline_target (objects that need to be migrated)
        // 
        // This accounts for pre-existing objects in the target storage domain.
        // If baseline_target = 0, this simplifies to: target / source * 100
        int progressPercent = 0;
        
        long targetDelta = targetObjects - baselineTargetObjects;
        long objectsToMigrate = baselineSourceObjects - baselineTargetObjects;
        
        if (objectsToMigrate > 0) {
            progressPercent = (int) ((targetDelta * 100) / objectsToMigrate);
        } else if (baselineSourceObjects > 0 && targetObjects >= baselineSourceObjects) {
            // Fallback: if objectsToMigrate <= 0, assume migration is complete
            progressPercent = 100;
        } else if (baselineSourceObjects == 0 && sourceObjects > 0) {
            // No baseline, use current source
            progressPercent = sourceObjects > 0 
                ? (int) ((targetObjects * 100) / sourceObjects)
                : 0;
        }
        
        logger.info("Progress calculation: baseline_source={}, baseline_target={}, current_source={}, current_target={}", 
            baselineSourceObjects, baselineTargetObjects, sourceObjects, targetObjects);
        logger.info("Progress calculation: target_delta={}, objects_to_migrate={}, progress={}%", 
            targetDelta, objectsToMigrate, progressPercent);
        
        // Cap at 100% - if target exceeds source, it might mean:
        // 1. Migration is complete and target has more objects (duplicates, etc.)
        // 2. Target storage domain had pre-existing objects
        // 3. Data gathering issue
        progressPercent = Math.max(0, Math.min(100, progressPercent));
        
        progress.setProgress(progressPercent);
        
        logger.info("Phase progress calculated: {}% (baseline source: {} objects, current target: {} objects)", 
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
            case "html":
                return exportAsHtml(phase, progress, data, forecast, options);
            case "pdf":
                return exportAsPdf(phase, progress, data, forecast, options);
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

    private byte[] exportAsHtml(MigrationPhase phase, PhaseProgress progress, List<MigrationData> data, Forecast forecast, ExportOptions options) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>Phase Report: ").append(escapeHtml(phase.getName())).append("</title>\n");
        html.append("  <style>\n");
        html.append("    body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }\n");
        html.append("    .container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }\n");
        html.append("    h1 { color: #333; border-bottom: 3px solid #4CAF50; padding-bottom: 10px; }\n");
        html.append("    h2 { color: #555; margin-top: 30px; }\n");
        html.append("    .info-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 20px; margin: 20px 0; }\n");
        html.append("    .info-card { background: #f9f9f9; padding: 15px; border-radius: 5px; border-left: 4px solid #4CAF50; }\n");
        html.append("    .info-label { font-weight: bold; color: #666; font-size: 0.9em; }\n");
        html.append("    .info-value { font-size: 1.1em; color: #333; margin-top: 5px; }\n");
        html.append("    .progress-bar { width: 100%; height: 30px; background-color: #e0e0e0; border-radius: 15px; overflow: hidden; margin: 10px 0; }\n");
        html.append("    .progress-fill { height: 100%; background-color: #4CAF50; display: flex; align-items: center; justify-content: center; color: white; font-weight: bold; }\n");
        html.append("    table { width: 100%; border-collapse: collapse; margin: 20px 0; }\n");
        html.append("    th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }\n");
        html.append("    th { background-color: #4CAF50; color: white; font-weight: bold; }\n");
        html.append("    tr:hover { background-color: #f5f5f5; }\n");
        html.append("    .number { text-align: right; }\n");
        html.append("    @media print { body { background: white; } .container { box-shadow: none; } }\n");
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("  <div class=\"container\">\n");
        html.append("    <h1>Phase Report: ").append(escapeHtml(phase.getName())).append("</h1>\n");
        
        // Phase Information
        html.append("    <h2>Phase Information</h2>\n");
        html.append("    <div class=\"info-grid\">\n");
        html.append("      <div class=\"info-card\">\n");
        html.append("        <div class=\"info-label\">Phase ID</div>\n");
        html.append("        <div class=\"info-value\">").append(escapeHtml(phase.getId())).append("</div>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"info-card\">\n");
        html.append("        <div class=\"info-label\">Type</div>\n");
        html.append("        <div class=\"info-value\">").append(escapeHtml(phase.getType())).append("</div>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"info-card\">\n");
        html.append("        <div class=\"info-label\">Source</div>\n");
        html.append("        <div class=\"info-value\">").append(escapeHtml(phase.getSource())).append("</div>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"info-card\">\n");
        html.append("        <div class=\"info-label\">Target</div>\n");
        html.append("        <div class=\"info-value\">").append(escapeHtml(phase.getTarget())).append("</div>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"info-card\">\n");
        html.append("        <div class=\"info-label\">Created</div>\n");
        html.append("        <div class=\"info-value\">").append(phase.getCreatedAt().toString()).append("</div>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"info-card\">\n");
        html.append("        <div class=\"info-label\">Last Updated</div>\n");
        html.append("        <div class=\"info-value\">").append(phase.getLastUpdated().toString()).append("</div>\n");
        html.append("      </div>\n");
        html.append("    </div>\n");
        
        // Progress
        html.append("    <h2>Progress</h2>\n");
        html.append("    <div class=\"info-grid\">\n");
        html.append("      <div class=\"info-card\">\n");
        html.append("        <div class=\"info-label\">Overall Progress</div>\n");
        html.append("        <div class=\"progress-bar\">\n");
        html.append("          <div class=\"progress-fill\" style=\"width: ").append(progress.getProgress()).append("%\">").append(progress.getProgress()).append("%</div>\n");
        html.append("        </div>\n");
        html.append("      </div>\n");
        html.append("    </div>\n");
        html.append("    <div class=\"info-grid\">\n");
        html.append("      <div class=\"info-card\">\n");
        html.append("        <div class=\"info-label\">Source Objects</div>\n");
        html.append("        <div class=\"info-value\">").append(String.format("%,d", progress.getSourceObjects())).append("</div>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"info-card\">\n");
        html.append("        <div class=\"info-label\">Target Objects</div>\n");
        html.append("        <div class=\"info-value\">").append(String.format("%,d", progress.getTargetObjects())).append("</div>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"info-card\">\n");
        html.append("        <div class=\"info-label\">Source Size</div>\n");
        html.append("        <div class=\"info-value\">").append(formatBytes(progress.getSourceSize())).append("</div>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"info-card\">\n");
        html.append("        <div class=\"info-label\">Target Size</div>\n");
        html.append("        <div class=\"info-value\">").append(formatBytes(progress.getTargetSize())).append("</div>\n");
        html.append("      </div>\n");
        html.append("    </div>\n");
        
        // Forecast
        if (options.getIncludeForecast() != null && options.getIncludeForecast()) {
            html.append("    <h2>Forecast</h2>\n");
            html.append("    <div class=\"info-grid\">\n");
            html.append("      <div class=\"info-card\">\n");
            html.append("        <div class=\"info-label\">Average Rate</div>\n");
            html.append("        <div class=\"info-value\">").append(String.format("%,d", forecast.getAverageRate())).append(" objects/day</div>\n");
            html.append("      </div>\n");
            html.append("      <div class=\"info-card\">\n");
            html.append("        <div class=\"info-label\">Remaining Objects</div>\n");
            html.append("        <div class=\"info-value\">").append(String.format("%,d", forecast.getRemainingObjects())).append("</div>\n");
            html.append("      </div>\n");
            html.append("      <div class=\"info-card\">\n");
            html.append("        <div class=\"info-label\">Remaining Size</div>\n");
            html.append("        <div class=\"info-value\">").append(formatBytes(forecast.getRemainingSize())).append("</div>\n");
            html.append("      </div>\n");
            html.append("      <div class=\"info-card\">\n");
            html.append("        <div class=\"info-label\">ETA</div>\n");
            html.append("        <div class=\"info-value\">").append(forecast.getEta().toString()).append("</div>\n");
            html.append("      </div>\n");
            html.append("      <div class=\"info-card\">\n");
            html.append("        <div class=\"info-label\">Confidence</div>\n");
            html.append("        <div class=\"info-value\">").append(forecast.getConfidence()).append("%</div>\n");
            html.append("      </div>\n");
            html.append("    </div>\n");
        }
        
        // Data Points
        if (options.getIncludeRawData() != null && options.getIncludeRawData() && !data.isEmpty()) {
            html.append("    <h2>Data Points</h2>\n");
            html.append("    <table>\n");
            html.append("      <thead>\n");
            html.append("        <tr>\n");
            html.append("          <th>Timestamp</th>\n");
            html.append("          <th class=\"number\">Source Objects</th>\n");
            html.append("          <th class=\"number\">Target Objects</th>\n");
            html.append("          <th class=\"number\">Source Size</th>\n");
            html.append("          <th class=\"number\">Target Size</th>\n");
            html.append("          <th>Type</th>\n");
            html.append("        </tr>\n");
            html.append("      </thead>\n");
            html.append("      <tbody>\n");
            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
            for (MigrationData point : data) {
                html.append("        <tr>\n");
                html.append("          <td>").append(point.getTimestamp().format(formatter)).append("</td>\n");
                html.append("          <td class=\"number\">").append(String.format("%,d", point.getSourceObjects())).append("</td>\n");
                html.append("          <td class=\"number\">").append(String.format("%,d", point.getTargetObjects())).append("</td>\n");
                html.append("          <td class=\"number\">").append(formatBytes(point.getSourceSize())).append("</td>\n");
                html.append("          <td class=\"number\">").append(formatBytes(point.getTargetSize())).append("</td>\n");
                html.append("          <td>").append(escapeHtml(point.getType())).append("</td>\n");
                html.append("        </tr>\n");
            }
            html.append("      </tbody>\n");
            html.append("    </table>\n");
        }
        
        html.append("  </div>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        
        return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] exportAsPdf(MigrationPhase phase, PhaseProgress progress, List<MigrationData> data, Forecast forecast, ExportOptions options) throws IOException {
        org.apache.pdfbox.pdmodel.PDDocument document = null;
        org.apache.pdfbox.pdmodel.PDPageContentStream contentStream = null;
        
        try {
            document = new org.apache.pdfbox.pdmodel.PDDocument();
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            document.addPage(page);
            
            contentStream = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page);
            
            // In PDFBox 3.0, use PDType1Font constructor with Standard14Fonts.FontName enum
            org.apache.pdfbox.pdmodel.font.PDType1Font boldFont = new org.apache.pdfbox.pdmodel.font.PDType1Font(
                org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD
            );
            org.apache.pdfbox.pdmodel.font.PDType1Font regularFont = new org.apache.pdfbox.pdmodel.font.PDType1Font(
                org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA
            );
            
            float y = 750;
            float leftMargin = 50;
            float rightMargin = 550;
            
            // Header
            contentStream.setFont(boldFont, 18);
            contentStream.beginText();
            contentStream.newLineAtOffset(leftMargin, y);
            contentStream.showText("Phase Report: " + phase.getName());
            contentStream.endText();
            
            y -= 30;
            contentStream.setFont(regularFont, 10);
            contentStream.beginText();
            contentStream.newLineAtOffset(leftMargin, y);
            contentStream.showText("Phase ID: " + phase.getId());
            contentStream.endText();
            
            y -= 15;
            contentStream.beginText();
            contentStream.newLineAtOffset(leftMargin, y);
            contentStream.showText("Source: " + phase.getSource() + " -> Target: " + phase.getTarget());
            contentStream.endText();
            
            y -= 30;
            
            // Progress Section with Visual Bar
            contentStream.setFont(boldFont, 14);
            contentStream.beginText();
            contentStream.newLineAtOffset(leftMargin, y);
            contentStream.showText("Progress");
            contentStream.endText();
            
            y -= 20;
            contentStream.setFont(regularFont, 12);
            contentStream.beginText();
            contentStream.newLineAtOffset(leftMargin, y);
            contentStream.showText("Overall Progress: " + progress.getProgress() + "%");
            contentStream.endText();
            
            // Draw progress bar
            y -= 15;
            float progressBarWidth = 400;
            float progressBarHeight = 20;
            float progressBarX = leftMargin;
            float progressBarY = y - progressBarHeight;
            
            // Background bar (gray)
            contentStream.setNonStrokingColor(0.9f, 0.9f, 0.9f);
            contentStream.addRect(progressBarX, progressBarY, progressBarWidth, progressBarHeight);
            contentStream.fill();
            
            // Progress bar (green)
            float progressWidth = (progressBarWidth * progress.getProgress()) / 100f;
            contentStream.setNonStrokingColor(0.2f, 0.7f, 0.3f);
            contentStream.addRect(progressBarX, progressBarY, progressWidth, progressBarHeight);
            contentStream.fill();
            
            // Border
            contentStream.setStrokingColor(0.5f, 0.5f, 0.5f);
            contentStream.setLineWidth(1);
            contentStream.addRect(progressBarX, progressBarY, progressBarWidth, progressBarHeight);
            contentStream.stroke();
            
            y -= 40;
            
            // Statistics Section
            contentStream.setFont(boldFont, 14);
            contentStream.beginText();
            contentStream.newLineAtOffset(leftMargin, y);
            contentStream.showText("Statistics");
            contentStream.endText();
            
            y -= 25;
            contentStream.setFont(regularFont, 11);
            float statY = y;
            
            // Left column
            contentStream.beginText();
            contentStream.newLineAtOffset(leftMargin, statY);
            contentStream.showText("Source Objects: " + String.format("%,d", progress.getSourceObjects()));
            contentStream.endText();
            
            statY -= 18;
            contentStream.beginText();
            contentStream.newLineAtOffset(leftMargin, statY);
            contentStream.showText("Source Size: " + formatBytes(progress.getSourceSize()));
            contentStream.endText();
            
            // Right column
            statY = y;
            contentStream.beginText();
            contentStream.newLineAtOffset(leftMargin + 250, statY);
            contentStream.showText("Target Objects: " + String.format("%,d", progress.getTargetObjects()));
            contentStream.endText();
            
            statY -= 18;
            contentStream.beginText();
            contentStream.newLineAtOffset(leftMargin + 250, statY);
            contentStream.showText("Target Size: " + formatBytes(progress.getTargetSize()));
            contentStream.endText();
            
            y = statY - 30;
            
            // Charts Section (if enabled and data available)
            boolean includeCharts = options.getIncludeCharts() != null && options.getIncludeCharts();
            if (includeCharts && data != null && !data.isEmpty()) {
                y = drawProgressChart(contentStream, data, leftMargin, y - 20, rightMargin - leftMargin, 200, boldFont, regularFont);
            }
            
            // Forecast Section
            if (options.getIncludeForecast() != null && options.getIncludeForecast()) {
                y -= 20;
                contentStream.setFont(boldFont, 14);
                contentStream.beginText();
                contentStream.newLineAtOffset(leftMargin, y);
                contentStream.showText("Forecast");
                contentStream.endText();
                
                y -= 25;
                contentStream.setFont(regularFont, 11);
                float forecastY = y;
                
                contentStream.beginText();
                contentStream.newLineAtOffset(leftMargin, forecastY);
                contentStream.showText("Average Rate: " + String.format("%,d", forecast.getAverageRate()) + " objects/day");
                contentStream.endText();
                
                forecastY -= 18;
                contentStream.beginText();
                contentStream.newLineAtOffset(leftMargin, forecastY);
                contentStream.showText("Remaining Objects: " + String.format("%,d", forecast.getRemainingObjects()));
                contentStream.endText();
                
                forecastY -= 18;
                contentStream.beginText();
                contentStream.newLineAtOffset(leftMargin, forecastY);
                contentStream.showText("Remaining Size: " + formatBytes(forecast.getRemainingSize()));
                contentStream.endText();
                
                forecastY -= 18;
                contentStream.beginText();
                contentStream.newLineAtOffset(leftMargin, forecastY);
                contentStream.showText("ETA: " + forecast.getEta().toString());
                contentStream.endText();
                
                forecastY -= 18;
                contentStream.beginText();
                contentStream.newLineAtOffset(leftMargin, forecastY);
                contentStream.showText("Confidence: " + forecast.getConfidence() + "%");
                contentStream.endText();
            }
            
            // Data Points Table (if enabled)
            if (options.getIncludeRawData() != null && options.getIncludeRawData() && data != null && !data.isEmpty()) {
                y = drawDataTable(contentStream, data, leftMargin, y - 30, rightMargin - leftMargin, boldFont, regularFont);
            }
            
            contentStream.close();
            contentStream = null;
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            document.close();
            document = null;
            
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("Error generating PDF for phase {}: {}", phase.getId(), e.getMessage(), e);
            throw new IOException("Failed to generate PDF: " + e.getMessage(), e);
        } finally {
            if (contentStream != null) {
                try {
                    contentStream.close();
                } catch (IOException e) {
                    logger.warn("Error closing content stream: {}", e.getMessage());
                }
            }
            if (document != null) {
                try {
                    document.close();
                } catch (IOException e) {
                    logger.warn("Error closing document: {}", e.getMessage());
                }
            }
        }
    }
    
    private float drawProgressChart(org.apache.pdfbox.pdmodel.PDPageContentStream contentStream, 
                                     List<MigrationData> data, 
                                     float x, float y, 
                                     float width, float height,
                                     org.apache.pdfbox.pdmodel.font.PDType1Font boldFont,
                                     org.apache.pdfbox.pdmodel.font.PDType1Font regularFont) throws IOException {
        // Chart title
        contentStream.setFont(boldFont, 14);
        contentStream.beginText();
        contentStream.newLineAtOffset(x, y);
        contentStream.showText("Migration Progress Over Time");
        contentStream.endText();
        
        y -= 25;
        
        // Chart area
        float chartX = x;
        float chartY = y - height;
        float chartWidth = width;
        float chartHeight = height;
        
        // Draw chart border
        contentStream.setStrokingColor(0.7f, 0.7f, 0.7f);
        contentStream.setLineWidth(1);
        contentStream.addRect(chartX, chartY, chartWidth, chartHeight);
        contentStream.stroke();
        
        // Find min/max values for scaling
        long maxObjects = 0;
        for (MigrationData d : data) {
            maxObjects = Math.max(maxObjects, Math.max(d.getSourceObjects(), d.getTargetObjects()));
        }
        if (maxObjects == 0) maxObjects = 1;
        
        // Draw grid lines
        contentStream.setStrokingColor(0.9f, 0.9f, 0.9f);
        contentStream.setLineWidth(0.5f);
        int gridLines = 5;
        for (int i = 0; i <= gridLines; i++) {
            float gridY = chartY + (chartHeight * i / gridLines);
            contentStream.moveTo(chartX, gridY);
            contentStream.lineTo(chartX + chartWidth, gridY);
            contentStream.stroke();
        }
        
        // Draw Y-axis labels
        contentStream.setFont(regularFont, 8);
        contentStream.setNonStrokingColor(0.3f, 0.3f, 0.3f);
        for (int i = 0; i <= gridLines; i++) {
            long value = maxObjects - (maxObjects * i / gridLines);
            String label = String.format("%,d", value);
            contentStream.beginText();
            contentStream.newLineAtOffset(chartX - 5, chartY + (chartHeight * i / gridLines) - 3);
            contentStream.showText(label);
            contentStream.endText();
        }
        
        // Draw data points and lines
        if (data.size() > 1) {
            // Sort data by timestamp
            List<MigrationData> sortedData = new java.util.ArrayList<>(data);
            sortedData.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));
            
            float pointSpacing = chartWidth / (sortedData.size() - 1);
            
            // Draw source objects line (blue)
            contentStream.setStrokingColor(0.2f, 0.4f, 0.8f);
            contentStream.setLineWidth(2);
            for (int i = 0; i < sortedData.size() - 1; i++) {
                MigrationData d1 = sortedData.get(i);
                MigrationData d2 = sortedData.get(i + 1);
                
                float x1 = chartX + (i * pointSpacing);
                float y1 = chartY + (chartHeight * d1.getSourceObjects() / maxObjects);
                float x2 = chartX + ((i + 1) * pointSpacing);
                float y2 = chartY + (chartHeight * d2.getSourceObjects() / maxObjects);
                
                contentStream.moveTo(x1, y1);
                contentStream.lineTo(x2, y2);
                contentStream.stroke();
                
                // Draw point
                contentStream.setNonStrokingColor(0.2f, 0.4f, 0.8f);
                contentStream.addRect(x1 - 2, y1 - 2, 4, 4);
                contentStream.fill();
            }
            
            // Draw target objects line (green)
            contentStream.setStrokingColor(0.2f, 0.7f, 0.3f);
            contentStream.setLineWidth(2);
            for (int i = 0; i < sortedData.size() - 1; i++) {
                MigrationData d1 = sortedData.get(i);
                MigrationData d2 = sortedData.get(i + 1);
                
                float x1 = chartX + (i * pointSpacing);
                float y1 = chartY + (chartHeight * d1.getTargetObjects() / maxObjects);
                float x2 = chartX + ((i + 1) * pointSpacing);
                float y2 = chartY + (chartHeight * d2.getTargetObjects() / maxObjects);
                
                contentStream.moveTo(x1, y1);
                contentStream.lineTo(x2, y2);
                contentStream.stroke();
                
                // Draw point
                contentStream.setNonStrokingColor(0.2f, 0.7f, 0.3f);
                contentStream.addRect(x1 - 2, y1 - 2, 4, 4);
                contentStream.fill();
            }
            
            // Draw X-axis labels (dates)
            contentStream.setFont(regularFont, 7);
            contentStream.setNonStrokingColor(0.3f, 0.3f, 0.3f);
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MM/dd");
            for (int i = 0; i < sortedData.size(); i++) {
                if (i % Math.max(1, sortedData.size() / 5) == 0 || i == sortedData.size() - 1) {
                    MigrationData d = sortedData.get(i);
                    String dateLabel = d.getTimestamp().format(formatter);
                    float labelX = chartX + (i * pointSpacing) - 15;
                    contentStream.beginText();
                    contentStream.newLineAtOffset(labelX, chartY - 15);
                    contentStream.showText(dateLabel);
                    contentStream.endText();
                }
            }
        }
        
        // Legend
        float legendY = chartY - 30;
        contentStream.setFont(regularFont, 9);
        
        // Source legend (blue)
        contentStream.setNonStrokingColor(0.2f, 0.4f, 0.8f);
        contentStream.addRect(chartX, legendY, 10, 3);
        contentStream.fill();
        contentStream.setNonStrokingColor(0, 0, 0);
        contentStream.beginText();
        contentStream.newLineAtOffset(chartX + 15, legendY);
        contentStream.showText("Source Objects");
        contentStream.endText();
        
        // Target legend (green)
        contentStream.setNonStrokingColor(0.2f, 0.7f, 0.3f);
        contentStream.addRect(chartX + 120, legendY, 10, 3);
        contentStream.fill();
        contentStream.setNonStrokingColor(0, 0, 0);
        contentStream.beginText();
        contentStream.newLineAtOffset(chartX + 135, legendY);
        contentStream.showText("Target Objects");
        contentStream.endText();
        
        return chartY - 50;
    }
    
    private float drawDataTable(org.apache.pdfbox.pdmodel.PDPageContentStream contentStream,
                                List<MigrationData> data,
                                float x, float y,
                                float width,
                                org.apache.pdfbox.pdmodel.font.PDType1Font boldFont,
                                org.apache.pdfbox.pdmodel.font.PDType1Font regularFont) throws IOException {
        // Table title
        contentStream.setFont(boldFont, 14);
        contentStream.beginText();
        contentStream.newLineAtOffset(x, y);
        contentStream.showText("Data Points");
        contentStream.endText();
        
        y -= 25;
        
        // Sort data by timestamp (newest first)
        List<MigrationData> sortedData = new java.util.ArrayList<>(data);
        sortedData.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));
        
        // Limit to most recent 10 entries to fit on page
        int maxRows = Math.min(10, sortedData.size());
        sortedData = sortedData.subList(0, maxRows);
        
        float rowHeight = 20;
        float tableY = y - rowHeight;
        
        // Table header
        contentStream.setNonStrokingColor(0.8f, 0.8f, 0.8f);
        contentStream.addRect(x, tableY, width, rowHeight);
        contentStream.fill();
        
        contentStream.setStrokingColor(0.5f, 0.5f, 0.5f);
        contentStream.setLineWidth(1);
        contentStream.addRect(x, tableY, width, rowHeight);
        contentStream.stroke();
        
        contentStream.setFont(boldFont, 9);
        contentStream.setNonStrokingColor(0, 0, 0);
        float col1 = x + 5;
        float col2 = x + 100;
        float col3 = x + 200;
        float col4 = x + 300;
        float col5 = x + 400;
        
        contentStream.beginText();
        contentStream.newLineAtOffset(col1, tableY + 6);
        contentStream.showText("Date");
        contentStream.endText();
        
        contentStream.beginText();
        contentStream.newLineAtOffset(col2, tableY + 6);
        contentStream.showText("Source Obj");
        contentStream.endText();
        
        contentStream.beginText();
        contentStream.newLineAtOffset(col3, tableY + 6);
        contentStream.showText("Target Obj");
        contentStream.endText();
        
        contentStream.beginText();
        contentStream.newLineAtOffset(col4, tableY + 6);
        contentStream.showText("Source Size");
        contentStream.endText();
        
        contentStream.beginText();
        contentStream.newLineAtOffset(col5, tableY + 6);
        contentStream.showText("Target Size");
        contentStream.endText();
        
        // Table rows
        contentStream.setFont(regularFont, 8);
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
        
        for (int i = 0; i < sortedData.size(); i++) {
            MigrationData d = sortedData.get(i);
            float rowY = tableY - (i + 1) * rowHeight;
            
            // Alternate row color
            if (i % 2 == 0) {
                contentStream.setNonStrokingColor(0.95f, 0.95f, 0.95f);
                contentStream.addRect(x, rowY, width, rowHeight);
                contentStream.fill();
            }
            
            // Row border
            contentStream.setStrokingColor(0.7f, 0.7f, 0.7f);
            contentStream.setLineWidth(0.5f);
            contentStream.addRect(x, rowY, width, rowHeight);
            contentStream.stroke();
            
            // Row data
            contentStream.setNonStrokingColor(0, 0, 0);
            contentStream.beginText();
            contentStream.newLineAtOffset(col1, rowY + 6);
            contentStream.showText(d.getTimestamp().format(formatter));
            contentStream.endText();
            
            contentStream.beginText();
            contentStream.newLineAtOffset(col2, rowY + 6);
            contentStream.showText(String.format("%,d", d.getSourceObjects()));
            contentStream.endText();
            
            contentStream.beginText();
            contentStream.newLineAtOffset(col3, rowY + 6);
            contentStream.showText(String.format("%,d", d.getTargetObjects()));
            contentStream.endText();
            
            contentStream.beginText();
            contentStream.newLineAtOffset(col4, rowY + 6);
            contentStream.showText(formatBytes(d.getSourceSize()));
            contentStream.endText();
            
            contentStream.beginText();
            contentStream.newLineAtOffset(col5, rowY + 6);
            contentStream.showText(formatBytes(d.getTargetSize()));
            contentStream.endText();
        }
        
        return tableY - (sortedData.size() + 1) * rowHeight;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        if (bytes < 1024L * 1024 * 1024 * 1024) return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        if (bytes < 1024L * 1024 * 1024 * 1024 * 1024) return String.format("%.2f TB", bytes / (1024.0 * 1024.0 * 1024.0 * 1024.0));
        return String.format("%.2f PB", bytes / (1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0));
    }
}
