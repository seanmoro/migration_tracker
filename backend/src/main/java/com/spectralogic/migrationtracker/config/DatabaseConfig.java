package com.spectralogic.migrationtracker.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.nio.file.Paths;

@Configuration
public class DatabaseConfig {

    @Value("${migration.tracker.database.path:}")
    private String configuredDbPath;

    @Bean
    public DataSource dataSource() {
        // Get database path - try multiple locations
        String dbPath = null;
        
        // Priority 1: Use environment variable or configured path if set
        if (configuredDbPath != null && !configuredDbPath.isEmpty()) {
            java.io.File configuredFile = new java.io.File(configuredDbPath);
            if (configuredFile.exists()) {
                dbPath = configuredFile.getAbsolutePath();
            } else {
                // Try to create parent directories if they don't exist
                configuredFile.getParentFile().mkdirs();
                dbPath = configuredFile.getAbsolutePath();
            }
        } else {
            // Priority 2: Try relative to backend directory (when running from backend/)
            java.io.File dbFile1 = new java.io.File("../resources/database/migrations.db");
            if (dbFile1.exists()) {
                dbPath = dbFile1.getAbsolutePath();
            } else {
                // Priority 3: Try relative to project root (when running from project root)
                java.io.File dbFile2 = new java.io.File("resources/database/migrations.db");
                if (dbFile2.exists()) {
                    dbPath = dbFile2.getAbsolutePath();
                } else {
                    // Priority 4: Try absolute path from current working directory
                    dbPath = Paths.get("resources", "database", "migrations.db")
                            .toAbsolutePath()
                            .toString();
                }
            }
        }
        
        System.out.println("Database path: " + dbPath);
        
        // Configure HikariCP connection pool for SQLite
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);
        
        // SQLite-specific connection pool settings
        // SQLite works best with a small connection pool due to its file-based nature
        config.setMaximumPoolSize(5);  // Small pool for SQLite
        config.setMinimumIdle(1);      // Keep at least 1 connection ready
        config.setConnectionTimeout(30000);  // 30 seconds
        config.setIdleTimeout(600000);  // 10 minutes
        config.setMaxLifetime(1800000); // 30 minutes
        config.setLeakDetectionThreshold(60000); // Detect connection leaks
        
        // SQLite-specific optimizations
        // Enable WAL mode for better concurrency (if supported)
        config.addDataSourceProperty("journal_mode", "WAL");
        // Set busy timeout to handle locked database gracefully
        config.addDataSourceProperty("busy_timeout", "30000");
        
        return new HikariDataSource(config);
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        @SuppressWarnings("null")
        JdbcTemplate template = new JdbcTemplate(dataSource);
        return template;
    }
}
