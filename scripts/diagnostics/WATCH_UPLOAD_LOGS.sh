#!/bin/bash
# Script to watch upload-related logs
# Run this on the remote server before uploading

APP_DIR="/home/seans/new_tracker"
cd "$APP_DIR" || exit 1

echo "=========================================="
echo "Finding and watching upload logs..."
echo "=========================================="
echo ""

# Check if log directory exists
if [ ! -d "log" ]; then
    echo "⚠️  Log directory doesn't exist. Creating it..."
    mkdir -p log
fi

# Find which log files exist
LOG_FILES=()
if [ -f "log/application.log" ]; then
    LOG_FILES+=("log/application.log")
fi
if [ -f "log/migration_tracker_api.log" ]; then
    LOG_FILES+=("log/migration_tracker_api.log")
fi

if [ ${#LOG_FILES[@]} -eq 0 ]; then
    echo "❌ No log files found. Checking if application is running..."
    if ps aux | grep -q "[m]igration-tracker-api.*jar"; then
        echo "   Application is running but no logs yet."
        echo "   Logs will appear in: log/application.log"
        echo ""
        echo "📺 Starting to watch for logs..."
        echo "   (Press Ctrl+C to stop)"
        echo ""
        tail -f log/application.log 2>/dev/null || {
            echo "Waiting for log file to be created..."
            while [ ! -f "log/application.log" ]; do
                sleep 1
            done
            tail -f log/application.log
        }
    else
        echo "   Application is not running."
        echo "   Start it with: ./START_IN_BACKGROUND.sh"
        exit 1
    fi
elif [ ${#LOG_FILES[@]} -eq 1 ]; then
    LOG_FILE="${LOG_FILES[0]}"
    echo "✅ Found log file: $LOG_FILE"
    echo ""
    echo "📺 Watching upload-related logs..."
    echo "   (Press Ctrl+C to stop)"
    echo ""
    tail -f "$LOG_FILE" | grep --line-buffered -i "restore\|upload\|database\|zstd\|pg_restore\|psql\|file\|backup\|extract"
else
    echo "✅ Found multiple log files:"
    for log in "${LOG_FILES[@]}"; do
        echo "   - $log"
    done
    echo ""
    echo "📺 Watching all log files for upload activity..."
    echo "   (Press Ctrl+C to stop)"
    echo ""
    # Use tail -f on all log files
    tail -f "${LOG_FILES[@]}" | grep --line-buffered -i "restore\|upload\|database\|zstd\|pg_restore\|psql\|file\|backup\|extract"
fi
