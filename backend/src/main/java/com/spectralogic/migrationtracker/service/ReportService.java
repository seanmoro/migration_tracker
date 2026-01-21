package com.spectralogic.migrationtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spectralogic.migrationtracker.api.dto.ExportOptions;
import com.spectralogic.migrationtracker.api.dto.Forecast;
import com.spectralogic.migrationtracker.api.dto.PhaseProgress;
import com.spectralogic.migrationtracker.model.MigrationData;
import com.spectralogic.migrationtracker.model.MigrationPhase;
import com.spectralogic.migrationtracker.repository.MigrationDataRepository;
import com.spectralogic.migrationtracker.repository.PhaseRepository;
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

    private final PhaseRepository phaseRepository;
    private final MigrationDataRepository dataRepository;

    public ReportService(PhaseRepository phaseRepository, MigrationDataRepository dataRepository) {
        this.phaseRepository = phaseRepository;
        this.dataRepository = dataRepository;
    }

    public PhaseProgress getPhaseProgress(String phaseId) {
        MigrationPhase phase = phaseRepository.findById(phaseId)
            .orElseThrow(() -> new RuntimeException("Phase not found: " + phaseId));

        Optional<MigrationData> reference = dataRepository.findReferenceByPhaseId(phaseId);
        Optional<MigrationData> latest = dataRepository.findLatestByPhaseId(phaseId);

        PhaseProgress progress = new PhaseProgress();
        progress.setPhaseId(phaseId);
        progress.setPhaseName(phase.getName());

        if (latest.isPresent()) {
            MigrationData last = latest.get();
            progress.setSourceObjects(last.getSourceObjects());
            progress.setTargetObjects(last.getTargetObjects());
            progress.setSourceSize(last.getSourceSize());
            progress.setTargetSize(last.getTargetSize());

            // Calculate progress
            if (reference.isPresent()) {
                // Use reference as baseline
                MigrationData ref = reference.get();
                long totalSource = ref.getSourceObjects();
                long targetDiff = last.getTargetObjects() - ref.getTargetObjects();
                
                int progressPercent = totalSource > 0 
                    ? (int) ((targetDiff * 100) / totalSource)
                    : 0;
                
                progress.setProgress(Math.max(0, Math.min(100, progressPercent)));
            } else if (last.getSourceObjects() > 0) {
                // No reference - calculate based on current target vs source
                int progressPercent = (int) ((last.getTargetObjects() * 100) / last.getSourceObjects());
                progress.setProgress(Math.max(0, Math.min(100, progressPercent)));
            } else {
                progress.setProgress(0);
            }
        } else {
            // No data points at all
            progress.setProgress(0);
            progress.setSourceObjects(0L);
            progress.setTargetObjects(0L);
            progress.setSourceSize(0L);
            progress.setTargetSize(0L);
        }

        return progress;
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
