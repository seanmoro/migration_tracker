# Testing PostgreSQL Data Directory Restore with Sudo

## Quick Test Commands (Running with Sudo)

```bash
# 1. Navigate to app directory
cd /home/seans/new_tracker

# 2. Pull latest code
git pull origin main

# 3. Rebuild backend
cd backend
mvn clean package -DskipTests
cd ..

# 4. Stop existing application (if running)
pkill -f "migration-tracker-api.*jar" || true
sleep 2

# 5. Load environment variables
source .env

# 6. Run application with sudo
sudo -E nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
    --server.port=80 \
    --server.address=0.0.0.0 \
    > log/application.log 2>&1 &

# Note: -E preserves environment variables (important for database configs)

# 7. Verify running
sleep 3
ps aux | grep migration-tracker
sudo curl http://localhost/api/actuator/health
```

## Alternative: Run in Foreground (for testing)

```bash
# Run in foreground to see output directly
cd /home/seans/new_tracker
source .env
sudo -E java -jar backend/target/migration-tracker-api-1.0.0.jar \
    --server.port=80 \
    --server.address=0.0.0.0
```

## Test the Restore

1. **Open restore page**: `http://<server-ip>/database/postgres-restore`
2. **Select database type**: BlackPearl or Rio
3. **Upload your data directory backup** (`.tar.zst` or `.tar` file)
4. **Watch the restore process**

## Monitor Logs

```bash
# Watch logs in real-time
sudo tail -f /home/seans/new_tracker/log/application.log | grep -i "postgres\|data\|restore\|directory\|rsync"

# Or view full logs
sudo tail -f /home/seans/new_tracker/log/application.log
```

## Expected Behavior with Sudo

With sudo, the application should be able to:
- ✅ Stop PostgreSQL: `sudo systemctl stop postgresql`
- ✅ Copy files: `sudo rsync -av` to `/var/lib/postgresql/16/main`
- ✅ Set permissions: `sudo chown -R postgres:postgres`
- ✅ Start PostgreSQL: `sudo systemctl start postgresql`

## Verify Permissions

```bash
# Check that files were copied with correct ownership
sudo ls -la /var/lib/postgresql/16/main/ | head -20

# Should show postgres:postgres ownership
```

## Stop the Application

```bash
# Find and kill the process
sudo pkill -f "migration-tracker-api.*jar"

# Or find PID first
ps aux | grep migration-tracker
sudo kill <PID>
```

## Important Notes

1. **Environment Variables**: Using `sudo -E` preserves environment variables from your `.env` file
2. **Log File Permissions**: Logs will be created as root, so you may need `sudo` to read them
3. **Port 80**: Running with sudo allows binding to port 80 without `setcap`
4. **Security**: Running with sudo gives the application elevated privileges - use with caution

## Troubleshooting

### Issue: Environment variables not loaded

**Solution**: Make sure to use `sudo -E`:
```bash
sudo -E java -jar backend/target/migration-tracker-api-1.0.0.jar
```

### Issue: Can't read log files

**Solution**: Use sudo to read logs:
```bash
sudo tail -f log/application.log
```

### Issue: Application won't start

**Solution**: Check for errors:
```bash
sudo java -jar backend/target/migration-tracker-api-1.0.0.jar
# Run in foreground to see errors
```

### Issue: Port already in use

**Solution**: Check what's using port 80:
```bash
sudo lsof -i :80
sudo kill <PID>
```

## Quick Verification

```bash
# One-liner to check everything
echo "=== Application Process ===" && \
sudo ps aux | grep migration-tracker | grep -v grep && \
echo -e "\n=== Health Check ===" && \
sudo curl -s http://localhost/api/actuator/health && \
echo -e "\n=== Recent Logs ===" && \
sudo tail -10 log/application.log
```
