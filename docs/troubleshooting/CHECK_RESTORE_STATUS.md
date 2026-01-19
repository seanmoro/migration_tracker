# Check Restore Status

## Process is Running (as root)
- Process ID: 885329
- State: Tl (stopped with threads, but using CPU)
- CPU: 3.4% (active)
- User: root (started with sudo)

## Check if Restore Continued

### 1. Check Recent Logs
```bash
# Check very recent logs (last few minutes)
sudo tail -200 log/application.log | tail -50

# Check for any restore activity
sudo tail -500 log/application.log | grep -i "restore\|upload\|saved.*file\|extract\|decompress\|zstd\|tar\|data directory" | tail -30
```

### 2. Check if File Upload Completed
```bash
# Check for file save messages
sudo tail -1000 log/application.log | grep -i "saved.*file\|upload.*complete\|file.*received" | tail -10
```

### 3. Check Temporary Directories
```bash
# Check if files are being extracted
ls -ltr /tmp/ | grep pg-restore | tail -5

# Check latest temp directory
LATEST_TMP=$(ls -td /tmp/pg-restore-* 2>/dev/null | head -1)
if [ -n "$LATEST_TMP" ]; then
    echo "Latest temp dir: $LATEST_TMP"
    ls -lh "$LATEST_TMP" | head -20
    du -sh "$LATEST_TMP"
fi
```

### 4. Check Process Activity
```bash
# Check what the process is doing (as root)
sudo cat /proc/885329/wchan  # What it's waiting on
sudo cat /proc/885329/status | grep -E "State|Threads"

# Check open file descriptors
sudo lsof -p 885329 2>/dev/null | grep -E "tmp|upload|file" | head -20
```

### 5. Check if Restore is Still in Progress
```bash
# Check for any restore-related processes
ps aux | grep -E "rsync|tar|zstd|pg_restore" | grep -v grep

# Check disk I/O
iostat -x 1 3
```

## Most Likely Scenarios

1. **File upload still in progress** - Large files take time to upload
2. **Restore is happening but not logging** - Check temp directories
3. **Process is waiting for I/O** - Check wchan and file descriptors
4. **Restore completed but no success message** - Check if database exists

## Quick Status Check
```bash
# One-liner to check everything
echo "=== Recent Logs ===" && \
sudo tail -50 log/application.log | tail -10 && \
echo -e "\n=== Temp Dirs ===" && \
ls -ltr /tmp/ | grep pg-restore | tail -3 && \
echo -e "\n=== Process State ===" && \
sudo cat /proc/885329/status | grep -E "State|Threads" && \
echo -e "\n=== Disk I/O ===" && \
iostat -x 1 2 | tail -3
```
