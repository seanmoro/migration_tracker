# Fix Stuck Restore

## Problem
Restore process is stopped/suspended right after determining database name. No file operations occurred.

## Diagnostic Steps

### 1. Check if Process is Actually Stopped
```bash
# Check process state and signals
ps aux | grep 885329 | grep -v grep

# Check what signal stopped it
kill -l $(cat /proc/885329/stat | awk '{print $8}')

# Try to resume it (if it's just stopped)
kill -CONT 885329
```

### 2. Check for More Recent Logs
```bash
# Check if there are newer logs (maybe restore is happening but not logged)
sudo tail -500 log/application.log | tail -50

# Check for any activity in the last few minutes
sudo tail -1000 log/application.log | grep "16:5[5-9]\|17:" | tail -30
```

### 3. Check if File Upload Completed
```bash
# Check HTTP request logs or Tomcat access logs
# The file upload might still be in progress

# Check network connections
netstat -an | grep :80 | grep ESTABLISHED

# Check if there's a large file being uploaded
lsof -p 885329 2>/dev/null | grep -i "tmp\|upload"
```

### 4. Check Thread Dump
```bash
# Get thread dump to see what threads are doing
kill -3 885329
sleep 2
sudo tail -200 log/application.log | grep -A 20 "Full thread dump\|at java" | head -50
```

### 5. Check if Restore Method is Being Called
The restore should:
1. Save uploaded file
2. Extract/decompress
3. Detect format
4. Restore

But logs show it stopped after step 0 (determining database name). This suggests the file upload might not have completed, or the restore method is blocking.

## Quick Fix: Resume Process and Check

```bash
# 1. Try to resume the process
kill -CONT 885329

# 2. Wait a moment
sleep 5

# 3. Check logs again
sudo tail -50 log/application.log | grep -i "restore\|upload\|file\|extract"

# 4. If still stuck, check what it's waiting on
cat /proc/885329/wchan  # What kernel function it's waiting on
```

## If Process is Actually Dead/Stuck

### Option 1: Kill and Restart
```bash
# Kill the process
sudo pkill -f "migration-tracker-api.*jar"

# Wait
sleep 2

# Restart
cd /home/seans/new_tracker
source .env
sudo -E nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
  --server.port=80 \
  --server.address=0.0.0.0 \
  > log/application.log 2>&1 &
```

### Option 2: Check if File Upload is the Issue
The restore might be waiting for the file upload to complete. Check:
- Browser network tab to see if upload is still in progress
- File size - large files take time to upload
- Network speed

## Most Likely Cause
The process is stopped (T state) which usually means:
- It received SIGSTOP signal
- It's waiting for I/O that's blocked
- The file upload hasn't completed yet

Try `kill -CONT 885329` to resume it, then check logs again.
