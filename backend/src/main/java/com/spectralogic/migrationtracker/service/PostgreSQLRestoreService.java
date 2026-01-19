package com.spectralogic.migrationtracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.GZIPInputStream;

@Service
public class PostgreSQLRestoreService {

    private static final Logger logger = LoggerFactory.getLogger(PostgreSQLRestoreService.class);
    
    private final DatabaseConfigService configService;

    public PostgreSQLRestoreService(DatabaseConfigService configService) {
        this.configService = configService;
    }

    @Value("${postgres.blackpearl.host:localhost}")
    private String blackpearlHost;

    @Value("${postgres.blackpearl.port:5432}")
    private int blackpearlPort;

    @Value("${postgres.blackpearl.database:tapesystem}")
    private String blackpearlDatabase;

    @Value("${postgres.blackpearl.username:postgres}")
    private String blackpearlUsername;

    @Value("${postgres.blackpearl.password:}")
    private String blackpearlPassword;

    @Value("${postgres.rio.host:localhost}")
    private String rioHost;

    @Value("${postgres.rio.port:5432}")
    private int rioPort;

    @Value("${postgres.rio.database:rio_db}")
    private String rioDatabase;

    @Value("${postgres.rio.username:postgres}")
    private String rioUsername;

    @Value("${postgres.rio.password:}")
    private String rioPassword;

    /**
     * Restore PostgreSQL database from backup file
     * Supports: .dump, .sql, .tar, .tar.gz, .zip, .zst
     */
    public RestoreResult restoreDatabase(String databaseType, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("File name is missing");
        }

        if (!databaseType.equalsIgnoreCase("blackpearl") && !databaseType.equalsIgnoreCase("rio")) {
            throw new IllegalArgumentException("Database type must be 'blackpearl' or 'rio'");
        }

        logger.info("Restoring {} database from file: {}", databaseType, originalFilename);

        // Get database connection info
        // If not configured, default to localhost for automatic setup
        DatabaseInfo dbInfo = getDatabaseInfo(databaseType);
        
        // If host is not configured or is default, use localhost for automatic setup
        if (dbInfo.host == null || dbInfo.host.isEmpty() || 
            (dbInfo.host.equals("localhost") && (dbInfo.password == null || dbInfo.password.isEmpty()))) {
            logger.info("No database connection configured - will restore to localhost with default credentials");
            dbInfo.host = "localhost";
            dbInfo.port = 5432;
            dbInfo.username = "postgres";
            dbInfo.password = "";
            // Use default database name
            if (databaseType.equalsIgnoreCase("blackpearl")) {
                dbInfo.database = "tapesystem";
            } else {
                dbInfo.database = "rio_db";
            }
        }

        // Create temp directory for processing
        Path tempDir = Files.createTempDirectory("pg-restore-");
        Path uploadedFile = tempDir.resolve(originalFilename);

