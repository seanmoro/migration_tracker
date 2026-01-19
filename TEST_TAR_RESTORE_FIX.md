# Testing Steps for TAR Archive Restore Fix

## Overview
This document outlines steps to test the improved TAR archive error detection and fallback mechanism on a remote machine.

## What Was Fixed
The code now better detects when a TAR file is not in PostgreSQL TAR format (missing `toc.dat` header) and automatically falls back to extracting the plain TAR archive and looking for `.sql` or `.dump` files inside.

## Prerequisites
- Remote server with Migration Tracker installed
- Access to the application directory (default: `/home/seans/new_tracker`)
- A TAR archive file that previously failed with the "could not find header for file toc.dat" error
- PostgreSQL client tools installed (`pg_restore`, `psql`, `tar`)

## Step 1: Update Code on Remote Machine

```bash
# SSH into remote machine
ssh user@remote-server

# Navigate to application directory
cd /home/seans/new_tracker

# Pull latest code from repository
git pull origin main

# Verify the fix is present
grep -A 5 "could not find header" backend/src/main/java/com/spectralogic/migrationtracker/service/PostgreSQLRestoreService.java
```

Expected output should show the improved error detection logic with case-insensitive matching.

## Step 2: Rebuild Backend

```bash
# Build backend with updated code
cd backend
mvn clean package -DskipTests
cd ..

# Verify JAR was created
ls -lh backend/target/migration-tracker-api-*.jar
```

## Step 3: Restart the Application

### Option A: If using systemd service
```bash
sudo systemctl restart migration-tracker-new
# or
sudo systemctl restart migration-tracker
```

### Option B: If running manually
```bash
# Stop existing process
pkill -f "migration-tracker-api.*jar" || true

# Wait a moment
sleep 2

# Start with updated JAR
cd /home/seans/new_tracker
source .env
nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
    --server.port=80 \
    --server.address=0.0.0.0 \
    > log/application.log 2>&1 &

# Verify it's running
sleep 3
ps aux | grep migration-tracker
```

## Step 4: Verify Application is Running

```bash
# Check health endpoint
curl http://localhost/api/actuator/health

# Check logs for startup
tail -n 50 log/application.log
```

## Step 5: Test TAR Archive Restore

### Test Case 1: TAR Archive with toc.dat Error (Main Fix)

1. **Access the restore page**:
   - Open browser: `http://<remote-server-ip>/database/postgres-restore`
   - Or: `http://<remote-server-ip>:8080/database/postgres-restore`

2. **Upload a TAR file that previously failed**:
   - Select database type (BlackPearl or Rio)
   - Upload the TAR archive file that previously showed the error:
     ```
     pg_restore failed with exit code 1: pg_restore: error: could not find header for file "toc.dat" in tar archive
     ```

3. **Expected Behavior**:
   - The system should detect the error automatically
   - It should extract the TAR archive using the system `tar` command
   - It should search for `.sql` or `.dump` files inside
   - It should restore using the appropriate method
   - You should see a success message instead of the error

4. **Monitor logs during upload**:
   ```bash
   # In a separate terminal, watch the logs
   tail -f /home/seans/new_tracker/log/application.log | grep -i "tar\|restore\|error"
   ```

5. **Expected log messages**:
   ```
   INFO: TAR file is not PostgreSQL TAR format, attempting to extract and find backup files
   INFO: Successfully extracted TAR archive to: /tmp/pg-restore-XXXXXX
   INFO: Found .sql file in TAR archive: /tmp/pg-restore-XXXXXX/path/to/backup.sql
   ```
   OR
   ```
   INFO: Found .dump file in TAR archive: /tmp/pg-restore-XXXXXX/path/to/backup.dump
   ```

### Test Case 2: Valid PostgreSQL TAR Format

1. **Upload a valid PostgreSQL TAR format file** (created with `pg_dump -Ft`):
   - Should restore directly using `pg_restore -Ft`
   - Should NOT trigger the fallback extraction

