# PostgreSQL Database Restore Guide

## Overview

The Migration Tracker supports uploading and restoring BlackPearl and Rio PostgreSQL database backups directly through the web interface. This allows you to set up the databases needed for bucket selection and data gathering without manual command-line operations.

## Quick Start

1. **Access the Restore Page**
   - Navigate to "Restore PostgreSQL" in the sidebar
   - Or go directly to: `http://your-server:8080/database/postgres-restore`

2. **Select Database Type**
   - Choose "BlackPearl" or "Rio" from the dropdown

3. **Upload Your Backup**
   - Click the upload area or drag and drop your backup file
   - Supported formats: `.dump`, `.sql`, `.tar`, `.tar.gz`, `.zip`
   - Maximum file size: 2GB

4. **Automatic Restore & Configuration**
   - The system will automatically restore the database to localhost PostgreSQL (or configured server)
   - **Connection settings are automatically configured** - no need to provide credentials separately!
   - After successful restore, you can immediately use bucket selection and data gathering features

## Supported File Formats

### Recommended Formats
- **`.dump`** - PostgreSQL custom format dump (created with `pg_dump -Fc`)
  - Best for: Full database backups with all metadata
  - Restored using: `pg_restore`

- **`.sql`** - SQL script dump (created with `pg_dump`)
  - Best for: Plain SQL backups, easy to inspect
  - Restored using: `psql`

### Archive Formats
- **`.zip`** - ZIP archive containing `.dump` or `.sql` file
- **`.tar.gz`** or **`.tgz`** - Compressed TAR archive
- **`.tar`** - TAR archive (Note: TAR archives may require manual setup for BlackPearl)

## Prerequisites

### 1. PostgreSQL Client Tools
The restore process requires PostgreSQL client tools to be installed on the server:

```bash
# Ubuntu/Debian
sudo apt-get install postgresql-client

# CentOS/RHEL
sudo yum install postgresql

# macOS
brew install postgresql
```

### 2. Database Connection Configuration

**Automatic Configuration (Recommended):**
When you restore a database backup, the connection settings are **automatically configured** and saved to a `.env` file. You don't need to provide credentials separately!

- For localhost restores: Uses default `postgres` user (no password required if configured)
- Connection settings are saved to `.env` file in the project root
- The tool can immediately access the database after restore

**Manual Configuration (Optional):**
If you need to connect to a remote database or use custom credentials, you can configure them:

**Via Environment Variables:**
```bash
# BlackPearl Database
export MT_BLACKPEARL_HOST=your-blackpearl-host
export MT_BLACKPEARL_PORT=5432
export MT_BLACKPEARL_DATABASE=tapesystem
export MT_BLACKPEARL_USERNAME=postgres
export MT_BLACKPEARL_PASSWORD=your-password

# Rio Database
export MT_RIO_HOST=your-rio-host
export MT_RIO_PORT=5432
export MT_RIO_DATABASE=rio_db
export MT_RIO_USERNAME=postgres
export MT_RIO_PASSWORD=your-password
```

**Or in `application.yml`:**
```yaml
postgres:
  blackpearl:
    host: your-blackpearl-host
    port: 5432
    database: tapesystem
    username: postgres
    password: your-password
  rio:
    host: your-rio-host
    port: 5432
    database: rio_db
    username: postgres
    password: your-password
```

### 3. Database Access
- The configured user must have permissions to:
  - Create/drop databases (for restore)
  - Connect to the target database
  - Create tables and insert data

## How It Works

1. **File Upload**: The backup file is uploaded to the server
2. **Extraction**: If the file is an archive, it's automatically extracted
3. **Format Detection**: The system detects the backup format (.dump, .sql, etc.)
4. **Database Restore**: 
   - For `.dump` files: Uses `pg_restore` with `--clean --if-exists`
   - For `.sql` files: Uses `psql` to execute the SQL script
   - Restores to localhost PostgreSQL by default (or configured server)
5. **Automatic Configuration**: 
   - Connection settings are automatically saved to `.env` file
   - For localhost: Uses default `postgres` user (no password needed)
   - For remote: Saves the connection credentials you used for restore
6. **Ready to Use**: The application can immediately connect and query buckets - no separate credential setup needed!

## Creating Backups

### Option 1: Custom Format Dump (Recommended)
```bash
# BlackPearl
pg_dump -h blackpearl-server -U postgres -Fc tapesystem > blackpearl_backup.dump

# Rio
pg_dump -h rio-server -U postgres -Fc rio_db > rio_backup.dump
```

### Option 2: SQL Script Dump
```bash
# BlackPearl
pg_dump -h blackpearl-server -U postgres tapesystem > blackpearl_backup.sql

# Rio
pg_dump -h rio-server -U postgres rio_db > rio_backup.sql
```

### Option 3: Compressed Archive
```bash
# Create dump and compress
pg_dump -h blackpearl-server -U postgres -Fc tapesystem | gzip > blackpearl_backup.dump.gz

# Or create ZIP
pg_dump -h blackpearl-server -U postgres -Fc tapesystem > blackpearl_backup.dump
zip blackpearl_backup.zip blackpearl_backup.dump
```

## Troubleshooting

### "pg_restore: command not found" or "psql: command not found"
**Problem:** PostgreSQL client tools are not installed.

