package com.spectralogic.migrationtracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Paths;

@Configuration
public class DatabaseConfig {

    @Value("${migration.tracker.database.path:}")
    private String configuredDbPath;

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        
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
        
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:" + dbPath);
        
        return dataSource;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
