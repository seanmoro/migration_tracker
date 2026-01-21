package com.spectralogic.migrationtracker.service;

import com.spectralogic.migrationtracker.model.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.GZIPInputStream;

@Service
public class PostgreSQLRestoreService {

    private static final Logger logger = LoggerFactory.getLogger(PostgreSQLRestoreService.class);
    
    private final DatabaseConfigService configService;
    private final CustomerService customerService;

    public PostgreSQLRestoreService(DatabaseConfigService configService, CustomerService customerService) {
        this.configService = configService;
        this.customerService = customerService;
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

    @Value("${postgres.blackpearl.data-directory:}")
    private String blackpearlDataDirectory;

    @Value("${postgres.rio.data-directory:}")
    private String rioDataDirectory;

    @Value("${postgres.backup.keep-count:3}")
    private int maxBackupsToKeep;

    /**
     * Restore PostgreSQL database from backup file
     * Supports: .dump, .sql, .tar, .tar.gz, .zip, .zst
     */
    public RestoreResult restoreDatabase(String databaseType, String customerId, MultipartFile file) throws IOException {
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

        if (customerId == null || customerId.isEmpty()) {
            throw new IllegalArgumentException("Customer ID is required");
        }

        // Get customer to construct database name
        Customer customer = customerService.findById(customerId);
        String customerName = customer.getName().toLowerCase().replaceAll("[^a-z0-9]", "_");
        
        logger.info("Restoring {} database for customer {} ({}) from file: {}", databaseType, customer.getName(), customerId, originalFilename);

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
            // Use customer-specific database name: tapesystem_customer_name or rio_db_customer_name
            if (databaseType.equalsIgnoreCase("blackpearl")) {
                dbInfo.database = "tapesystem_" + customerName;
            } else {
                dbInfo.database = "rio_db_" + customerName;
            }
            logger.info("Using customer-specific database name: {}", dbInfo.database);
        } else {
            // If database is configured, still use customer-specific name
            String baseDatabase = databaseType.equalsIgnoreCase("blackpearl") ? "tapesystem" : "rio_db";
            dbInfo.database = baseDatabase + "_" + customerName;
            logger.info("Using customer-specific database name: {}", dbInfo.database);
        }

        // Create database if it doesn't exist
        createDatabaseIfNotExists(dbInfo);

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
                // PostgreSQL backup compressed with Zstandard - decompress first, then restore
                // Process: .zst → zstd -d → .tar → tar -xvf → find .sql/.dump → restore with psql/pg_restore
                Path decompressed = extractFromZst(backupFile, tempDir);
                String decompressedName = decompressed.getFileName().toString().toLowerCase();
                if (decompressedName.endsWith(".dump")) {
                    // PostgreSQL custom format dump - restore with pg_restore
                    result = restoreFromDump(databaseType, decompressed, dbInfo);
                } else if (decompressedName.endsWith(".sql")) {
                    // PostgreSQL SQL script - restore with psql
                    result = restoreFromSql(databaseType, decompressed, dbInfo);
                } else if (decompressedName.endsWith(".tar")) {
                    // Decompressed .zst gave us a .tar file - extract with tar -xvf and search for PostgreSQL backup files
                    logger.info("Decompressed .zst file is a TAR archive, extracting and searching for PostgreSQL backup files (.sql or .dump)");
                    result = restoreFromPlainTar(databaseType, decompressed, tempDir, dbInfo);
                } else {
                    throw new IOException("Decompressed .zst file is not a recognized PostgreSQL backup format: " + decompressedName);
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
            Path decompressed = extractFromZst(archiveFile, extractDir);
            // Recursively check if the decompressed file needs further extraction
            // e.g., .tar.zst → .tar → extract and find backup files
            String decompressedName = decompressed.getFileName().toString().toLowerCase();
            if (decompressedName.endsWith(".tar") || decompressedName.endsWith(".tar.gz")) {
                // Decompressed .zst gave us a .tar file - return it for main restore logic to handle
                return decompressed;
            }
            // If decompressed to .dump or .sql, return it directly
            if (decompressedName.endsWith(".dump") || decompressedName.endsWith(".sql")) {
                return decompressed;
            }
            // Otherwise, recursively try to extract further
            return extractBackupFile(decompressed, extractDir);
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

            // Check if this is a PostgreSQL data directory backup
            // Look for characteristic PostgreSQL data directory files/directories
            boolean isDataDirectoryBackup = false;
            final List<String> foundFiles = new ArrayList<>();
            final Path[] foundSqlFile = {null};
            final Path[] foundDumpFile = {null};
            
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
                    } else if (Files.isDirectory(path)) {
                        String dirName = path.getFileName().toString().toLowerCase();
                        // Check for PostgreSQL data directory structure
                        if (dirName.equals("base") || dirName.equals("global") || 
                            dirName.equals("pg_wal") || dirName.equals("pg_xact") ||
                            dirName.equals("pg_multixact") || dirName.equals("pg_subtrans")) {
                            foundFiles.add(extractDir.relativize(path).toString() + "/");
                        }
                    }
                });
                
                // Check for PG_VERSION file which indicates data directory backup
                Path pgVersion = extractDir.resolve("PG_VERSION");
                if (Files.exists(pgVersion)) {
                    isDataDirectoryBackup = true;
                    logger.info("Detected PostgreSQL data directory backup (found PG_VERSION file)");
                }
                
                // Also check if we found data directory structure
                if (!isDataDirectoryBackup) {
                    long dataDirDirs = foundFiles.stream()
                        .filter(f -> f.contains("base/") || f.contains("global/") || 
                                   f.contains("pg_wal/") || f.contains("pg_xact/"))
                        .count();
                    if (dataDirDirs > 0) {
                        isDataDirectoryBackup = true;
                        logger.info("Detected PostgreSQL data directory backup (found data directory structure)");
                    }
                }
            } catch (IOException e) {
                result.setSuccess(false);
                result.setError("Failed to search extracted files: " + e.getMessage());
                return result;
            }

            // If this is a data directory backup, restore it directly
            if (isDataDirectoryBackup) {
                logger.info("TAR archive contains PostgreSQL data directory backup, restoring to data directory");
                return restoreFromDataDirectory(databaseType, extractDir, dbInfo);
            }

            // Restore using the found file (pg_dump backup)
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

    /**
     * Restore PostgreSQL data directory backup
     * Extracts the data directory backup to the PostgreSQL data directory
     */
    private RestoreResult restoreFromDataDirectory(String databaseType, Path extractedDataDir, DatabaseInfo dbInfo) throws IOException {
        RestoreResult result = new RestoreResult();
        result.setDatabaseType(databaseType);
        result.setFormat("data directory");

        logger.info("Restoring PostgreSQL data directory backup for {}", databaseType);

        Path pgDataDir = null;
        try {
            // Get PostgreSQL data directory path
            pgDataDir = getPostgreSQLDataDirectory(databaseType, dbInfo);
            if (pgDataDir == null) {
                result.setSuccess(false);
                StringBuilder errorMsg = new StringBuilder();
                errorMsg.append("Could not determine PostgreSQL data directory.\n\n");
                errorMsg.append("Please configure it using one of these methods:\n\n");
                errorMsg.append("1. Set environment variable:\n");
                if (databaseType.equalsIgnoreCase("blackpearl")) {
                    errorMsg.append("   export MT_BLACKPEARL_DATA_DIRECTORY=/path/to/postgres/data\n");
                } else {
                    errorMsg.append("   export MT_RIO_DATA_DIRECTORY=/path/to/postgres/data\n");
                }
                errorMsg.append("\n2. Or add to application.yml:\n");
                errorMsg.append("   postgres:\n");
                errorMsg.append("     ").append(databaseType.toLowerCase()).append(":\n");
                errorMsg.append("       data-directory: /path/to/postgres/data\n\n");
                errorMsg.append("3. Common locations tried:\n");
                errorMsg.append("   - /var/lib/postgresql/14/main\n");
                errorMsg.append("   - /var/lib/postgresql/data\n");
                errorMsg.append("   - /usr/local/pgsql/data\n");
                errorMsg.append("   - /opt/postgresql/data\n\n");
                errorMsg.append("Note: PostgreSQL may need to be running to query the data directory, ");
                errorMsg.append("or you can specify it explicitly using the methods above.");
                result.setError(errorMsg.toString());
                return result;
            }

            logger.info("PostgreSQL data directory: {}", pgDataDir);

            // Check if data directory exists
            if (!Files.exists(pgDataDir)) {
                Files.createDirectories(pgDataDir);
                logger.info("Created PostgreSQL data directory: {}", pgDataDir);
            }

            // Stop PostgreSQL if running
            logger.info("Stopping PostgreSQL service...");
            boolean postgresStopped = stopPostgreSQL(pgDataDir);
            
            // Wait a moment and verify PostgreSQL actually stopped
            if (postgresStopped) {
                try {
                    Thread.sleep(2000); // Wait for PostgreSQL to fully stop
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            // Check if PostgreSQL is still running
            if (isPostgreSQLRunning()) {
                logger.error("PostgreSQL is still running after stop attempt.");
                
                // Try one more time with pg_ctl using the actual data directory
                logger.info("Attempting to stop PostgreSQL using pg_ctl with data directory: {}", pgDataDir);
                boolean pgCtlStopped = false;
                if (Files.exists(pgDataDir.resolve("postmaster.pid"))) {
                    // PostgreSQL is definitely running (postmaster.pid exists)
                    try {
                        List<String> command = new ArrayList<>();
                        command.add("pg_ctl");
                        command.add("stop");
                        command.add("-D");
                        command.add(pgDataDir.toAbsolutePath().toString());
                        command.add("-m");
                        command.add("fast"); // Fast shutdown
                        
                        ProcessBuilder pb = new ProcessBuilder(command);
                        pb.redirectErrorStream(true);
                        
                        Process process = pb.start();
                        StringBuilder output = new StringBuilder();
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(process.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                output.append(line).append("\n");
                                logger.debug("pg_ctl stop: {}", line);
                            }
                        }
                        
                        int exitCode = process.waitFor();
                        if (exitCode == 0) {
                            logger.info("PostgreSQL stopped via pg_ctl");
                            pgCtlStopped = true;
                            // Wait for PostgreSQL to fully stop
                            Thread.sleep(2000);
                        } else {
                            logger.warn("pg_ctl stop failed with exit code: {}, output: {}", exitCode, output.toString());
                        }
                    } catch (Exception e) {
                        logger.warn("pg_ctl stop failed: {}", e.getMessage());
                    }
                }
                
                // Check again if PostgreSQL is still running
                if (!pgCtlStopped && isPostgreSQLRunning()) {
                    logger.error("PostgreSQL is still running. Cannot proceed safely.");
                    result.setSuccess(false);
                    result.setError("PostgreSQL must be stopped before restoring data directory backup. " +
                            "The automatic stop failed because sudo requires a password. " +
                            "Please stop PostgreSQL manually with: sudo systemctl stop postgresql " +
                            "and try again. To enable automatic stopping, configure passwordless sudo: " +
                            "echo 'your_user ALL=(ALL) NOPASSWD: /bin/systemctl stop postgresql, /bin/systemctl start postgresql' | sudo tee /etc/sudoers.d/migration-tracker");
                    return result;
                } else if (pgCtlStopped) {
                    logger.info("PostgreSQL stopped successfully via pg_ctl");
                }
            } else {
                if (postgresStopped) {
                    logger.info("PostgreSQL stopped successfully");
                } else {
                    logger.info("PostgreSQL is already stopped");
                }
            }

            // Backup existing data directory if it exists and has content
            if (Files.exists(pgDataDir) && Files.list(pgDataDir).count() > 0) {
                Path backupDir = pgDataDir.getParent().resolve(pgDataDir.getFileName().toString() + "_backup_" + System.currentTimeMillis());
                logger.info("Backing up existing data directory to: {}", backupDir);
                boolean backupSuccess = copyDirectoryWithPermissions(pgDataDir, backupDir);
                if (!backupSuccess) {
                    try {
                        copyDirectory(pgDataDir, backupDir);
                        logger.info("Backup completed (without sudo)");
                    } catch (Exception e) {
                        logger.warn("Could not backup existing data directory: {}. Continuing with restore...", e.getMessage());
                    }
                } else {
                    logger.info("Backup completed");
                }
                
                // Clean up old backups to prevent directory from getting too large
                cleanupOldBackups(pgDataDir.getParent(), pgDataDir.getFileName().toString());
            }

            // Copy extracted data directory contents to PostgreSQL data directory
            logger.info("Copying data directory backup to: {}", pgDataDir);
            
            // Find the actual data directory in the extracted archive
            // It might be at the root or in a subdirectory
            final Path[] sourceDataDir = {extractedDataDir};
            Path pgVersion = extractedDataDir.resolve("PG_VERSION");
            
            // If PG_VERSION is not at root, search for it
            if (!Files.exists(pgVersion)) {
                try {
                    Files.walk(extractedDataDir, 3).forEach(path -> {
                        if (path.getFileName().toString().equals("PG_VERSION")) {
                            sourceDataDir[0] = path.getParent();
                        }
                    });
                } catch (IOException e) {
                    logger.warn("Could not find PG_VERSION in extracted archive, using root directory");
                }
            }

            // Copy all files and directories from source to destination
            // Use rsync with sudo for better permission handling
            logger.info("Attempting to copy data directory from {} to {}", sourceDataDir[0], pgDataDir);
            boolean copySuccess = copyDirectoryWithPermissions(sourceDataDir[0], pgDataDir);
            if (!copySuccess) {
                logger.warn("copyDirectoryWithPermissions failed. Checking if target directory is writable...");
                // Check if we can write to the target directory before attempting fallback
                // PostgreSQL data directories are typically protected and require root/postgres permissions
                boolean canWrite = false;
                try {
                    // Try to create a test file in the target directory to check write permissions
                    Path testFile = pgDataDir.resolve(".write_test_" + System.currentTimeMillis());
                    try {
                        // Ensure parent directory exists
                        Files.createDirectories(pgDataDir);
                        Files.createFile(testFile);
                        Files.delete(testFile);
                        canWrite = true;
                        logger.debug("Write test succeeded - target directory is writable");
                    } catch (java.nio.file.AccessDeniedException e) {
                        canWrite = false;
                        logger.warn("Write test failed with AccessDeniedException - target directory is protected");
                    }
                } catch (Exception e) {
                    // If we can't even check, assume we can't write
                    canWrite = false;
                    logger.warn("Write test failed with exception: {} - assuming target directory is protected", e.getMessage());
                }
                
                if (!canWrite) {
                    // Protected directory - skip fallback and show clear error
                    result.setSuccess(false);
                    String username = System.getProperty("user.name", "your_user");
                    String errorMsg = "Permission denied: Cannot write to " + pgDataDir + 
                                  "\n\nThe PostgreSQL data directory requires root/postgres user permissions." +
                                  "\n\nOptions:" +
                                  "\n1. Configure passwordless sudo for rsync (recommended):" +
                                  "\n   echo '" + username + " ALL=(ALL) NOPASSWD: /usr/bin/rsync' | sudo tee /etc/sudoers.d/migration-tracker-rsync" +
                                  "\n   sudo chmod 0440 /etc/sudoers.d/migration-tracker-rsync" +
                                  "\n\n2. Copy manually before restoring:" +
                                  "\n   sudo rsync -av " + sourceDataDir[0] + "/ " + pgDataDir + "/" +
                                  "\n   sudo chown -R postgres:postgres " + pgDataDir +
                                  "\n\n3. Run the application with sudo:" +
                                  "\n   sudo -E java -jar backend/target/migration-tracker-api-1.0.0.jar";
                    result.setError(errorMsg);
                    logger.error("Failed to copy data directory - permission denied. Protected directory requires elevated permissions.");
                    return result;
                }
                
                // Try regular copy as fallback (only if we can write)
                try {
                    copyDirectory(sourceDataDir[0], pgDataDir);
                    logger.info("Data directory files copied successfully (without sudo)");
                } catch (java.nio.file.AccessDeniedException e) {
                    result.setSuccess(false);
                    String username = System.getProperty("user.name", "your_user");
                    result.setError("Permission denied: Cannot write to " + pgDataDir + 
                                  "\n\nThe PostgreSQL data directory requires root/postgres user permissions." +
                                  "\n\nOptions:" +
                                  "\n1. Configure passwordless sudo for rsync (recommended):" +
                                  "\n   echo '" + username + " ALL=(ALL) NOPASSWD: /usr/bin/rsync' | sudo tee /etc/sudoers.d/migration-tracker-rsync" +
                                  "\n   sudo chmod 0440 /etc/sudoers.d/migration-tracker-rsync" +
                                  "\n\n2. Copy manually before restoring:" +
                                  "\n   sudo rsync -av " + sourceDataDir[0] + "/ " + pgDataDir + "/" +
                                  "\n   sudo chown -R postgres:postgres " + pgDataDir +
                                  "\n\n3. Run the application with sudo:" +
                                  "\n   sudo -E java -jar backend/target/migration-tracker-api-1.0.0.jar");
                    logger.error("Failed to copy data directory - permission denied", e);
                    return result;
                } catch (Exception e) {
                    result.setSuccess(false);
                    result.setError("Failed to copy data directory files: " + e.getMessage() + 
                                  "\n\nThis may require sudo permissions. You can copy manually:\n" +
                                  "sudo rsync -av " + sourceDataDir[0] + "/ " + pgDataDir + "/\n" +
                                  "Or ensure the application has write permissions to: " + pgDataDir);
                    logger.error("Failed to copy data directory", e);
                    return result;
                }
            } else {
                logger.info("Data directory files copied successfully");
            }

            // Set proper permissions (PostgreSQL data directory should be owned by postgres user)
            logger.info("Setting proper ownership and permissions...");
            boolean permissionsSet = setPostgreSQLPermissions(pgDataDir);
            if (!permissionsSet) {
                logger.warn("Could not set permissions automatically. You may need to run: sudo chown -R postgres:postgres {}", pgDataDir);
            } else {
                logger.info("Permissions set successfully");
            }

            // Start PostgreSQL
            logger.info("Starting PostgreSQL service...");
            boolean postgresStarted = startPostgreSQL();
            if (!postgresStarted) {
                logger.warn("Could not start PostgreSQL automatically. Please start it manually: sudo systemctl start postgresql");
                result.setSuccess(true);
                result.setMessage("PostgreSQL data directory backup restored successfully to: " + pgDataDir.toString() + 
                                ". Please start PostgreSQL manually: sudo systemctl start postgresql");
            } else {
                logger.info("PostgreSQL started successfully");
                result.setSuccess(true);
                result.setMessage("PostgreSQL data directory backup restored successfully to: " + pgDataDir.toString() + 
                                ". PostgreSQL has been restarted and is ready to use.");
            }

            // Automatically configure database access
            configureDatabaseAccessAfterRestore(databaseType, dbInfo);

        } catch (Exception e) {
            result.setSuccess(false);
            String errorMsg = "Failed to restore data directory backup";
            String targetPath = (pgDataDir != null) ? pgDataDir.toString() : "unknown location";
            
            if (e.getMessage() != null && !e.getMessage().isEmpty() && !e.getMessage().equals(targetPath)) {
                errorMsg += ": " + e.getMessage();
            } else {
                errorMsg += " to " + targetPath;
                if (e.getClass() != Exception.class) {
                    errorMsg += " (" + e.getClass().getSimpleName() + ")";
                }
            }
            
            // Add more context for common exceptions
            if (e instanceof IOException) {
                IOException ioException = (IOException) e;
                String msg = ioException.getMessage();
                if (msg != null) {
                    if (msg.contains("Permission denied") || msg.contains("permission denied")) {
                        errorMsg += "\n\nPermission denied. This may require sudo. Try:\n";
                        if (pgDataDir != null) {
                            errorMsg += "sudo rsync -av " + extractedDataDir + "/ " + pgDataDir + "/\n";
                            errorMsg += "sudo chown -R postgres:postgres " + pgDataDir;
                        } else {
                            errorMsg += "sudo rsync -av <source>/ <target>/";
                        }
                    } else if (msg.contains("No space left") || msg.contains("no space")) {
                        errorMsg += "\n\nDisk space is full. Please free up space and try again.";
                    } else if (msg.contains("File exists") || msg.contains("file exists")) {
                        errorMsg += "\n\nTarget directory already exists and may be in use. Ensure PostgreSQL is stopped.";
                    }
                }
            } else if (e instanceof SecurityException) {
                errorMsg += "\n\nSecurity/permission error. The application may need to run with appropriate permissions.";
            } else if (e instanceof java.nio.file.FileSystemException) {
                errorMsg += "\n\nFile system error. Check disk space and permissions.";
            }
            
            result.setError(errorMsg);
            logger.error("Data directory restore failed for target: {}", targetPath, e);
        }

        return result;
    }

    /**
     * Stop PostgreSQL service
     * Tries systemctl first, then pg_ctl as fallback
     * @param dataDir Optional data directory path for pg_ctl fallback
     */
    private boolean stopPostgreSQL(Path dataDir) {
        // Try sudo systemctl first (requires root privileges)
        // Note: We try sudo even if it might require a password, but use -n flag to fail fast
        try {
            List<String> command = new ArrayList<>();
            command.add("sudo");
            command.add("-n"); // Non-interactive mode - fail if password required
            command.add("systemctl");
            command.add("stop");
            command.add("postgresql");
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.debug("sudo systemctl stop: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("PostgreSQL stopped via sudo systemctl");
                return true;
            } else {
                String errorOutput = output.toString();
                logger.warn("sudo systemctl stop postgresql failed with exit code: {}", exitCode);
                logger.warn("Output: {}", errorOutput);
            }
        } catch (Exception e) {
            logger.error("sudo systemctl stop failed: {}", e.getMessage(), e);
        }

        // Try systemctl without sudo (in case running as root or has permissions)
        // This is what worked previously - systemctl might work without sudo
        try {
            List<String> command = new ArrayList<>();
            command.add("systemctl");
            command.add("stop");
            command.add("postgresql");
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.debug("systemctl stop: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("PostgreSQL stopped via systemctl");
                return true;
            }
        } catch (Exception e) {
            logger.debug("systemctl stop failed: {}", e.getMessage());
        }

        // Try alternative service names with sudo
        String[] serviceNames = {"postgresql@14-main", "postgresql@15-main", "postgresql@16-main", 
                                "postgresql-14", "postgresql-15", "postgresql-16"};
        for (String serviceName : serviceNames) {
            try {
                List<String> command = new ArrayList<>();
                command.add("sudo");
                command.add("-n"); // Non-interactive mode - fail if password required
                command.add("systemctl");
                command.add("stop");
                command.add(serviceName);
                
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                
                Process process = pb.start();
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }
                
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    logger.info("PostgreSQL stopped via sudo systemctl ({})", serviceName);
                    return true;
                }
            } catch (Exception e) {
                logger.debug("sudo systemctl stop {} failed: {}", serviceName, e.getMessage());
            }
        }

        // Try pg_ctl as fallback (works if we know the data directory and have permissions)
        if (dataDir != null && Files.exists(dataDir)) {
            try {
                List<String> command = new ArrayList<>();
                command.add("pg_ctl");
                command.add("stop");
                command.add("-D");
                command.add(dataDir.toAbsolutePath().toString());
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                while (reader.readLine() != null) {
                    // Consume output
                }
            }
            
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    logger.info("PostgreSQL stopped via pg_ctl using data directory: {}", dataDir);
                    return true;
                } else {
                    logger.debug("pg_ctl stop failed with exit code: {}", exitCode);
                }
            } catch (Exception e) {
                logger.debug("pg_ctl stop failed for data directory {}: {}", dataDir, e.getMessage());
            }
        }
        
        // Try pg_ctl with common locations as last resort
        String[] commonDataDirs = {"/var/lib/postgresql/14/main", "/var/lib/postgresql/15/main", 
                                   "/var/lib/postgresql/16/main", "/var/lib/postgresql/data"};
        for (String dataDirPath : commonDataDirs) {
            try {
                Path testPath = Paths.get(dataDirPath);
                if (!Files.exists(testPath) || !Files.exists(testPath.resolve("PG_VERSION"))) {
                    continue; // Skip if doesn't exist or not a valid data directory
                }
                
                List<String> command = new ArrayList<>();
                command.add("pg_ctl");
                command.add("stop");
                command.add("-D");
                command.add(dataDirPath);
                
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                
                Process process = pb.start();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    while (reader.readLine() != null) {
                        // Consume output
                    }
                }
                
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    logger.info("PostgreSQL stopped via pg_ctl using common location: {}", dataDirPath);
                    return true;
                }
            } catch (Exception e) {
                logger.debug("pg_ctl stop failed for {}: {}", dataDirPath, e.getMessage());
            }
        }

        logger.warn("Could not stop PostgreSQL via any command. It may already be stopped, or sudo requires a password.");
        logger.warn("If PostgreSQL is running, you may need to stop it manually: sudo systemctl stop postgresql");
        return false;
    }

    /**
     * Check if PostgreSQL is currently running
     */
    private boolean isPostgreSQLRunning() {
        // Check if PostgreSQL process is running
        try {
            List<String> command = new ArrayList<>();
            command.add("pg_isready");
            command.add("-h");
            command.add("localhost");
            command.add("-p");
            command.add("5432");
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            // If pg_isready is not available, try checking process
            try {
                List<String> command = new ArrayList<>();
                command.add("pgrep");
                command.add("-f");
                command.add("postgres");
                
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                
                Process process = pb.start();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line = reader.readLine();
                    int exitCode = process.waitFor();
                    return exitCode == 0 && line != null && !line.trim().isEmpty();
                }
            } catch (Exception e2) {
                logger.debug("Could not check if PostgreSQL is running: {}", e2.getMessage());
                return false; // Assume not running if we can't check
            }
        }
    }

    /**
     * Set PostgreSQL data directory permissions
     * Sets ownership to postgres:postgres
     */
    private boolean setPostgreSQLPermissions(Path dataDir) {
        // Try chown without sudo first (in case we're running as postgres user)
        try {
            List<String> command = new ArrayList<>();
            command.add("chown");
            command.add("-R");
            command.add("postgres:postgres");
            command.add(dataDir.toAbsolutePath().toString());
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                while (reader.readLine() != null) {
                    // Consume output
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("Permissions set successfully (chown postgres:postgres)");
                return true;
            }
        } catch (Exception e) {
            logger.debug("chown without sudo failed: {}", e.getMessage());
        }

        // Try with sudo
        try {
            List<String> command = new ArrayList<>();
            command.add("sudo");
            command.add("chown");
            command.add("-R");
            command.add("postgres:postgres");
            command.add(dataDir.toAbsolutePath().toString());
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("Permissions set successfully (sudo chown postgres:postgres)");
                return true;
            } else {
                logger.warn("sudo chown failed: {}", output.toString());
            }
        } catch (Exception e) {
            logger.debug("sudo chown failed: {}", e.getMessage());
        }

        logger.warn("Could not set permissions automatically. You may need to run: sudo chown -R postgres:postgres {}", dataDir);
        return false;
    }

    /**
     * Start PostgreSQL service
     * Tries systemctl first, then pg_ctl as fallback
     */
    private boolean startPostgreSQL() {
        // Try sudo systemctl first (requires root privileges)
        try {
            List<String> command = new ArrayList<>();
            command.add("sudo");
            command.add("-n"); // Non-interactive mode - fail if password required
            command.add("systemctl");
            command.add("start");
            command.add("postgresql");
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.debug("sudo systemctl start: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                // Wait a moment for PostgreSQL to start
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                logger.info("PostgreSQL started via sudo systemctl");
                return true;
            } else {
                logger.warn("sudo systemctl start postgresql failed with exit code: {}", exitCode);
                logger.warn("Output: {}", output.toString());
            }
        } catch (Exception e) {
            logger.debug("sudo systemctl start failed: {}", e.getMessage());
        }

        // Try systemctl without sudo (in case running as root or has permissions)
        try {
            List<String> command = new ArrayList<>();
            command.add("systemctl");
            command.add("start");
            command.add("postgresql");
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.debug("systemctl start: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                // Wait a moment for PostgreSQL to start
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                logger.info("PostgreSQL started via systemctl");
                return true;
            } else {
                logger.warn("systemctl start failed: {}", output.toString());
            }
        } catch (Exception e) {
            logger.debug("systemctl start failed: {}", e.getMessage());
        }

        // Try alternative service names with sudo
        String[] serviceNames = {"postgresql@14-main", "postgresql@15-main", "postgresql@16-main",
                                "postgresql-14", "postgresql-15", "postgresql-16"};
        for (String serviceName : serviceNames) {
            try {
                List<String> command = new ArrayList<>();
                command.add("sudo");
                command.add("-n"); // Non-interactive mode - fail if password required
                command.add("systemctl");
                command.add("start");
                command.add(serviceName);
                
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                
                Process process = pb.start();
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }
                
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    // Wait a moment for PostgreSQL to start
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    logger.info("PostgreSQL started via sudo systemctl ({})", serviceName);
                    return true;
                }
            } catch (Exception e) {
                logger.debug("sudo systemctl start {} failed: {}", serviceName, e.getMessage());
            }
        }

        // Try pg_ctl as fallback
        try {
            List<String> command = new ArrayList<>();
            command.add("pg_ctl");
            command.add("start");
            command.add("-D");
            command.add("/var/lib/postgresql/14/main"); // Try common location
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                while (reader.readLine() != null) {
                    // Consume output
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                // Wait a moment for PostgreSQL to start
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                logger.info("PostgreSQL started via pg_ctl");
                return true;
            }
        } catch (Exception e) {
            logger.debug("pg_ctl start failed: {}", e.getMessage());
        }

        logger.warn("Could not start PostgreSQL automatically. Please start it manually: sudo systemctl start postgresql");
        return false;
    }

    /**
     * Get PostgreSQL data directory path
     */
    private Path getPostgreSQLDataDirectory(String databaseType, DatabaseInfo dbInfo) throws IOException {
        // First, check if data directory is explicitly configured
        String configuredDataDir = null;
        if (databaseType.equalsIgnoreCase("blackpearl") && blackpearlDataDirectory != null && !blackpearlDataDirectory.isEmpty()) {
            configuredDataDir = blackpearlDataDirectory;
            logger.info("Using configured BlackPearl data directory: {}", configuredDataDir);
        } else if (databaseType.equalsIgnoreCase("rio") && rioDataDirectory != null && !rioDataDirectory.isEmpty()) {
            configuredDataDir = rioDataDirectory;
            logger.info("Using configured Rio data directory: {}", configuredDataDir);
        }
        
        if (configuredDataDir != null) {
            Path dataDirPath = Paths.get(configuredDataDir);
            if (Files.exists(dataDirPath)) {
                return dataDirPath;
            } else {
                logger.warn("Configured data directory does not exist: {}. Will try to create it or find alternative.", configuredDataDir);
                // Continue to try other methods, but we'll use this as the target
            }
        }

        // Try to query PostgreSQL for data directory (only if PostgreSQL is accessible)
        try {
            List<String> command = new ArrayList<>();
            command.add("psql");
            command.add("-h"); command.add(dbInfo.host);
            command.add("-p"); command.add(String.valueOf(dbInfo.port));
            command.add("-U"); command.add(dbInfo.username);
            command.add("-d"); command.add("postgres"); // Connect to postgres database
            command.add("-t"); // Tuples only
            command.add("-A"); // Unaligned output
            command.add("-c"); command.add("SHOW data_directory;");

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.environment().put("PGPASSWORD", dbInfo.password);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line.trim());
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                String dataDir = output.toString().trim();
                if (!dataDir.isEmpty() && Files.exists(Paths.get(dataDir))) {
                    logger.info("Found PostgreSQL data directory via query: {}", dataDir);
                    return Paths.get(dataDir);
                }
            }
        } catch (Exception e) {
            logger.debug("Could not query PostgreSQL for data directory (PostgreSQL may not be running): {}", e.getMessage());
        }
        
        // If we have a configured directory, use it even if it doesn't exist yet (we'll create it)
        if (configuredDataDir != null) {
            return Paths.get(configuredDataDir);
        }

        // Fallback: Try common PostgreSQL data directory locations
        String[] commonPaths = {
            "/var/lib/postgresql/" + getPostgreSQLVersion() + "/main",
            "/var/lib/postgresql/14/main",
            "/var/lib/postgresql/15/main",
            "/var/lib/postgresql/16/main",
            "/var/lib/postgresql/data",
            "/usr/local/pgsql/data",
            "/opt/postgresql/data",
            "/usr/local/var/postgres",
            System.getProperty("user.home") + "/postgres/data"
        };

        // First, try to find existing data directories
        for (String pathStr : commonPaths) {
            Path path = Paths.get(pathStr);
            if (Files.exists(path) && Files.exists(path.resolve("PG_VERSION"))) {
                logger.info("Found PostgreSQL data directory at common location: {}", pathStr);
                return path;
            }
        }

        // If not found, try to find any PostgreSQL data directory by searching for PG_VERSION
        logger.info("Searching for PostgreSQL data directories...");
        Path foundDataDir = null;
        for (String pathStr : commonPaths) {
            Path path = Paths.get(pathStr);
            if (Files.exists(path)) {
                // Check parent directories too
                Path parent = path.getParent();
                if (parent != null && Files.exists(parent)) {
                    try {
                        List<Path> subdirs = Files.list(parent)
                            .filter(p -> Files.isDirectory(p) && Files.exists(p.resolve("PG_VERSION")))
                            .collect(Collectors.toList());
                        if (!subdirs.isEmpty()) {
                            foundDataDir = subdirs.get(0);
                            logger.info("Found PostgreSQL data directory: {}", foundDataDir);
                            return foundDataDir;
                        }
                    } catch (IOException e) {
                        // Ignore and continue
                    }
                }
            }
        }

        // If we still don't have a configured directory, try to use a reasonable default
        // Use the first common path that we can create
        for (String pathStr : commonPaths) {
            Path path = Paths.get(pathStr);
            Path parent = path.getParent();
            if (parent != null && (Files.exists(parent) || parent.toFile().canWrite())) {
                logger.info("Will use default PostgreSQL data directory location: {}", pathStr);
                return path;
            }
        }

        logger.warn("Could not determine PostgreSQL data directory. Please configure it explicitly.");
        return null;
    }

    /**
     * Get PostgreSQL version (for determining data directory path)
     */
    private String getPostgreSQLVersion() {
        // Try to detect PostgreSQL version
        try {
            List<String> command = new ArrayList<>();
            command.add("psql");
            command.add("--version");
            
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    // Extract version number (e.g., "psql (PostgreSQL) 14.5" -> "14")
                    String[] parts = line.split(" ");
                    for (String part : parts) {
                        if (part.matches("\\d+\\.\\d+")) {
                            process.waitFor();
                            return part.split("\\.")[0]; // Return major version
                        }
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            logger.debug("Could not detect PostgreSQL version: {}", e.getMessage());
        }
        
        return "14"; // Default to version 14
    }

    /**
     * Copy directory recursively using rsync with sudo (for protected directories)
     * Returns true if successful, false otherwise
     */
    private boolean copyDirectoryWithPermissions(Path source, Path target) {
        try {
            // Try rsync with sudo first (best for preserving permissions)
            // Use -n flag to fail fast if password is required
            List<String> command = new ArrayList<>();
            command.add("sudo");
            command.add("-n"); // Non-interactive - fail if password required
            command.add("rsync");
            command.add("-av");
            command.add("--delete");
            command.add(source.toAbsolutePath().toString() + "/");
            command.add(target.toAbsolutePath().toString() + "/");

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.debug("rsync: {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("Successfully copied data directory using rsync with sudo");
                return true;
            } else {
                String errorOutput = output.toString();
                logger.warn("rsync with sudo failed (exit code {}): {}", exitCode, errorOutput);
                // Check if it's a password issue
                if (errorOutput.contains("password") || errorOutput.contains("sudo") || errorOutput.contains("a password is required")) {
                    logger.warn("sudo requires password. Cannot use rsync with sudo automatically.");
                }
                // Check if it's a permission issue
                if (errorOutput.contains("Permission denied") || errorOutput.contains("permission denied")) {
                    logger.warn("Permission denied when trying to copy with sudo rsync.");
                }
            }
        } catch (Exception e) {
            logger.warn("rsync with sudo failed with exception: {}", e.getMessage());
            if (e.getMessage() != null && (e.getMessage().contains("password") || e.getMessage().contains("sudo"))) {
                logger.warn("sudo requires password. Cannot use rsync with sudo automatically.");
            }
        }

        // Try rsync without sudo (in case running as postgres user)
        try {
            List<String> command = new ArrayList<>();
            command.add("rsync");
            command.add("-av");
            command.add("--delete");
            command.add(source.toAbsolutePath().toString() + "/");
            command.add(target.toAbsolutePath().toString() + "/");

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.debug("rsync: {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("Successfully copied data directory using rsync");
                return true;
            } else {
                logger.debug("rsync without sudo failed (exit code {}): {}", exitCode, output.toString());
            }
        } catch (Exception e) {
            logger.debug("rsync without sudo failed: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Copy directory recursively (fallback method using Java Files API)
     */
    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(sourcePath -> {
            try {
                Path targetPath = target.resolve(source.relativize(sourcePath));
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy " + sourcePath + " to " + target, e);
            }
        });
    }

    /**
     * Clean up old backup directories, keeping only the most recent N backups
     * @param backupParentDir The parent directory containing backup directories
     * @param dataDirName The name of the data directory (used to identify backup directories)
     */
    private void cleanupOldBackups(Path backupParentDir, String dataDirName) {
        try {
            if (!Files.exists(backupParentDir) || !Files.isDirectory(backupParentDir)) {
                logger.debug("Backup parent directory does not exist: {}", backupParentDir);
                return;
            }

            // Pattern: {dataDirName}_backup_{timestamp}
            String backupPrefix = dataDirName + "_backup_";
            
            // Find all backup directories matching the pattern
            List<Path> backupDirs = Files.list(backupParentDir)
                .filter(path -> {
                    String dirName = path.getFileName().toString();
                    return dirName.startsWith(backupPrefix) && Files.isDirectory(path);
                })
                .sorted((p1, p2) -> {
                    // Sort by timestamp (extracted from directory name) - newest first
                    try {
                        String name1 = p1.getFileName().toString();
                        String name2 = p2.getFileName().toString();
                        String timestamp1 = name1.substring(backupPrefix.length());
                        String timestamp2 = name2.substring(backupPrefix.length());
                        return Long.compare(Long.parseLong(timestamp2), Long.parseLong(timestamp1));
                    } catch (Exception e) {
                        // If timestamp parsing fails, compare by modification time
                        try {
                            return Long.compare(
                                Files.getLastModifiedTime(p2).toMillis(),
                                Files.getLastModifiedTime(p1).toMillis()
                            );
                        } catch (IOException ioException) {
                            return 0;
                        }
                    }
                })
                .collect(Collectors.toList());

            if (backupDirs.size() <= maxBackupsToKeep) {
                logger.debug("Only {} backup(s) found, keeping all (limit: {})", backupDirs.size(), maxBackupsToKeep);
                return;
            }

            // Keep the most recent N backups, delete the rest
            List<Path> backupsToDelete = backupDirs.subList(maxBackupsToKeep, backupDirs.size());
            logger.info("Found {} backup(s), keeping {} most recent, deleting {} old backup(s)", 
                       backupDirs.size(), maxBackupsToKeep, backupsToDelete.size());

            for (Path backupDir : backupsToDelete) {
                try {
                    logger.info("Deleting old backup: {}", backupDir.getFileName());
                    deleteDirectoryRecursively(backupDir);
                    logger.info("Successfully deleted old backup: {}", backupDir.getFileName());
                } catch (Exception e) {
                    logger.warn("Failed to delete old backup {}: {}", backupDir.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to clean up old backups: {}", e.getMessage());
        }
    }

    /**
     * Recursively delete a directory and all its contents
     */
    private void deleteDirectoryRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        // Try using system commands first (more efficient for large directories)
        try {
            // Try rm -rf with sudo first
            List<String> command = new ArrayList<>();
            command.add("sudo");
            command.add("rm");
            command.add("-rf");
            command.add(directory.toAbsolutePath().toString());

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.debug("Successfully deleted directory using sudo rm -rf: {}", directory);
                return;
            }
        } catch (Exception e) {
            logger.debug("sudo rm -rf failed, trying without sudo: {}", e.getMessage());
        }

        // Fallback: try without sudo
        try {
            List<String> command = new ArrayList<>();
            command.add("rm");
            command.add("-rf");
            command.add(directory.toAbsolutePath().toString());

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.debug("Successfully deleted directory using rm -rf: {}", directory);
                return;
            }
        } catch (Exception e) {
            logger.debug("rm -rf failed, using Java Files API: {}", e.getMessage());
        }

        // Final fallback: use Java Files API
        Files.walk(directory)
            .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    logger.warn("Failed to delete {}: {}", path, e.getMessage());
                }
            });
    }

    /**
     * Create PostgreSQL database if it doesn't exist
     */
    private void createDatabaseIfNotExists(DatabaseInfo dbInfo) {
        try {
            // Connect to postgres database to check/create target database
            List<String> command = new ArrayList<>();
            command.add("psql");
            command.add("-h"); command.add(dbInfo.host);
            command.add("-p"); command.add(String.valueOf(dbInfo.port));
            command.add("-U"); command.add(dbInfo.username);
            command.add("-d"); command.add("postgres"); // Connect to default postgres database
            command.add("-tc"); // Single command, no headers
            command.add("SELECT 1 FROM pg_database WHERE datname = '" + dbInfo.database + "'");
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.environment().put("PGPASSWORD", dbInfo.password);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            String result = output.toString().trim();
            
            // If database doesn't exist (result is empty or doesn't contain "1"), create it
            if (exitCode == 0 && (result.isEmpty() || !result.contains("1"))) {
                logger.info("Database {} does not exist, creating it...", dbInfo.database);
                
                // Create database
                List<String> createCommand = new ArrayList<>();
                createCommand.add("psql");
                createCommand.add("-h"); createCommand.add(dbInfo.host);
                createCommand.add("-p"); createCommand.add(String.valueOf(dbInfo.port));
                createCommand.add("-U"); createCommand.add(dbInfo.username);
                createCommand.add("-d"); createCommand.add("postgres");
                createCommand.add("-c"); createCommand.add("CREATE DATABASE \"" + dbInfo.database + "\"");
                
                ProcessBuilder createPb = new ProcessBuilder(createCommand);
                createPb.environment().put("PGPASSWORD", dbInfo.password);
                createPb.redirectErrorStream(true);
                
                Process createProcess = createPb.start();
                StringBuilder createOutput = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(createProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        createOutput.append(line).append("\n");
                    }
                }
                
                int createExitCode = createProcess.waitFor();
                if (createExitCode == 0) {
                    logger.info("Database {} created successfully", dbInfo.database);
                } else {
                    logger.warn("Failed to create database {}: {}", dbInfo.database, createOutput.toString());
                }
            } else if (exitCode == 0 && result.contains("1")) {
                logger.info("Database {} already exists", dbInfo.database);
            } else {
                logger.warn("Could not check if database {} exists: {}", dbInfo.database, output.toString());
            }
        } catch (Exception e) {
            logger.warn("Could not create database {} (it may already exist): {}", dbInfo.database, e.getMessage());
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