2. **Expected Behavior**:
   - Direct restore without extraction
   - Success message

### Test Case 3: TAR Archive with .sql File Inside

1. **Create a test TAR with SQL file**:
   ```bash
   # Create a test SQL file
   echo "CREATE TABLE test_table (id INT);" > test_backup.sql
   
   # Create TAR archive
   tar -cf test_backup.tar test_backup.sql
   ```

2. **Upload the TAR file**:
   - Should extract the TAR
   - Should find the `.sql` file
   - Should restore using `psql`

### Test Case 4: TAR Archive with .dump File Inside

1. **Create a test TAR with dump file**:
   ```bash
   # Create a test dump file (or use existing)
   # Create TAR archive
   tar -cf test_backup.tar test_backup.dump
   ```

2. **Upload the TAR file**:
   - Should extract the TAR
   - Should find the `.dump` file
   - Should restore using `pg_restore`

### Test Case 5: TAR Archive with No Backup Files

1. **Create a TAR with no backup files**:
   ```bash
   # Create TAR with random files
   echo "test" > random_file.txt
   echo "data" > data.txt
   tar -cf test_no_backup.tar random_file.txt data.txt
   ```

2. **Upload the TAR file**:
   - Should extract successfully
   - Should show improved error message listing the files found:
     ```
     No .sql or .dump files found in TAR archive. Found 2 file(s) in archive:
       - random_file.txt
       - data.txt
     
     Please ensure the archive contains a PostgreSQL backup file (.sql or .dump).
     ```
   - Should NOT crash
   - The error message helps identify what files are actually in the archive

## Step 6: Verify Error Detection Improvements

The fix improves detection of these error variations:
- ✅ "could not find header for file 'toc.dat' in tar archive" (original error)
- ✅ "could not find header for file toc.dat" (without quotes)
- ✅ "could not find header" (general)
- ✅ "not a tar archive"
- ✅ "invalid tar header"
- ✅ Case variations (now case-insensitive)

## Step 7: Check Database After Restore

```bash
# Connect to PostgreSQL
psql -h localhost -U postgres -d tapesystem  # For BlackPearl
# OR
psql -h localhost -U postgres -d rio_db      # For Rio

# Verify tables were created
\dt

# Check some data
SELECT COUNT(*) FROM <some_table>;
```

## Troubleshooting

### Issue: Still seeing the error message
**Solution**: 
- Verify the backend was rebuilt: `ls -lh backend/target/migration-tracker-api-*.jar`
- Check the JAR timestamp is recent
- Verify the application was restarted
- Check logs for the actual error output

### Issue: TAR extraction fails
**Solution**:
- Verify `tar` command is available: `which tar`
- Check file permissions on the uploaded file
- Check disk space: `df -h`

### Issue: No .sql or .dump files found
**Solution**:
- Verify the TAR actually contains backup files
- Extract manually to check: `tar -tf your_file.tar`
- Check logs for extraction output

### Issue: Application not starting
**Solution**:
```bash
# Check Java version
java -version  # Should be 17+

# Check port availability
sudo ss -tulpn | grep :80

# Check logs
tail -n 100 log/application.log
```

## Success Criteria

✅ **Test passes if**:
1. TAR file that previously failed now succeeds
2. Error message is automatically detected
3. TAR is extracted automatically
4. Backup file (.sql or .dump) is found and restored
5. Database is accessible after restore
6. No manual intervention required

## Rollback Plan

If the fix causes issues:

```bash
# Revert to previous commit
cd /home/seans/new_tracker
git log --oneline -5  # Find previous commit
git checkout <previous-commit-hash>
cd backend
mvn clean package -DskipTests
cd ..
# Restart application
```

## Additional Notes

- The fix is backward compatible - valid PostgreSQL TAR files still work as before
- The fallback only triggers when the specific error is detected
- Temporary extraction directories are automatically cleaned up
- Logs will show which method was used (PostgreSQL TAR vs plain TAR extraction)
