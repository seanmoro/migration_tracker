package com.spectralogic.migrationtracker.service;

import com.spectralogic.migrationtracker.api.dto.Forecast;
import com.spectralogic.migrationtracker.api.dto.PhaseProgress;
import com.spectralogic.migrationtracker.model.MigrationData;
import com.spectralogic.migrationtracker.model.MigrationPhase;
import com.spectralogic.migrationtracker.repository.MigrationDataRepository;
import com.spectralogic.migrationtracker.repository.PhaseRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

        List<MigrationData> dataPoints = dataRepository.findByPhaseId(phaseId);
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
}
