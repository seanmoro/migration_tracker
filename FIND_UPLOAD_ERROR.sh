#!/bin/bash
# Script to find the actual error message from upload logs

APP_DIR="/home/seans/new_tracker"
cd "$APP_DIR" || exit 1

echo "=========================================="
echo "Finding upload error in logs..."
echo "=========================================="
echo ""

# Check all possible log files
LOG_FILES=("log/application.log" "log/migration_tracker_api.log")

for log_file in "${LOG_FILES[@]}"; do
    if [ -f "$log_file" ]; then
        echo "📄 Checking: $log_file"
        echo ""
        
        # Find ERROR lines related to upload/restore/database
        echo "--- ERROR messages ---"
        grep -i "error\|exception\|failed" "$log_file" | tail -20
        echo ""
        
        # Find the most recent exception with context
        echo "--- Most recent exception (last 50 lines) ---"
        tail -50 "$log_file" | grep -A 10 -B 5 -i "exception\|error\|failed" | tail -30
        echo ""
        
        # Show last 30 lines for context
        echo "--- Last 30 lines of log ---"
        tail -30 "$log_file"
        echo ""
        echo "=========================================="
    fi
done

# If no log files, check if app is running
if [ ! -f "log/application.log" ] && [ ! -f "log/migration_tracker_api.log" ]; then
    echo "⚠️  No log files found. Checking if application is running..."
    if ps aux | grep -q "[m]igration-tracker-api.*jar"; then
        echo "   Application is running. Logs should be in: log/application.log"
        echo "   Try: tail -100 log/application.log"
    else
        echo "   Application is not running."
    fi
fi