        try {
            // Save uploaded file
            File targetFile = uploadedFile.toFile();
            if (targetFile == null) {
                throw new IOException("Failed to create target file");
            }
            file.transferTo(targetFile);
            logger.debug("Saved uploaded file to: {}", uploadedFile);

            // Extract and find database backup file
            Path backupFile = extractBackupFile(uploadedFile, tempDir);
            
            if (backupFile == null) {
                throw new IOException("No valid database backup file found. Supported: .dump, .sql, .tar, .tar.gz, .zst");
            }

            logger.info("Found backup file: {}", backupFile);

            // Determine backup format
            String filename = backupFile.getFileName().toString().toLowerCase();
            RestoreResult result = new RestoreResult();
            result.setDatabaseType(databaseType);
            result.setFilename(originalFilename);

            if (filename.endsWith(".dump")) {
                // PostgreSQL custom format dump
                result = restoreFromDump(databaseType, backupFile, dbInfo);
            } else if (filename.endsWith(".sql")) {
                // SQL script dump
                result = restoreFromSql(databaseType, backupFile, dbInfo);
            } else if (filename.endsWith(".tar") || filename.endsWith(".tar.gz")) {
                // TAR archive (BlackPearl format)
                result = restoreFromTar(databaseType, backupFile, tempDir, dbInfo);
            } else if (filename.endsWith(".zst")) {
                // Zstandard compressed file - decompress first, then try to restore
                Path decompressed = extractFromZst(backupFile, tempDir);
                String decompressedName = decompressed.getFileName().toString().toLowerCase();
                if (decompressedName.endsWith(".dump")) {
                    result = restoreFromDump(databaseType, decompressed, dbInfo);
                } else if (decompressedName.endsWith(".sql")) {
                    result = restoreFromSql(databaseType, decompressed, dbInfo);
                } else if (decompressedName.endsWith(".tar")) {
                    // Decompressed .zst gave us a .tar file - extract it and look for backup files
                    logger.info("Decompressed .zst file is a TAR archive, extracting and searching for backup files");
                    result = restoreFromPlainTar(databaseType, decompressed, tempDir, dbInfo);
                } else {
                    throw new IOException("Decompressed .zst file is not a recognized format: " + decompressedName);
                }
            } else {
                throw new IOException("Unsupported backup format: " + filename);
            }

            result.setFilename(originalFilename);
            return result;

        } finally {
            // Clean up temp directory
            deleteDirectory(tempDir.toFile());
        }
    }

    /**
     * Extract backup file from archive
     */
    private Path extractBackupFile(Path archiveFile, Path extractDir) throws IOException {
        String filename = archiveFile.getFileName().toString().toLowerCase();

        // If it's already a backup file, return it
        if (filename.endsWith(".dump") || filename.endsWith(".sql") || 
            filename.endsWith(".tar") || filename.endsWith(".tar.gz")) {
            return archiveFile;
        }

        // Handle ZIP files
        if (filename.endsWith(".zip")) {
            return extractFromZip(archiveFile, extractDir);
        }

        // Handle GZ files
        if (filename.endsWith(".gz")) {
            return extractFromGz(archiveFile, extractDir);
        }

        // Handle ZST files (Zstandard)
        if (filename.endsWith(".zst")) {
            return extractFromZst(archiveFile, extractDir);
        }

        throw new IOException("Unsupported archive format. Supported: .zip, .gz, .zst, .dump, .sql, .tar, .tar.gz");
    }

    private Path extractFromZip(Path zipFile, Path extractDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;
            Path backupFile = null;

            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = extractDir.resolve(entry.getName());
                
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    
                    try (FileOutputStream fos = new FileOutputStream(entryPath.toFile())) {
                        zis.transferTo(fos);
                    }
                    
                    // Look for backup files
                    String entryName = entry.getName().toLowerCase();
                    if (entryName.endsWith(".dump") || entryName.endsWith(".sql") || 
                        entryName.endsWith(".tar") || entryName.endsWith(".tar.gz") ||
                        entryName.endsWith(".zst")) {
                        backupFile = entryPath;
                    }
                }
                zis.closeEntry();
            }

            return backupFile;
        }
    }

    private Path extractFromGz(Path gzFile, Path extractDir) throws IOException {
        String baseName = gzFile.getFileName().toString();
        if (baseName.endsWith(".gz")) {
            baseName = baseName.substring(0, baseName.length() - 3);
        }
        
        Path outputFile = extractDir.resolve(baseName);
        
        try (GZIPInputStream gis = new GZIPInputStream(new FileInputStream(gzFile.toFile()));
             FileOutputStream fos = new FileOutputStream(outputFile.toFile())) {
            gis.transferTo(fos);
        }
        
        return outputFile;
    }

    private Path extractFromZst(Path zstFile, Path extractDir) throws IOException {
        String baseName = zstFile.getFileName().toString();
        if (baseName.endsWith(".zst")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }
        
        Path outputFile = extractDir.resolve(baseName);
        
        // Use zstd command-line tool to decompress
        List<String> command = new ArrayList<>();
        command.add("zstd");
        command.add("-d");
        command.add("-f");
        command.add("-o");
        command.add(outputFile.toAbsolutePath().toString());
        command.add(zstFile.toAbsolutePath().toString());
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        
        try {
            Process process = pb.start();
            
            // Capture output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.debug("zstd: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0 && Files.exists(outputFile)) {
                logger.info("Successfully decompressed .zst file to: {}", outputFile);
                return outputFile;
            } else {
                throw new IOException("zstd decompression failed with exit code " + exitCode + ": " + output.toString());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("zstd decompression interrupted", e);
        } catch (IOException e) {
            if (e.getMessage().contains("Cannot run program \"zstd\"")) {
                throw new IOException("zstd command not found. Please install zstd: sudo apt-get install zstd (Ubuntu) or sudo yum install zstd (CentOS)", e);
            }
            throw e;
        }
    }

    /**
     * Restore from PostgreSQL custom format dump (.dump)
     */
    private RestoreResult restoreFromDump(String databaseType, Path dumpFile, DatabaseInfo dbInfo) throws IOException {
        RestoreResult result = new RestoreResult();
        result.setDatabaseType(databaseType);
        result.setFormat("custom");

        // Build pg_restore command
        List<String> command = new ArrayList<>();
        command.add("pg_restore");
        command.add("-h"); command.add(dbInfo.host);
        command.add("-p"); command.add(String.valueOf(dbInfo.port));
        command.add("-U"); command.add(dbInfo.username);
        command.add("-d"); command.add(dbInfo.database);
        command.add("--clean");
        command.add("--if-exists");
        command.add("--no-owner");
        command.add("--no-acl");
        command.add(dumpFile.toAbsolutePath().toString());

        // Set password via environment variable
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().put("PGPASSWORD", dbInfo.password);
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            
            // Capture output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.debug("pg_restore: {}", line);
                }
            }

            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                result.setSuccess(true);
                result.setMessage("Database restored successfully from .dump file");
                logger.info("Successfully restored {} database from dump file", databaseType);
                
                // Automatically configure database access
                configureDatabaseAccessAfterRestore(databaseType, dbInfo);
            } else {
                result.setSuccess(false);
                result.setError("pg_restore failed with exit code " + exitCode + ": " + output.toString());
                logger.error("pg_restore failed: {}", output.toString());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.setSuccess(false);
            result.setError("Restore process was interrupted: " + e.getMessage());
            throw new IOException("Restore process interrupted", e);
        } catch (IOException e) {
            result.setSuccess(false);
            result.setError("Failed to execute pg_restore: " + e.getMessage());
            throw e;
        }

        return result;
    }

    /**
     * Restore from SQL script (.sql)
     */
    private RestoreResult restoreFromSql(String databaseType, Path sqlFile, DatabaseInfo dbInfo) throws IOException {
        RestoreResult result = new RestoreResult();
        result.setDatabaseType(databaseType);
        result.setFormat("sql");

        // Build psql command
        List<String> command = new ArrayList<>();
        command.add("psql");
        command.add("-h"); command.add(dbInfo.host);
        command.add("-p"); command.add(String.valueOf(dbInfo.port));
        command.add("-U"); command.add(dbInfo.username);
        command.add("-d"); command.add(dbInfo.database);
        command.add("-f"); command.add(sqlFile.toAbsolutePath().toString());

        // Set password via environment variable
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().put("PGPASSWORD", dbInfo.password);
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            
            // Capture output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.debug("psql: {}", line);
                }
            }

            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                result.setSuccess(true);
                result.setMessage("Database restored successfully from SQL file");
                logger.info("Successfully restored {} database from SQL file", databaseType);
                
                // Automatically configure database access
                configureDatabaseAccessAfterRestore(databaseType, dbInfo);
            } else {
                result.setSuccess(false);
                result.setError("psql failed with exit code " + exitCode + ": " + output.toString());
                logger.error("psql failed: {}", output.toString());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.setSuccess(false);
            result.setError("Restore process was interrupted: " + e.getMessage());
            throw new IOException("Restore process interrupted", e);
        } catch (IOException e) {
            result.setSuccess(false);
            result.setError("Failed to execute psql: " + e.getMessage());
            throw e;
        }

        return result;
    }

    /**
     * Restore from TAR archive (PostgreSQL TAR format)
     * PostgreSQL TAR format can be restored using pg_restore with -F t flag
     * Handles both .tar and .tar.gz files
     */
    private RestoreResult restoreFromTar(String databaseType, Path tarFile, Path extractDir, DatabaseInfo dbInfo) throws IOException {
        RestoreResult result = new RestoreResult();
        result.setDatabaseType(databaseType);
        
        String filename = tarFile.getFileName().toString().toLowerCase();
        Path actualTarFile = tarFile;
        
        // If it's a .tar.gz, decompress it first
        if (filename.endsWith(".tar.gz") || filename.endsWith(".tgz")) {
            result.setFormat("tar.gz");
            logger.info("Decompressing .tar.gz file: {}", tarFile);
            actualTarFile = extractFromGz(tarFile, extractDir);
            logger.info("Decompressed to: {}", actualTarFile);
        } else {
            result.setFormat("tar");
        }

        // Build pg_restore command for TAR format
        List<String> command = new ArrayList<>();
        command.add("pg_restore");
        command.add("-h"); command.add(dbInfo.host);
        command.add("-p"); command.add(String.valueOf(dbInfo.port));
        command.add("-U"); command.add(dbInfo.username);
        command.add("-d"); command.add(dbInfo.database);
        command.add("-F"); command.add("t");  // TAR format
        command.add("-v");  // Verbose
        command.add("--no-owner");  // Don't restore ownership
        command.add("--no-privileges");  // Don't restore privileges
        command.add(actualTarFile.toAbsolutePath().toString());

        // Set password via environment variable
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().put("PGPASSWORD", dbInfo.password);
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            
            // Capture output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.debug("pg_restore (tar): {}", line);
                }
            }

            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                result.setSuccess(true);
                result.setMessage("Database restored successfully from TAR archive");
                logger.info("Successfully restored {} database from TAR archive", databaseType);
                
                // Automatically configure database access
                configureDatabaseAccessAfterRestore(databaseType, dbInfo);
            } else {
                // Check if it's a plain TAR archive (not PostgreSQL TAR format)
                String errorOutput = output.toString().toLowerCase();
                if ((errorOutput.contains("could not find header for file") && errorOutput.contains("toc.dat")) ||
                    errorOutput.contains("could not find header") ||
                    errorOutput.contains("not a tar archive") ||
                    errorOutput.contains("invalid tar header") ||
                    (errorOutput.contains("toc.dat") && errorOutput.contains("tar archive"))) {
                    // This is likely a plain TAR archive, not PostgreSQL TAR format
                    // Try extracting it and looking for .sql or .dump files
                    logger.info("TAR file is not PostgreSQL TAR format, attempting to extract and find backup files");
                    return restoreFromPlainTar(databaseType, actualTarFile, extractDir, dbInfo);
                } else {
                    result.setSuccess(false);
                    result.setError("pg_restore failed with exit code " + exitCode + ": " + output.toString());
                    logger.error("pg_restore (tar) failed: {}", output.toString());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.setSuccess(false);
            result.setError("Restore process was interrupted: " + e.getMessage());
            throw new IOException("Restore process interrupted", e);
        } catch (IOException e) {
            result.setSuccess(false);
            result.setError("Failed to execute pg_restore: " + e.getMessage());
            throw e;
        }

        return result;
    }

    /**
     * Restore from plain TAR archive (not PostgreSQL TAR format)
     * Extracts the TAR and looks for .sql or .dump files inside
     */
    private RestoreResult restoreFromPlainTar(String databaseType, Path tarFile, Path extractDir, DatabaseInfo dbInfo) throws IOException {
        RestoreResult result = new RestoreResult();
        result.setDatabaseType(databaseType);
        result.setFormat("tar (plain)");

        // Extract TAR archive using system tar command
        List<String> extractCommand = new ArrayList<>();
        extractCommand.add("tar");
        extractCommand.add("-xf");
        extractCommand.add(tarFile.toAbsolutePath().toString());
        extractCommand.add("-C");
        extractCommand.add(extractDir.toAbsolutePath().toString());

        ProcessBuilder extractPb = new ProcessBuilder(extractCommand);
        extractPb.redirectErrorStream(true);

        try {
            Process extractProcess = extractPb.start();
            StringBuilder extractOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(extractProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    extractOutput.append(line).append("\n");
                    logger.debug("tar extract: {}", line);
                }
            }

            int extractExitCode = extractProcess.waitFor();
            if (extractExitCode != 0) {
                result.setSuccess(false);
                result.setError("Failed to extract TAR archive: " + extractOutput.toString());
                return result;
            }

            logger.info("Successfully extracted TAR archive to: {}", extractDir);

            // Look for .sql or .dump files in the extracted directory
            final Path[] foundSqlFile = {null};
            final Path[] foundDumpFile = {null};
            final List<String> foundFiles = new ArrayList<>();
            
            try {
                Files.walk(extractDir).forEach(path -> {
                    if (Files.isRegularFile(path)) {
                        String name = path.getFileName().toString().toLowerCase();
                        String relativePath = extractDir.relativize(path).toString();
                        
                        // Collect all files for diagnostics
                        foundFiles.add(relativePath);
                        
                        if (name.endsWith(".sql")) {
                            if (foundSqlFile[0] == null) {
                                foundSqlFile[0] = path;
                            }
                        } else if (name.endsWith(".dump")) {
                            if (foundDumpFile[0] == null) {
                                foundDumpFile[0] = path;
                            }
                        }
                    }
                });
            } catch (IOException e) {
                result.setSuccess(false);
                result.setError("Failed to search extracted files: " + e.getMessage());
                return result;
            }

            // Restore using the found file
            if (foundDumpFile[0] != null) {
                logger.info("Found .dump file in TAR archive: {}", foundDumpFile[0]);
                return restoreFromDump(databaseType, foundDumpFile[0], dbInfo);
            } else if (foundSqlFile[0] != null) {
                logger.info("Found .sql file in TAR archive: {}", foundSqlFile[0]);
                return restoreFromSql(databaseType, foundSqlFile[0], dbInfo);
            } else {
                // Provide detailed error message with list of files found
                StringBuilder errorMsg = new StringBuilder();
                errorMsg.append("No .sql or .dump files found in TAR archive.");
                
                if (foundFiles.isEmpty()) {
                    errorMsg.append(" The archive appears to be empty or contains only directories.");
                } else {
                    errorMsg.append(" Found ").append(foundFiles.size()).append(" file(s) in archive:");
                    int maxFiles = Math.min(foundFiles.size(), 10);
                    for (int i = 0; i < maxFiles; i++) {
                        errorMsg.append("\n  - ").append(foundFiles.get(i));
                    }
                    if (foundFiles.size() > 10) {
                        errorMsg.append("\n  ... and ").append(foundFiles.size() - 10).append(" more file(s)");
                    }
                    errorMsg.append("\n\nPlease ensure the archive contains a PostgreSQL backup file (.sql or .dump).");
                }
                
                result.setSuccess(false);
                result.setError(errorMsg.toString());
                logger.warn("TAR archive extracted but no backup files found. Files in archive: {}", foundFiles);
                return result;
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.setSuccess(false);
            result.setError("TAR extraction was interrupted: " + e.getMessage());
            throw new IOException("TAR extraction interrupted", e);
        } catch (IOException e) {
            result.setSuccess(false);
            result.setError("Failed to extract TAR archive: " + e.getMessage());
            throw e;
        }
    }

    private DatabaseInfo getDatabaseInfo(String databaseType) {
        DatabaseInfo info = new DatabaseInfo();
        if (databaseType.equalsIgnoreCase("blackpearl")) {
            info.host = blackpearlHost;
            info.port = blackpearlPort;
            info.database = blackpearlDatabase;
            info.username = blackpearlUsername;
            info.password = blackpearlPassword != null ? blackpearlPassword : "";
        } else {
            info.host = rioHost;
            info.port = rioPort;
            info.database = rioDatabase;
            info.username = rioUsername;
            info.password = rioPassword != null ? rioPassword : "";
        }
        return info;
    }

    /**
     * Automatically configure database access after successful restore
     * Updates .env file with connection settings so credentials are not needed separately
     */
    private void configureDatabaseAccessAfterRestore(String databaseType, DatabaseInfo dbInfo) {
        try {
            // If restoring to localhost, use default postgres credentials
            // This allows the tool to access the database without separate credentials
            if ("localhost".equals(dbInfo.host) || "127.0.0.1".equals(dbInfo.host)) {
                logger.info("Restored to localhost - configuring local database access");
                configService.configureLocalDatabaseAccess(databaseType, dbInfo.database);
            } else {
                // For remote databases, save the connection info
                logger.info("Restored to remote host - saving connection configuration");
                configService.configureDatabaseAccess(
                    databaseType,
                    dbInfo.host,
                    dbInfo.port,
                    dbInfo.database,
                    dbInfo.username,
                    dbInfo.password
                );
            }
        } catch (Exception e) {
            logger.warn("Failed to automatically configure database access: {}", e.getMessage());
            // Don't fail the restore if config save fails
        }
    }

    /**
     * Recursively delete directory
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    private static class DatabaseInfo {
        String host;
        int port;
        String database;
        String username;
        String password;
    }

    public static class RestoreResult {
        private boolean success;
        private String message;
        private String error;
        private String databaseType;
        private String format;
        private String filename;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public String getDatabaseType() {
            return databaseType;
        }

        public void setDatabaseType(String databaseType) {
            this.databaseType = databaseType;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }
    }
}
