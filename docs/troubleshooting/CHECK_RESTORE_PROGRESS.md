# Check Restore Progress

## Is It Hung or Just Slow?

Data directory restores can take a **very long time** (30+ minutes for large databases) because they copy many files. Here's how to check if it's actually working:

### 1. Check if the Process is Still Running
```bash
# Check if Java process is running
ps aux | grep "migration-tracker-api.*jar" | grep -v grep

# Check CPU usage (should be > 0% if actively copying)
ps aux | grep "migration-tracker-api.*jar" | grep -v grep | awk '{print $3}'

# Check if rsync or tar processes are running
ps aux | grep -E "rsync|tar|zstd" | grep -v grep
```

### 2. Check Application Logs (Most Important)
```bash
# Watch logs in real-time
sudo tail -f log/application.log

# Or check recent restore-related logs
sudo tail -200 log/application.log | grep -i "restore\|copy\|rsync\|data directory" | tail -30

# Check for errors
sudo tail -200 log/application.log | grep -i "error\|exception\|failed" | tail -20
```

### 3. Check Disk I/O Activity
```bash
# Check if disk is being written to (high iowait means copying is happening)
iostat -x 1 5

# Or check disk usage changes
watch -n 5 'df -h /var/lib/postgresql'
```

### 4. Check PostgreSQL Data Directory
```bash
# Check if files are being copied (watch file count increase)
watch -n 5 'find /var/lib/postgresql/16/main -type f | wc -l'

# Or check directory size
watch -n 5 'du -sh /var/lib/postgresql/16/main'
```

### 5. Check Temporary Directory (Where Files Are Being Extracted)
```bash
# Find temp directories
ls -ltr /tmp/ | grep pg-restore | tail -5

# Check size of extracted files
du -sh /tmp/pg-restore-* 2>/dev/null | tail -1

# Or find the latest temp directory
LATEST_TMP=$(ls -td /tmp/pg-restore-* 2>/dev/null | head -1)
if [ -n "$LATEST_TMP" ]; then
    echo "Latest temp dir: $LATEST_TMP"
    du -sh "$LATEST_TMP"
    find "$LATEST_TMP" -type f | wc -l
fi
```

### 6. Check Network Activity (If Uploading)
```bash
# Check if file is still being uploaded
netstat -an | grep ESTABLISHED | grep :80

# Or check network traffic
iftop -i eth0  # or your network interface
```

## What to Look For

### Signs It's Working (Not Hung):
- ✅ Java process CPU usage > 0%
- ✅ `rsync` or `tar` process visible in `ps aux`
- ✅ Disk I/O activity (high iowait in `iostat`)
- ✅ File count or directory size increasing
- ✅ Recent log entries (even if just debug messages)
- ✅ Network activity if still uploading

### Signs It Might Be Hung:
- ❌ Java process CPU usage = 0% for > 5 minutes
- ❌ No `rsync` or `tar` processes running
- ❌ No disk I/O activity
- ❌ No new log entries for > 10 minutes
- ❌ File count/size not changing

## If It's Actually Hung

### 1. Check for Errors in Logs
```bash
sudo tail -500 log/application.log | grep -i "error\|exception\|failed" | tail -30
```

### 2. Check System Resources
```bash
# Check memory usage
free -h

# Check disk space
df -h

# Check if disk is full
df -h /var/lib/postgresql
```

### 3. Kill and Restart (Last Resort)
```bash
# Kill the process
sudo pkill -f "migration-tracker-api.*jar"

# Wait a moment
sleep 2

# Check if PostgreSQL is running
sudo systemctl status postgresql

# Restart application
cd /home/seans/new_tracker
source .env
sudo -E nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
  --server.port=80 \
  --server.address=0.0.0.0 \
  > log/application.log 2>&1 &
```

## Expected Timeline

- **Small database (< 10GB)**: 5-15 minutes
- **Medium database (10-50GB)**: 15-45 minutes
- **Large database (50-200GB)**: 45 minutes - 2+ hours
- **Very large database (> 200GB)**: 2+ hours

The restore involves:
1. Uploading file (network speed dependent)
2. Decompressing `.zst` file (CPU intensive)
3. Extracting `.tar` file (I/O intensive)
4. Stopping PostgreSQL
5. **Copying data directory files (VERY I/O intensive - longest step)**
6. Setting permissions
7. Starting PostgreSQL

Step 5 (copying files) is usually the longest and can take 30+ minutes for large databases.

## Quick Status Check Command
```bash
# One-liner to check everything
echo "=== Process Status ===" && \
ps aux | grep "migration-tracker-api.*jar" | grep -v grep && \
echo -e "\n=== Recent Logs ===" && \
sudo tail -20 log/application.log | grep -i "restore\|copy\|rsync" && \
echo -e "\n=== Disk Activity ===" && \
iostat -x 1 2 | tail -5
```
