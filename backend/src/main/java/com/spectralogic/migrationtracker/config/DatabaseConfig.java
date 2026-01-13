package com.spectralogic.migrationtracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Paths;

@Configuration
public class DatabaseConfig {

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        
        // Get database path - try multiple locations
        String dbPath = null;
        
        // Try relative to backend directory (when running from backend/)
        java.io.File dbFile1 = new java.io.File("../resources/database/migrations.db");
        if (dbFile1.exists()) {
            dbPath = dbFile1.getAbsolutePath();
        } else {
            // Try relative to project root (when running from project root)
            java.io.File dbFile2 = new java.io.File("resources/database/migrations.db");
            if (dbFile2.exists()) {
                dbPath = dbFile2.getAbsolutePath();
            } else {
                // Try absolute path from current working directory
                dbPath = Paths.get("resources", "database", "migrations.db")
                        .toAbsolutePath()
                        .toString();
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
