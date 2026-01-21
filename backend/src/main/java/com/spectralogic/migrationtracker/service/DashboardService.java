package com.spectralogic.migrationtracker.service;

import com.spectralogic.migrationtracker.api.dto.CustomerPhases;
import com.spectralogic.migrationtracker.api.dto.DashboardStats;
import com.spectralogic.migrationtracker.api.dto.PhaseProgress;
import com.spectralogic.migrationtracker.api.dto.ProjectPhases;
import com.spectralogic.migrationtracker.model.MigrationPhase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final ReportService reportService;
    private final JdbcTemplate jdbcTemplate;

    public DashboardService(ReportService reportService, JdbcTemplate jdbcTemplate) {
        this.reportService = reportService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public DashboardStats getStats() {
        DashboardStats stats = new DashboardStats();
        
        // Count active phases
        Integer activePhases = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM migration_phase",
            Integer.class
        );
        stats.setActiveMigrations(activePhases != null ? activePhases : 0);

        // Sum total objects migrated
        Long totalObjects = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(target_objects), 0) FROM migration_data WHERE type = 'DATA'",
            Long.class
        );
        stats.setTotalObjectsMigrated(totalObjects != null ? totalObjects : 0L);

        // Calculate average progress
        // This is a simplified calculation
        Integer avgProgress = jdbcTemplate.queryForObject(
            "SELECT AVG(CASE WHEN source_objects > 0 THEN (target_objects * 100 / source_objects) ELSE 0 END) FROM migration_data WHERE type = 'DATA'",
            Integer.class
        );
        stats.setAverageProgress(avgProgress != null ? avgProgress : 0);

        // Phases needing attention (progress < 50%) - count using the same logic as getPhasesNeedingAttention
        // This will be calculated by checking actual latest progress, not historical data points
        List<PhaseProgress> attentionPhases = getPhasesNeedingAttention();
        stats.setPhasesNeedingAttention(attentionPhases.size());

        return stats;
    }

    public List<PhaseProgress> getActivePhases() {
        List<PhaseProgress> progressList = new ArrayList<>();
        
        // Get only active phases from database (increased limit to show more)
        // Filter by active = 1 (or active IS NULL for backward compatibility)
        List<MigrationPhase> allPhases = jdbcTemplate.query(
            "SELECT * FROM migration_phase WHERE (active IS NULL OR active = 1) ORDER BY created_at DESC LIMIT 100",
            (rs, rowNum) -> {
                MigrationPhase phase = new MigrationPhase();
                phase.setId(rs.getString("id"));
                phase.setName(rs.getString("name"));
                return phase;
            }
        );

        for (MigrationPhase phase : allPhases) {
            try {
                PhaseProgress progress = reportService.getPhaseProgress(phase.getId());
                // Only add phases that have data (sourceObjects > 0) or are at 0%
                if (progress.getSourceObjects() > 0 || progress.getProgress() == 0) {
                    progressList.add(progress);
                }
            } catch (Exception e) {
                // If progress calculation fails, create a basic progress object
                PhaseProgress progress = new PhaseProgress();
                progress.setPhaseId(phase.getId());
                progress.setPhaseName(phase.getName());
                progress.setProgress(0);
                progress.setSourceObjects(0L);
                progress.setTargetObjects(0L);
                progress.setSourceSize(0L);
                progress.setTargetSize(0L);
                progressList.add(progress);
            }
        }

        return progressList;
    }

    public List<CustomerPhases> getActivePhasesByCustomer() {
        List<CustomerPhases> customerPhasesList = new ArrayList<>();
        
        // Get only active phases with customer and project info
        List<Map<String, Object>> phaseData = jdbcTemplate.query(
            "SELECT mp.id as phase_id, mp.name as phase_name, mp.migration_id as project_id, " +
            "pj.name as project_name, pj.customer_id, c.name as customer_name " +
            "FROM migration_phase mp " +
            "JOIN migration_project pj ON mp.migration_id = pj.id " +
            "JOIN customer c ON pj.customer_id = c.id " +
            "WHERE c.active = 1 AND pj.active = 1 AND (mp.active IS NULL OR mp.active = 1) " +
            "ORDER BY c.name, pj.name, mp.name",
            (rs, rowNum) -> {
                Map<String, Object> data = new HashMap<>();
                data.put("phaseId", rs.getString("phase_id"));
                data.put("phaseName", rs.getString("phase_name"));
                data.put("projectId", rs.getString("project_id"));
                data.put("projectName", rs.getString("project_name"));
                data.put("customerId", rs.getString("customer_id"));
                data.put("customerName", rs.getString("customer_name"));
                return data;
            }
        );

        // Group by customer, then by project
        Map<String, Map<String, List<PhaseProgress>>> customerMap = new HashMap<>();
        Map<String, String> customerNames = new HashMap<>();
        Map<String, String> projectNames = new HashMap<>();

        for (Map<String, Object> data : phaseData) {
            String customerId = (String) data.get("customerId");
            String customerName = (String) data.get("customerName");
            String projectId = (String) data.get("projectId");
            String projectName = (String) data.get("projectName");
            String phaseId = (String) data.get("phaseId");
            
            // Store names for later use
            customerNames.putIfAbsent(customerId, customerName);
            projectNames.putIfAbsent(projectId, projectName);

            // Get phase progress
            try {
                PhaseProgress progress = reportService.getPhaseProgress(phaseId);
                if (progress.getSourceObjects() > 0 || progress.getProgress() == 0) {
                    // Initialize customer map if needed
                    customerMap.putIfAbsent(customerId, new HashMap<>());
                    Map<String, List<PhaseProgress>> projectMap = customerMap.get(customerId);
                    
                    // Initialize project map if needed
                    projectMap.putIfAbsent(projectId, new ArrayList<>());
                    projectMap.get(projectId).add(progress);
                }
            } catch (Exception e) {
                // If progress calculation fails, create a basic progress object
                PhaseProgress progress = new PhaseProgress();
                progress.setPhaseId(phaseId);
                progress.setPhaseName((String) data.get("phaseName"));
                progress.setProgress(0);
                progress.setSourceObjects(0L);
                progress.setTargetObjects(0L);
                progress.setSourceSize(0L);
                progress.setTargetSize(0L);
                
                customerMap.putIfAbsent(customerId, new HashMap<>());
                Map<String, List<PhaseProgress>> projectMap = customerMap.get(customerId);
                projectMap.putIfAbsent(projectId, new ArrayList<>());
                projectMap.get(projectId).add(progress);
            }
        }

        // Convert to CustomerPhases list
        for (Map.Entry<String, Map<String, List<PhaseProgress>>> customerEntry : customerMap.entrySet()) {
            String customerId = customerEntry.getKey();
            String customerName = customerNames.getOrDefault(customerId, "Unknown Customer");

            List<ProjectPhases> projects = new ArrayList<>();
            for (Map.Entry<String, List<PhaseProgress>> projectEntry : customerEntry.getValue().entrySet()) {
                String projectId = projectEntry.getKey();
                String projectName = projectNames.getOrDefault(projectId, "Unknown Project");

                projects.add(new ProjectPhases(projectId, projectName, projectEntry.getValue()));
            }

            customerPhasesList.add(new CustomerPhases(customerId, customerName, projects));
        }

        return customerPhasesList;
    }

    public List<PhaseProgress> getPhasesNeedingAttention() {
        List<PhaseProgress> attentionList = new ArrayList<>();
        
        // Get only active phases and check their latest progress
        // Filter by active = 1 (or active IS NULL for backward compatibility)
        List<MigrationPhase> activePhases = jdbcTemplate.query(
            "SELECT * FROM migration_phase WHERE (active IS NULL OR active = 1) ORDER BY created_at DESC",
            (rs, rowNum) -> {
                MigrationPhase phase = new MigrationPhase();
                phase.setId(rs.getString("id"));
                phase.setName(rs.getString("name"));
                return phase;
            }
        );

        for (MigrationPhase phase : activePhases) {
            try {
                PhaseProgress progress = reportService.getPhaseProgress(phase.getId());
                // Only include phases with progress < 50%
                if (progress.getProgress() < 50 && progress.getSourceObjects() > 0) {
                    attentionList.add(progress);
                }
            } catch (Exception e) {
                // Skip phases with errors
            }
        }

        // Sort by progress (lowest first)
        attentionList.sort((a, b) -> Integer.compare(a.getProgress(), b.getProgress()));

        return attentionList;
    }

    public List<Object> getRecentActivity() {
        // Return recent migration data points
        return jdbcTemplate.query(
            "SELECT md.*, mp.name as phase_name FROM migration_data md " +
            "JOIN migration_phase mp ON md.migration_phase_id = mp.id " +
            "ORDER BY md.timestamp DESC LIMIT 10",
            (rs, rowNum) -> {
                java.util.Map<String, Object> activity = new java.util.HashMap<>();
                activity.put("id", rs.getString("id"));
                activity.put("phaseName", rs.getString("phase_name"));
                String timestampStr = rs.getString("timestamp");
                activity.put("timestamp", timestampStr != null ? timestampStr : "");
                activity.put("sourceObjects", rs.getLong("source_objects"));
                activity.put("targetObjects", rs.getLong("target_objects"));
                return activity;
            }
        );
    }
}
