# Testing PostgreSQL Data Directory Restore on Remote Machine

## Quick Test Commands

```bash
# 1. Navigate to app directory
cd /home/seans/new_tracker

# 2. Pull latest code
git pull origin main

# 3. Rebuild backend
cd backend
mvn clean package -DskipTests
cd ..

# 4. Restart application
pkill -f "migration-tracker-api.*jar" || true
sleep 2
source .env
nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
    --server.port=80 \
    --server.address=0.0.0.0 \
    > log/application.log 2>&1 &

# 5. Verify running
sleep 3
ps aux | grep migration-tracker
curl http://localhost/api/actuator/health
```

## Optional: Configure Data Directory Path

If you want to specify a custom data directory location:

```bash
# Add to .env file or export before starting
export MT_BLACKPEARL_DATA_DIRECTORY=/var/lib/postgresql/14/main
export MT_RIO_DATA_DIRECTORY=/var/lib/postgresql/16/main

# Or edit application.yml
nano backend/src/main/resources/application.yml
# Add under postgres.blackpearl and postgres.rio:
#   data-directory: /path/to/data/directory
```

## Test the Restore

### 1. Check Current PostgreSQL Data Directory

```bash
# Query PostgreSQL to see current data directory
psql -h localhost -U postgres -d postgres -c "SHOW data_directory;"

# Or check common locations
ls -la /var/lib/postgresql/*/main/
ls -la /var/lib/postgresql/data/
```

### 2. Prepare Test Backup File

Make sure you have a `.tar.zst` or `.tar` file containing a PostgreSQL data directory backup:
- Should contain `PG_VERSION` file
- Should contain `base/`, `global/`, `pg_wal/` directories

### 3. Upload and Restore

1. **Open restore page**: `http://<server-ip>/database/postgres-restore`
2. **Select database type**: BlackPearl or Rio
3. **Upload your data directory backup** (`.tar.zst` or `.tar` file)
4. **Watch the restore process**

### 4. Monitor Logs

```bash
# Watch logs in real-time
tail -f /home/seans/new_tracker/log/application.log | grep -i "postgres\|data\|restore\|directory"

# Or view full logs
tail -f /home/seans/new_tracker/log/application.log
```

## Expected Log Output

You should see logs like:

```
INFO: Restoring PostgreSQL data directory backup for blackpearl
INFO: PostgreSQL data directory: /var/lib/postgresql/14/main
INFO: Stopping PostgreSQL service...
INFO: PostgreSQL stopped via systemctl
INFO: Backing up existing data directory to: /var/lib/postgresql/14/main_backup_1234567890
INFO: Copying data directory backup to: /var/lib/postgresql/14/main
INFO: Data directory files copied successfully
INFO: Setting proper ownership and permissions...
INFO: Permissions set successfully (sudo chown postgres:postgres)
INFO: Starting PostgreSQL service...
INFO: PostgreSQL started via systemctl
INFO: PostgreSQL data directory backup restored successfully
```

## Verify Restore Worked

### 1. Check PostgreSQL is Running

```bash
# Check if PostgreSQL is running
sudo systemctl status postgresql
# OR
pg_isready -h localhost -p 5432
```

### 2. Check Data Directory

```bash
# Check the data directory was restored
ls -la /var/lib/postgresql/14/main/  # or your configured path
ls -la /var/lib/postgresql/14/main/base/
ls -la /var/lib/postgresql/14/main/global/

# Check PG_VERSION matches backup
cat /var/lib/postgresql/14/main/PG_VERSION
```

### 3. Test Database Connection

```bash
# Connect to the database
psql -h localhost -U postgres -d tapesystem  # For BlackPearl
# OR
psql -h localhost -U postgres -d rio_db       # For Rio

# Run a test query
\dt  # List tables
SELECT COUNT(*) FROM <some_table>;
```

### 4. Check Backup Was Created

```bash
# Check that old data directory was backed up
ls -la /var/lib/postgresql/14/main_backup_*/
```

## Troubleshooting

### Issue: "Could not determine PostgreSQL data directory"

**Solution**: Configure the data directory explicitly:
```bash
export MT_BLACKPEARL_DATA_DIRECTORY=/var/lib/postgresql/14/main
# Restart application
```

### Issue: "PostgreSQL must be stopped before restoring"

**Solution**: The code should stop it automatically, but if it fails:
```bash
# Stop PostgreSQL manually
sudo systemctl stop postgresql
# Then try restore again
```

### Issue: "Could not set permissions automatically"

**Solution**: Set permissions manually:
```bash
sudo chown -R postgres:postgres /var/lib/postgresql/14/main
sudo chmod 700 /var/lib/postgresql/14/main
```

### Issue: PostgreSQL won't start after restore

**Solution**: Check logs and permissions:
```bash
# Check PostgreSQL logs
sudo journalctl -u postgresql -n 50

# Check data directory permissions
ls -la /var/lib/postgresql/14/main/
# Should be owned by postgres:postgres

# Try starting manually
sudo systemctl start postgresql
```

### Issue: Wrong data directory location

**Solution**: Check what directory was used:
```bash
# Check logs for the path used
grep "PostgreSQL data directory" log/application.log

# Or query PostgreSQL
psql -h localhost -U postgres -c "SHOW data_directory;"
```

## Full Test Checklist

- [ ] Code pulled and backend rebuilt
- [ ] Application restarted successfully
- [ ] Data directory path configured (if needed)
- [ ] Backup file ready (`.tar.zst` or `.tar`)
- [ ] Uploaded backup through web interface
- [ ] Logs show successful restore
- [ ] PostgreSQL stopped automatically
- [ ] Data directory copied successfully
- [ ] Permissions set automatically
- [ ] PostgreSQL started automatically
- [ ] Can connect to database
- [ ] Data is accessible

## Quick Verification Commands

```bash
# One-liner to check everything
echo "=== PostgreSQL Status ===" && \
sudo systemctl status postgresql --no-pager | head -5 && \
echo -e "\n=== Data Directory ===" && \
ls -ld /var/lib/postgresql/*/main/ 2>/dev/null && \
echo -e "\n=== Database Connection ===" && \
pg_isready -h localhost -p 5432 && \
echo -e "\n=== Recent Restore Logs ===" && \
tail -20 log/application.log | grep -i "data\|restore\|postgres"
```
