# Database Restore Guide

## Overview

The Migration Tracker now supports uploading and restoring database backups directly through the web interface. This makes it easy to restore your migration data from a backup file without manual file operations.

## Quick Start

1. **Access the Restore Page**
   - Navigate to "Restore Database" in the sidebar
   - Or go directly to: `http://your-server:8080/database/restore`

2. **Upload Your Backup**
   - Click the upload area or drag and drop your backup file
   - Supported formats: `.db`, `.zip`, `.tar.gz`, `.gz`, `.tar`
   - Maximum file size: 500MB

3. **Automatic Restore**
   - The system will automatically:
     - Extract the database file (if zipped)
     - Backup your existing database
     - Restore the new database
     - Reload the application

## Supported File Formats

### Direct Database File
- **`.db`** - SQLite database file (direct upload)

### Archive Formats
- **`.zip`** - ZIP archive containing `migrations.db`
- **`.tar.gz`** or **`.tgz`** - Compressed TAR archive
- **`.gz`** - GZIP compressed file
- **`.tar`** - TAR archive

## How It Works

1. **File Upload**: The backup file is uploaded to the server
2. **Extraction**: If the file is an archive, it's automatically extracted
3. **Database Detection**: The system searches for `migrations.db` in the archive
4. **Backup**: Your existing database is automatically backed up with a timestamp
5. **Restore**: The new database replaces the current one
6. **Reload**: The page automatically reloads to show the new data

## Backup Location

When you restore a database, your existing database is automatically backed up to:
```
resources/database/migrations_backup_[timestamp].db
```

## Creating a Backup

### Option 1: Using the Backup Script
```bash
# From the project root
./resources/scripts/backup [backup-name]
```

### Option 2: Manual Backup
```bash
# Copy the database file
cp resources/database/migrations.db migrations_backup_$(date +%Y%m%d).db

# Or create a ZIP archive
zip migrations_backup_$(date +%Y%m%d).zip resources/database/migrations.db
```

### Option 3: Using SQLite Backup Command
```bash
sqlite3 resources/database/migrations.db ".backup migrations_backup_$(date +%Y%m%d).db"
```

## Troubleshooting

### "No database file found in archive"
- Make sure your ZIP file contains `migrations.db` (not in a subdirectory)
- The database file should be named `migrations.db` (case-sensitive)

### "File too large"
- Maximum file size is 500MB
- Compress your backup file if it's larger
- Consider using `.tar.gz` for better compression

### "Failed to restore database"
- Check server logs: `log/migration_tracker_api.log`
- Ensure the database directory is writable
- Verify the uploaded file is not corrupted

### Database Not Reloading
- The page should automatically reload after 2 seconds
- If it doesn't, manually refresh the page
- Check browser console for errors

## Security Notes

- Database restore requires access to the web interface
- Consider adding authentication for production deployments
- Backups are stored in the same directory as the database
- Ensure proper file permissions on the database directory

## API Endpoints

### Restore Database
```
POST /api/database/restore
Content-Type: multipart/form-data
Body: file (multipart file)
```

### Get Database Info
```
GET /api/database/info
Response: { path, exists, size, lastModified }
```

## Example: Restoring from Production

1. **On Production Server**
   ```bash
   # Create a backup
   cd /home/seans/migration_tracker-v0.6.2
   zip migrations_backup_$(date +%Y%m%d).zip resources/database/migrations.db
   ```

2. **Transfer to New Server**
   ```bash
   # Copy to new tracker
   scp migrations_backup_20250112.zip user@new-server:/tmp/
   ```

3. **Restore via Web Interface**
   - Navigate to Restore Database page
   - Upload the ZIP file
   - Wait for automatic restore and reload

## Integration with Deployment

The database restore feature works seamlessly with the deployment process:

1. **Initial Setup**: Upload a backup during initial deployment
2. **Updates**: Restore from backup after updates
3. **Migration**: Move data between installations

## Best Practices

1. **Regular Backups**: Create backups before major changes
2. **Test Restores**: Test restore process with non-production data
3. **Version Control**: Name backups with dates: `migrations_backup_20250112.zip`
4. **Verify Data**: After restore, verify critical data is present
5. **Keep Multiple Backups**: Don't delete old backups immediately