**Solution:**
```bash
# Install PostgreSQL client tools (see Prerequisites section)
sudo apt-get install postgresql-client  # Ubuntu/Debian
```

### "Failed to connect to database"
**Problem:** Database connection settings are incorrect or database doesn't exist.

**Solution:**
1. Verify connection settings in `.env` or `application.yml`
2. Test connection manually:
   ```bash
   psql -h your-host -U postgres -d tapesystem
   ```
3. Create database if it doesn't exist:
   ```bash
   psql -h your-host -U postgres -c "CREATE DATABASE tapesystem;"
   ```

### "Permission denied" or "Access denied"
**Problem:** Database user doesn't have required permissions.

**Solution:**
```bash
# Grant necessary permissions
psql -h your-host -U postgres
GRANT ALL PRIVILEGES ON DATABASE tapesystem TO postgres;
ALTER USER postgres WITH SUPERUSER;
```

### "TAR archive restore requires system-level PostgreSQL setup"
**Problem:** TAR archives (especially BlackPearl format) require special handling.

**Solution:**
- Use `.dump` or `.sql` format instead
- Or use the `unpack_database` script manually:
  ```bash
  ./bin/migration_tracker unpack-database --type blackpearl --file backup.tar
  ```

### "File too large"
**Problem:** Backup file exceeds 2GB limit.

**Solution:**
- Compress the backup file before uploading
- Or increase the upload limit in the application configuration

## Security Notes

- Database credentials are stored in environment variables or configuration files
- Backup files are temporarily stored on the server during restore
- Ensure proper file permissions on uploaded files
- Consider adding authentication for production deployments
- Backup files are automatically deleted after restore

## API Endpoints

### Restore PostgreSQL Database
```
POST /api/database/restore-postgres
Content-Type: multipart/form-data
Body: 
  - file: (multipart file)
  - databaseType: "blackpearl" | "rio"
```

### Response
```json
{
  "success": true,
  "message": "Database restored successfully from .dump file",
  "filename": "blackpearl_backup.dump",
  "databaseType": "blackpearl",
  "format": "custom"
}
```

## Example Workflow

### 1. Create Backup on Source Server
```bash
# On BlackPearl server
pg_dump -U postgres -Fc tapesystem > /tmp/blackpearl_backup.dump
```

### 2. Transfer to Migration Tracker Server
```bash
# Copy to tracker server
scp /tmp/blackpearl_backup.dump user@tracker-server:/tmp/
```

### 3. Restore via Web Interface
- Navigate to "Restore PostgreSQL" page
- Select "BlackPearl" from dropdown
- Upload `blackpearl_backup.dump`
- Wait for restore to complete

### 4. Verify Restore
- Go to "Gather Data" page
- Click "Show Buckets"
- You should see buckets from the restored database

## Automatic Configuration Feature

**No Credentials Needed!** When you restore a database backup:

1. **Automatic Setup**: Connection settings are automatically saved to `.env` file
2. **Localhost Default**: Restores to `localhost:5432` with `postgres` user (no password)
3. **Immediate Access**: After restore, the tool can immediately access the database
4. **No Manual Configuration**: You don't need to provide BlackPearl/Rio credentials separately

The `.env` file is automatically created/updated in your project root with entries like:
```bash
# BlackPearl Database
export MT_BLACKPEARL_HOST=localhost
export MT_BLACKPEARL_PORT=5432
export MT_BLACKPEARL_DATABASE=tapesystem
export MT_BLACKPEARL_USERNAME=postgres
export MT_BLACKPEARL_PASSWORD=""
```

**Note**: If the application is already running, you may need to restart it to pick up the new configuration, or source the `.env` file:
```bash
source .env
```

## Integration with Bucket Selection

After restoring a PostgreSQL database:

1. **BlackPearl Database**: Enables querying BlackPearl buckets for migration tracking
2. **Rio Database**: Enables querying Rio buckets for migration tracking
3. **Bucket Selection**: You can now select specific buckets when gathering migration data
4. **Data Gathering**: The tool can query object counts and sizes from the restored databases
5. **No Credentials Required**: Connection is automatically configured - just upload and go!

## Best Practices

1. **Use Custom Format (.dump)**: Best compression and fastest restore
2. **Regular Backups**: Create backups before major changes
3. **Test Restores**: Test restore process with non-production data
4. **Version Control**: Name backups with dates: `blackpearl_backup_20250112.dump`
5. **Verify Data**: After restore, verify buckets are accessible via the UI
6. **Keep Multiple Backups**: Don't delete old backups immediately
7. **Secure Storage**: Store backups securely, especially if they contain sensitive data

## Differences from Tracker Database Restore

- **Tracker Database**: SQLite database for the migration tracker application itself
- **PostgreSQL Databases**: BlackPearl and Rio databases for bucket/object data
- **Purpose**: PostgreSQL restore enables data gathering features, while tracker restore restores your migration tracking data

## Related Documentation

- `DATABASE_RESTORE.md` - SQLite tracker database restore
- `POSTGRESQL_SETUP.md` - PostgreSQL connection setup
- `CONFIGURE_REMOTE_DATABASES.md` - Remote database configuration
- `BUCKET_SELECTION_WORKFLOW.md` - Using bucket selection after restore
