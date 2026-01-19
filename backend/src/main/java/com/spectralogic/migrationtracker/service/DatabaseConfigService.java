package com.spectralogic.migrationtracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/**
 * Service for managing database configuration files
 * Automatically updates connection settings after database restore
 */
@Service
public class DatabaseConfigService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfigService.class);

    @Value("${migration.tracker.config.dir:}")
    private String configDir;

    /**
     * Automatically configure database connection after restore
     * Updates .env file or creates one if it doesn't exist
     */
    public void configureDatabaseAccess(String databaseType, String host, int port, String database, String username, String password) {
        logger.info("Configuring {} database access: {}@{}:{}/{}", databaseType, username, host, port, database);

        // Determine config file location
        Path envFile = getEnvFilePath();
        
        try {
            // Load existing .env file or create new one
            Properties props = loadEnvFile(envFile);
            
            // Update database connection settings
            String prefix = databaseType.equalsIgnoreCase("blackpearl") ? "MT_BLACKPEARL" : "MT_RIO";
            props.setProperty(prefix + "_HOST", host);
            props.setProperty(prefix + "_PORT", String.valueOf(port));
            props.setProperty(prefix + "_DATABASE", database);
            props.setProperty(prefix + "_USERNAME", username);
            props.setProperty(prefix + "_PASSWORD", password);
            
            // Save updated configuration
            saveEnvFile(envFile, props);
            
            logger.info("Successfully configured {} database access in {}", databaseType, envFile);
            
        } catch (IOException e) {
            logger.error("Failed to save database configuration: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save database configuration", e);
        }
    }

    /**
     * Configure local database access (localhost with default credentials)
     */
    public void configureLocalDatabaseAccess(String databaseType, String database) {
        configureDatabaseAccess(databaseType, "localhost", 5432, database, "postgres", "");
    }

    /**
     * Get .env file path
     */
    private Path getEnvFilePath() {
        // Try multiple locations
        if (configDir != null && !configDir.isEmpty()) {
            Path configPath = Paths.get(configDir, ".env");
            if (Files.exists(configPath.getParent())) {
                return configPath;
            }
        }
        
        // Try current working directory
        Path currentDir = Paths.get("").toAbsolutePath();
        Path envFile = currentDir.resolve(".env");
        if (Files.exists(envFile) || Files.exists(currentDir)) {
            return envFile;
        }
        
        // Try parent directory (when running from backend/)
        Path parentDir = currentDir.getParent();
        if (parentDir != null) {
            Path parentEnv = parentDir.resolve(".env");
            if (Files.exists(parentEnv) || Files.exists(parentDir)) {
                return parentEnv;
            }
        }
        
        // Default to current directory
        return currentDir.resolve(".env");
    }

    /**
     * Load .env file as Properties
     */
    private Properties loadEnvFile(Path envFile) throws IOException {
        Properties props = new Properties();
        
        if (Files.exists(envFile)) {
            try (BufferedReader reader = Files.newBufferedReader(envFile)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    // Skip comments and empty lines
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    // Handle "export KEY=VALUE" format
                    String key;
                    String value;
                    
                    // Check if line starts with "export"
                    if (line.startsWith("export ")) {
                        line = line.substring(7).trim(); // Remove "export "
                    }
                    
                    // Parse KEY=VALUE format
                    int eqIndex = line.indexOf('=');
                    if (eqIndex > 0) {
                        key = line.substring(0, eqIndex).trim();
                        value = line.substring(eqIndex + 1).trim();
                        // Remove quotes if present (but remember we need to add them back when saving)
                        if (value.startsWith("\"") && value.endsWith("\"")) {
                            value = value.substring(1, value.length() - 1);
                        } else if (value.startsWith("'") && value.endsWith("'")) {
                            value = value.substring(1, value.length() - 1);
                        }
                        props.setProperty(key, value);
                    }
                }
            }
            logger.debug("Loaded existing .env file from {}", envFile);
        } else {
            logger.info("Creating new .env file at {}", envFile);
        }
        
        return props;
    }

    /**
     * Save Properties to .env file
     */
    private void saveEnvFile(Path envFile, Properties props) throws IOException {
        // Ensure parent directory exists
        Files.createDirectories(envFile.getParent());
        
        // Write .env file in KEY=VALUE format
        try (BufferedWriter writer = Files.newBufferedWriter(envFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            // Write header comment
            writer.write("# Migration Tracker Database Configuration");
            writer.newLine();
            writer.write("# Auto-generated/updated by database restore");
            writer.newLine();
            writer.newLine();
            
            // Group related settings
            writeSection(writer, "BlackPearl Database", props, "MT_BLACKPEARL");
            writeSection(writer, "Rio Database", props, "MT_RIO");
            
            // Write other properties (like JAVA_OPTS, APP_DIR, etc.)
            boolean hasOtherProps = false;
            for (String key : props.stringPropertyNames()) {
                if (!key.startsWith("MT_BLACKPEARL") && !key.startsWith("MT_RIO")) {
                    hasOtherProps = true;
                    break;
                }
            }
            
            if (hasOtherProps) {
                writer.write("# Other Configuration");
                writer.newLine();
                for (String key : props.stringPropertyNames()) {
                    if (!key.startsWith("MT_BLACKPEARL") && !key.startsWith("MT_RIO")) {
                        String value = props.getProperty(key);
                        // Quote values that contain spaces or special characters (like JAVA_OPTS)
                        if (value != null && (value.contains(" ") || value.contains("-") || value.contains("+"))) {
                            writer.write("export " + key + "=\"" + value + "\"");
                        } else {
                            writer.write("export " + key + "=" + value);
                        }
                        writer.newLine();
                    }
                }
                writer.newLine();
            }
        }
        
        logger.info("Saved .env file to {}", envFile);
    }

    /**
     * Write a section of related properties
     */
    private void writeSection(BufferedWriter writer, String sectionName, Properties props, String prefix) throws IOException {
        boolean hasSection = false;
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                hasSection = true;
                break;
            }
        }
        
        if (!hasSection) {
            return;
        }
        
        writer.write("# " + sectionName);
        writer.newLine();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                String value = props.getProperty(key);
                // Quote values that contain spaces or special characters
                if (value != null && (value.contains(" ") || value.contains("-") || value.contains("+"))) {
                    writer.write("export " + key + "=\"" + value + "\"");
                } else {
                    writer.write("export " + key + "=" + value);
                }
                writer.newLine();
            }
        }
        writer.newLine();
    }

    /**
     * Get current database configuration
     */
    public DatabaseConfig getDatabaseConfig(String databaseType) {
        Path envFile = getEnvFilePath();
        Properties props;
        try {
            props = loadEnvFile(envFile);
        } catch (IOException e) {
            logger.warn("Could not load .env file: {}", e.getMessage());
            props = new Properties();
        }
        
        String prefix = databaseType.equalsIgnoreCase("blackpearl") ? "MT_BLACKPEARL" : "MT_RIO";
        DatabaseConfig config = new DatabaseConfig();
        config.host = props.getProperty(prefix + "_HOST", "localhost");
        config.port = Integer.parseInt(props.getProperty(prefix + "_PORT", "5432"));
        config.database = props.getProperty(prefix + "_DATABASE", databaseType.equalsIgnoreCase("blackpearl") ? "tapesystem" : "rio_db");
        config.username = props.getProperty(prefix + "_USERNAME", "postgres");
        config.password = props.getProperty(prefix + "_PASSWORD", "");
        
        return config;
    }

    public static class DatabaseConfig {
        public String host;
        public int port;
        public String database;
        public String username;
        public String password;
    }
}
