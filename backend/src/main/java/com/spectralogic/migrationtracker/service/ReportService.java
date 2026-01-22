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
        
        // Read from SQLite (migration_data table) - data gathered during "gather data" operation
        // PostgreSQL is only queried during gatherData(), not during progress reports
        Optional<MigrationData> latest = dataRepository.findLatestByPhaseId(phaseId);
        
        long sourceObjects = 0L;
        long sourceSize = 0L;
        long targetObjects = 0L;
        long targetSize = 0L;
        long sourceTapeCount = 0L;
        long targetTapeCount = 0L;
        
        if (latest.isPresent()) {
            MigrationData last = latest.get();
            sourceObjects = last.getSourceObjects() != null ? last.getSourceObjects() : 0L;
            sourceSize = last.getSourceSize() != null ? last.getSourceSize() : 0L;
            targetObjects = last.getTargetObjects() != null ? last.getTargetObjects() : 0L;
            targetSize = last.getTargetSize() != null ? last.getTargetSize() : 0L;
            logger.info("Using stored migration_data from SQLite: source={} objects ({} bytes), target={} objects ({} bytes)", 
                sourceObjects, sourceSize, targetObjects, targetSize);
        } else {
            logger.warn("No migration_data found in SQLite for phase '{}'. Progress will show 0. Run 'gather data' to collect metrics.", phaseId);
        }
        
        // Calculate tape counts from bucket_data if available
        // Sum up tape counts from individual bucket data records
        // Note: Tape counts aren't stored in migration_data, so we'd need to query PostgreSQL for this
        // For now, we'll leave them as 0 since they're not critical for progress calculation
        
        progress.setSourceObjects(sourceObjects);
        progress.setSourceSize(sourceSize);
        progress.setSourceTapeCount(sourceTapeCount);
        progress.setTargetObjects(targetObjects);
        progress.setTargetSize(targetSize);
        progress.setTargetTapeCount(targetTapeCount);
        
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
