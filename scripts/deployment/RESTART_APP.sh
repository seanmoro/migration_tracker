#!/bin/bash
# Quick script to restart the Migration Tracker application
# This is useful when only the deployment script changed or you need to restart manually

set -e

APP_DIR="/home/seans/new_tracker"
PORT="${SERVER_PORT:-80}"

echo "=========================================="
echo "Migration Tracker - Manual Restart"
echo "=========================================="
echo ""

# Navigate to app directory
cd "$APP_DIR" || {
    echo "❌ Error: Directory $APP_DIR not found"
    exit 1
}

# Stop any existing process
echo "🛑 Stopping any existing process..."
sudo pkill -f "migration-tracker-api.*jar" || echo "   No existing process found"

# Wait a moment for process to stop
sleep 2

# Load environment variables
if [ -f ".env" ]; then
    echo "📋 Loading environment variables..."
    source .env
else
    echo "⚠️  Warning: .env file not found"
fi

# Determine Java path
JAVA_CMD="java"
if [ -n "$JAVA_HOME" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
fi

# Start in background with sudo (for port 80 and PostgreSQL permissions)
echo ""
echo "🚀 Starting Migration Tracker on port $PORT (with sudo)..."
sudo -E nohup $JAVA_CMD -jar backend/target/migration-tracker-api-1.0.0.jar \
    --server.port=$PORT \
    --server.address=0.0.0.0 \
    > log/application.log 2>&1 &

# Get the process ID
APP_PID=$!
echo "   Process started with PID: $APP_PID"
echo "   Logs: $APP_DIR/log/application.log"

# Wait a moment and check if it's running
sleep 3
if ps -p $APP_PID > /dev/null; then
    echo ""
    echo "✅ Application is running!"
    echo ""
    echo "Access the application at:"
    echo "   http://$(hostname -I | awk '{print $1}'):$PORT"
    echo "   or"
    echo "   http://10.85.45.166:$PORT"
    echo ""
    echo "To view logs:"
    echo "   tail -f $APP_DIR/log/application.log"
    echo ""
    echo "To stop the application:"
    echo "   sudo pkill -f 'migration-tracker-api.*jar'"
else
    echo ""
    echo "❌ Application failed to start. Check logs:"
    echo "   tail -n 50 $APP_DIR/log/application.log"
    exit 1
fi
