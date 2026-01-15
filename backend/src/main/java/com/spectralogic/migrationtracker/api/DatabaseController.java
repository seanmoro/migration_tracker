package com.spectralogic.migrationtracker.api;

import com.spectralogic.migrationtracker.service.DatabaseService;
import com.spectralogic.migrationtracker.service.PostgreSQLRestoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/database")
@CrossOrigin(origins = "*")
public class DatabaseController {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseController.class);
    
    private final DatabaseService databaseService;
    private final PostgreSQLRestoreService postgreSQLRestoreService;

    public DatabaseController(DatabaseService databaseService, PostgreSQLRestoreService postgreSQLRestoreService) {
        this.databaseService = databaseService;
        this.postgreSQLRestoreService = postgreSQLRestoreService;
    }

    /**
     * Upload and restore PostgreSQL database backup (BlackPearl or Rio)
     * Accepts: .dump, .sql, .tar, .tar.gz, .zip files
     */
    @PostMapping("/restore-postgres")
    public ResponseEntity<Map<String, Object>> restorePostgreSQLDatabase(
            @RequestParam("file") MultipartFile file,
            @RequestParam("databaseType") String databaseType) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("error", "File is empty");
                return ResponseEntity.badRequest().body(response);
            }

            if (databaseType == null || (!databaseType.equalsIgnoreCase("blackpearl") && !databaseType.equalsIgnoreCase("rio"))) {
                response.put("success", false);
                response.put("error", "Database type must be 'blackpearl' or 'rio'");
                return ResponseEntity.badRequest().body(response);
            }

            String filename = file.getOriginalFilename();
            logger.info("Received PostgreSQL database restore request: type={}, file={}", databaseType, filename);

            PostgreSQLRestoreService.RestoreResult result = postgreSQLRestoreService.restoreDatabase(databaseType, file);
            
            response.put("success", result.isSuccess());
            if (result.isSuccess()) {
                response.put("message", result.getMessage());
            } else {
                response.put("error", result.getError());
            }
            response.put("filename", result.getFilename());
            response.put("databaseType", result.getDatabaseType());
            response.put("format", result.getFormat());
            
            if (result.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
            
        } catch (IOException e) {
            logger.error("Error restoring PostgreSQL database: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", "Failed to restore database: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", "Unexpected error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Upload and restore SQLite tracker database backup
     * Accepts: .db, .zip, .tar.gz, .gz files
     */
    @PostMapping("/restore")
    public ResponseEntity<Map<String, Object>> restoreDatabase(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("error", "File is empty");
                return ResponseEntity.badRequest().body(response);
            }

            String filename = file.getOriginalFilename();
            logger.info("Received SQLite database restore request for file: {}", filename);

            String message = databaseService.restoreDatabase(file);
            
            response.put("success", true);
            response.put("message", message);
            response.put("filename", filename);
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
            
        } catch (IOException e) {
            logger.error("Error restoring database: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", "Failed to restore database: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", "Unexpected error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get database information
     */
    @GetMapping("/info")
    public ResponseEntity<DatabaseService.DatabaseInfo> getDatabaseInfo() {
        return ResponseEntity.ok(databaseService.getDatabaseInfo());
    }
}
