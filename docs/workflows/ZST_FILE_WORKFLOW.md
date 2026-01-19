# Workflow: Restoring PostgreSQL from .zst Files

## Overview

`.zst` files are Zstandard compressed backups that can contain:
- PostgreSQL data directory backups (direct file copy)
- PostgreSQL dump files (`.dump` or `.sql`)
- TAR archives containing backup files

The system automatically detects and handles all these formats.

## Prerequisites

### 1. Install Zstandard Tool

```bash
# Ubuntu/Debian
sudo apt-get install zstd

# CentOS/RHEL
sudo yum install zstd

# macOS
brew install zstd

# Verify installation
zstd --version
```

### 2. Ensure PostgreSQL is Running

```bash
# Check PostgreSQL status
sudo systemctl status postgresql

# Start if needed
sudo systemctl start postgresql
```

## Workflow Steps

### Step 1: Access Restore Page

1. Open the Migration Tracker web interface
2. Navigate to **"Restore PostgreSQL"** in the sidebar
3. Or go directly to: `http://your-server/database/postgres-restore`

### Step 2: Select Database Type

- Choose **"BlackPearl"** or **"Rio"** from the dropdown
- This determines which database the backup will be restored to

### Step 3: Upload .zst File

1. Click the upload area or drag and drop your `.zst` file
2. Supported file: `backup.tar.zst` or `backup.dump.zst` or `backup.sql.zst`
3. Maximum file size: 2GB (configurable)

### Step 4: Automatic Processing

The system automatically:

1. **Decompresses** the `.zst` file using `zstd -d`
2. **Detects** the format of the decompressed file:
   - If `.tar` → Extracts and searches for backup files
   - If `.dump` → Restores using `pg_restore`
   - If `.sql` → Restores using `psql`
   - If data directory structure → Copies to PostgreSQL data directory
3. **Stops PostgreSQL** (for data directory backups)
4. **Restores** the database
5. **Sets permissions** (for data directory backups)
6. **Starts PostgreSQL** (for data directory backups)

### Step 5: Wait for Completion

- Progress is shown in the UI
- A success message appears when complete
- Any errors are displayed with details

## File Format Detection

The system handles these `.zst` file scenarios:

### Scenario 1: `.tar.zst` (TAR Archive)
```
backup.tar.zst
  → zstd -d → backup.tar
  → tar -xvf → finds .sql or .dump files
  → restores using psql or pg_restore
```

### Scenario 2: `.dump.zst` (PostgreSQL Custom Format)
```
backup.dump.zst
  → zstd -d → backup.dump
  → pg_restore → database restored
```

### Scenario 3: `.sql.zst` (SQL Script)
```
backup.sql.zst
  → zstd -d → backup.sql
  → psql → database restored
```

### Scenario 4: Data Directory Backup
```
data_directory.tar.zst
  → zstd -d → data_directory.tar
  → tar -xvf → PostgreSQL data directory structure
  → Detects PG_VERSION, base/, global/, pg_wal/ directories
  → Stops PostgreSQL
  → Copies to /var/lib/postgresql/16/main (or configured path)
  → Sets permissions (chown postgres:postgres)
  → Starts PostgreSQL
```

## Data Directory Backup Workflow

If your `.zst` file contains a PostgreSQL data directory:

### Automatic Steps:
1. System detects data directory structure (looks for `PG_VERSION`, `base/`, `global/`, `pg_wal/`)
2. Determines target data directory:
   - Queries PostgreSQL: `SHOW data_directory`
   - Or uses configured path from `application.yml`
   - Or falls back to common paths: `/var/lib/postgresql/16/main`
3. **Stops PostgreSQL** service
4. **Backs up** existing data directory (if it exists)
5. **Copies** restored data to target directory
6. **Sets permissions**: `chown -R postgres:postgres /var/lib/postgresql/16/main`
7. **Starts PostgreSQL** service

### Configuration (Optional)

You can configure the data directory paths in `application.yml`:

```yaml
postgres:
  blackpearl:
    data-directory: /var/lib/postgresql/16/main
  rio:
    data-directory: /var/lib/postgresql/16/main
```

Or via environment variables:
```bash
export MT_BLACKPEARL_DATA_DIRECTORY=/var/lib/postgresql/16/main
export MT_RIO_DATA_DIRECTORY=/var/lib/postgresql/16/main
```

## Verification

After restore completes:

### 1. Check Database Connection

```bash
# Test BlackPearl connection
psql -h localhost -p 5432 -U postgres -d tapesystem -c "SELECT version();"

# Test Rio connection
psql -h localhost -p 5432 -U postgres -d rio_db -c "SELECT version();"
```

### 2. Verify Buckets are Available

1. Go to **"Gather Data"** page
2. Click **"Show Buckets"**
3. You should see buckets listed (if the database has bucket data)

### 3. Check Application Logs

```bash
# Check restore logs
sudo tail -100 log/application.log | grep -i "restore\|zst\|decompress"

# Check for errors
sudo tail -100 log/application.log | grep -i "error\|exception"
```

## Troubleshooting

### "zstd: command not found"
**Solution:** Install zstd tool (see Prerequisites)

### "Failed to decompress .zst file"
**Possible causes:**
- File is corrupted
- File is not actually a .zst file
- Insufficient disk space

**Check:**
```bash
# Verify file is actually .zst
file backup.tar.zst

# Check disk space
df -h
```

### "Permission denied" during data directory restore
**Solution:** The application needs to run with `sudo` or have passwordless sudo configured:

```bash
# Run application with sudo
sudo -E nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
    --server.port=80 \
    --server.address=0.0.0.0 \
    > log/application.log 2>&1 &
```

Or configure passwordless sudo:
```bash
# Add to /etc/sudoers.d/migration-tracker
your-user ALL=(ALL) NOPASSWD: /usr/bin/systemctl stop postgresql, /usr/bin/systemctl start postgresql, /usr/bin/chown, /usr/bin/rsync
```

### "PostgreSQL data directory not found"
**Solution:** Configure the data directory path explicitly (see Configuration section above)

## Example: Complete Workflow

### 1. Prepare Your .zst File

```bash
# If you have a data directory backup
tar -czf backup.tar.gz /var/lib/postgresql/16/main
zstd backup.tar.gz -o backup.tar.zst

# Or if you have a pg_dump backup
pg_dump -Fc -U postgres tapesystem | zstd -o backup.dump.zst
```

### 2. Upload via Web Interface

1. Navigate to Restore PostgreSQL page
2. Select "BlackPearl" or "Rio"
3. Upload `backup.tar.zst` or `backup.dump.zst`
4. Wait for restore to complete

### 3. Verify

```bash
# Check buckets are available
curl http://localhost/api/migration/buckets

# Should return bucket list (if database has buckets)
```

## Best Practices

1. **Test First**: Test with a small backup before using production data
2. **Backup First**: Always backup existing database before restore
3. **Check Logs**: Monitor logs during restore for any issues
4. **Verify Data**: After restore, verify buckets/data are accessible
5. **Keep Original**: Don't delete original .zst file until restore is verified

## Related Documentation

- `POSTGRESQL_RESTORE.md` - General PostgreSQL restore guide
- `TEST_DATA_DIRECTORY_RESTORE.md` - Testing data directory restores
- `CONFIGURE_REMOTE_DATABASES.md` - Database configuration
