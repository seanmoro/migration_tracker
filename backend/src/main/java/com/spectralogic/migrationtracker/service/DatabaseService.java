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
public class DatabaseService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);
    
    private final String databasePath;

    public DatabaseService(@Value("${migration.tracker.database.path:}") String configuredDbPath) {
        
        // Resolve database path similar to DatabaseConfig
        if (configuredDbPath != null && !configuredDbPath.isEmpty()) {
            this.databasePath = new File(configuredDbPath).getAbsolutePath();
        } else {
            // Try common locations
            File dbFile1 = new File("../resources/database/migrations.db");
            if (dbFile1.exists()) {
                this.databasePath = dbFile1.getAbsolutePath();
            } else {
                File dbFile2 = new File("resources/database/migrations.db");
                this.databasePath = dbFile2.getAbsolutePath();
            }
        }
        
        logger.info("Database service initialized with path: {}", this.databasePath);
    }

    /**
     * Restore database from uploaded backup file
     * Supports: .db, .zip, .tar.gz, .gz, .zst
     */
    public String restoreDatabase(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("File name is missing");
        }

        logger.info("Restoring database from file: {}", originalFilename);

        // Create temp directory for extraction
        Path tempDir = Files.createTempDirectory("db-restore-");
        Path uploadedFile = tempDir.resolve(originalFilename);

        try {
            // Save uploaded file
            File targetFile = uploadedFile.toFile();
            if (targetFile == null) {
                throw new IOException("Failed to create target file");
            }
            file.transferTo(targetFile);
            logger.debug("Saved uploaded file to: {}", uploadedFile);

            // Extract and find database file
            Path extractedDb = extractDatabaseFile(uploadedFile, tempDir);
            
            if (extractedDb == null) {
                throw new IOException("No database file (.db) found in the uploaded archive");
            }

            logger.info("Found database file: {}", extractedDb);

            // Backup existing database if it exists
            File currentDb = new File(databasePath);
            if (currentDb.exists()) {
                String backupName = "migrations_backup_" + System.currentTimeMillis() + ".db";
                Path backupPath = currentDb.toPath().getParent().resolve(backupName);
                Files.copy(currentDb.toPath(), backupPath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Backed up existing database to: {}", backupName);
            }

            // Ensure parent directory exists
            File dbFile = new File(databasePath);
            dbFile.getParentFile().mkdirs();

            // Copy extracted database to target location
            Files.copy(extractedDb, Paths.get(databasePath), StandardCopyOption.REPLACE_EXISTING);
            logger.info("Database restored successfully to: {}", databasePath);

            return "Database restored successfully. Backup saved as: " + 
                   (currentDb.exists() ? new File(currentDb.getParent(), 
                       "migrations_backup_" + System.currentTimeMillis() + ".db").getName() : "N/A");

        } finally {
            // Clean up temp directory
            deleteDirectory(tempDir.toFile());
        }
    }

    /**
     * Extract database file from archive
     */
    private Path extractDatabaseFile(Path archiveFile, Path extractDir) throws IOException {
        String filename = archiveFile.getFileName().toString().toLowerCase();

        // If it's already a .db file, return it
        if (filename.endsWith(".db")) {
            return archiveFile;
        }

        // Handle ZIP files
        if (filename.endsWith(".zip")) {
            return extractFromZip(archiveFile, extractDir);
        }

        // Handle TAR.GZ files
        if (filename.endsWith(".tar.gz") || filename.endsWith(".tgz")) {
            return extractFromTarGz(archiveFile, extractDir);
        }

        // Handle GZ files (single file compressed)
        if (filename.endsWith(".gz")) {
            return extractFromGz(archiveFile, extractDir);
        }

        // Handle TAR files
        if (filename.endsWith(".tar")) {
            return extractFromTar(archiveFile, extractDir);
        }

        // Handle ZST files (Zstandard)
        if (filename.endsWith(".zst")) {
            return extractFromZst(archiveFile, extractDir);
        }

        throw new IOException("Unsupported archive format. Supported: .zip, .tar.gz, .tar, .gz, .zst, .db");
    }

    private Path extractFromZip(Path zipFile, Path extractDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;
            Path dbFile = null;

            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = extractDir.resolve(entry.getName());
                
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    
                    try (FileOutputStream fos = new FileOutputStream(entryPath.toFile())) {
                        zis.transferTo(fos);
                    }
                    
                    // Look for .db file
                    if (entry.getName().toLowerCase().endsWith(".db")) {
                        dbFile = entryPath;
                    }
                }
                zis.closeEntry();
            }

            return dbFile;
        }
    }

    private Path extractFromTarGz(Path tarGzFile, Path extractDir) throws IOException {
        // For TAR.GZ, we'll use a simple approach: try to find .db in the archive
        // Note: Full TAR parsing would require Apache Commons Compress, but we'll use a simpler approach
        // For now, we'll extract using system commands if available, otherwise throw an error
        throw new IOException("TAR.GZ extraction requires additional dependencies. Please use .zip or .db format.");
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
        
        // If the extracted file is a .db, return it
        if (baseName.toLowerCase().endsWith(".db")) {
            return outputFile;
        }
        
        // Otherwise, recursively try to extract
        return extractDatabaseFile(outputFile, extractDir);
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
                // If the extracted file is a .db, return it
                if (baseName.toLowerCase().endsWith(".db")) {
                    return outputFile;
                }
                // Otherwise, recursively try to extract
                return extractDatabaseFile(outputFile, extractDir);
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

    private Path extractFromTar(Path tarFile, Path extractDir) throws IOException {
        // Full TAR parsing requires Apache Commons Compress
        throw new IOException("TAR extraction requires additional dependencies. Please use .zip or .db format.");
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

    /**
     * Get database file info
     */
    public DatabaseInfo getDatabaseInfo() {
        File dbFile = new File(databasePath);
        DatabaseInfo info = new DatabaseInfo();
        info.setPath(databasePath);
        info.setExists(dbFile.exists());
        if (dbFile.exists()) {
            info.setSize(dbFile.length());
            info.setLastModified(dbFile.lastModified());
        }
        return info;
    }

    public static class DatabaseInfo {
        private String path;
        private boolean exists;
        private long size;
        private long lastModified;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public boolean isExists() {
            return exists;
        }

        public void setExists(boolean exists) {
            this.exists = exists;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        public long getLastModified() {
            return lastModified;
        }

        public void setLastModified(long lastModified) {
            this.lastModified = lastModified;
        }
    }
}
