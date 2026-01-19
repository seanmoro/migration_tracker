# Set PostgreSQL Password

## Steps to Fix

### 1. Kill Stuck Process
```bash
sudo pkill -f "migration-tracker-api.*jar"
sleep 2
```

### 2. Add Password to .env File
```bash
cd /home/seans/new_tracker

# Edit .env file and add/update the password
# Replace "your-password" with your actual PostgreSQL password
nano .env

# Or add it directly (replace YOUR_PASSWORD with actual password):
echo 'export MT_BLACKPEARL_PASSWORD="YOUR_PASSWORD"' >> .env
```

### 3. Verify .env File
```bash
# Check that password is set (don't show full password in output)
grep MT_BLACKPEARL_PASSWORD .env
```

### 4. Restart Application
```bash
source .env
sudo -E nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
  --server.port=80 \
  --server.address=0.0.0.0 \
  > log/application.log 2>&1 &

# Wait for startup
sleep 5

# Verify it's running
ps aux | grep "migration-tracker-api.*jar" | grep -v grep
```

### 5. Retry Restore
Go back to the UI and try the restore again. It should now be able to create the database and proceed with the restore.

## Note
Make sure the password in .env matches your PostgreSQL `postgres` user password. The restore uses this to connect and create the customer-specific database.
