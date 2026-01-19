# Debug Stuck Restore

## Current Status
- Restore started at 16:55:03
- Last log: "Using customer-specific database name: tapesystem_howard_stern"
- No activity since then (no rsync, no disk I/O)
- Java process running but idle

## Diagnostic Commands

### 1. Check ALL Recent Logs (Not Just Filtered)
```bash
# Check last 100 lines of logs
sudo tail -100 log/application.log

# Check for any errors or exceptions
sudo tail -200 log/application.log | grep -i "error\|exception\|failed\|timeout" | tail -30
```

### 2. Check if File Upload Completed
```bash
# Check for file upload/save messages
sudo tail -200 log/application.log | grep -i "upload\|saved.*file\|extract\|decompress" | tail -20
```

### 3. Check Temporary Directories
```bash
# Find temp directories
ls -ltr /tmp/ | grep pg-restore | tail -5

# Check latest temp directory
LATEST_TMP=$(ls -td /tmp/pg-restore-* 2>/dev/null | head -1)
if [ -n "$LATEST_TMP" ]; then
    echo "Latest temp dir: $LATEST_TMP"
    ls -lh "$LATEST_TMP" | head -20
    du -sh "$LATEST_TMP"
fi
```

### 4. Check Thread Dump (See What Java is Doing)
```bash
# Get thread dump to see what the Java process is waiting on
jstack 885329 | head -100

# Or if jstack not available, use kill -3
kill -3 885329
sleep 2
sudo tail -100 log/application.log | grep -A 5 "Full thread dump\|at java"
```

### 5. Check Database Creation
```bash
# Check if database was created
sudo -u postgres psql -d postgres -c "SELECT datname FROM pg_database WHERE datname = 'tapesystem_howard_stern';"
```

### 6. Check for Blocked Operations
```bash
# Check if process is waiting on I/O
cat /proc/885329/status | grep -i state

# Check file descriptors
ls -l /proc/885329/fd/ | wc -l
```

## Common Causes

1. **File upload still in progress** - Check if file is still being uploaded
2. **Waiting for database creation** - Database creation might be blocked
3. **Waiting for file extraction** - Decompression/extraction might be stuck
4. **Deadlock or thread issue** - Java thread might be blocked
5. **Permission issue** - Process might be waiting for permissions

## Quick Fix: Check Full Logs
```bash
# Most important - see what's actually happening
sudo tail -200 log/application.log
```
