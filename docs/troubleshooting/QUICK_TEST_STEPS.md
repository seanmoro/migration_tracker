# Quick Test Steps - TAR Restore Fix

## On Remote Machine

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

## Test the Fix

1. **Open restore page**: `http://<server-ip>/database/postgres-restore`
2. **Upload the TAR file** that previously failed with "could not find header for file toc.dat"
3. **Expected**: Should automatically extract and restore instead of showing error
4. **Watch logs**: `tail -f log/application.log | grep -i tar`

## What to Look For

✅ **Success indicators**:
- Log shows: "TAR file is not PostgreSQL TAR format, attempting to extract"
- Log shows: "Found .sql file" or "Found .dump file"
- Restore completes successfully
- No error message about toc.dat

❌ **If still failing**:
- Check JAR was rebuilt (timestamp)
- Verify application restarted
- Check logs for actual error

## Full Testing Guide

See `TEST_TAR_RESTORE_FIX.md` for comprehensive testing scenarios.
