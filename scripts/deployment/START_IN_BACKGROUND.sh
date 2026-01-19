#!/bin/bash
# Script to pull latest code and start Migration Tracker in background
# Run this on the remote server

set -e

APP_DIR="/home/seans/new_tracker"
# Port can be overridden by SERVER_PORT env var, but default to 80
PORT="${SERVER_PORT:-80}"

echo "=========================================="
echo "Migration Tracker - Update and Start"
echo "=========================================="
echo ""

# Navigate to app directory
cd "$APP_DIR" || {
    echo "❌ Error: Directory $APP_DIR not found"
    exit 1
}

# Pull latest code
echo "📥 Pulling latest code from GitHub..."
git pull origin main

# Rebuild backend
echo ""
echo "🔨 Building backend..."
cd backend
mvn clean package -DskipTests
cd ..

# Rebuild frontend
echo ""
echo "🔨 Building frontend..."
cd frontend
npm install
npm run build
cd ..

# Check if zstd is installed (for .zst file support)
if ! command -v zstd &> /dev/null; then
    echo ""
    echo "⚠️  Warning: zstd not found. Installing for .zst file support..."
    if command -v apt-get &> /dev/null; then
        sudo apt-get update && sudo apt-get install -y zstd
    elif command -v yum &> /dev/null; then
        sudo yum install -y zstd
    else
        echo "   Please install zstd manually for .zst file support"
    fi
fi

# Stop any existing process
echo ""
echo "🛑 Stopping any existing process..."
pkill -f "migration-tracker-api.*jar" || echo "   No existing process found"

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

# Start in background
echo ""
echo "🚀 Starting Migration Tracker on port $PORT..."
nohup $JAVA_CMD -jar backend/target/migration-tracker-api-1.0.0.jar \
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
    echo "   pkill -f 'migration-tracker-api.*jar'"
else
    echo ""
    echo "❌ Application failed to start. Check logs:"
    echo "   tail -n 50 $APP_DIR/log/application.log"
    exit 1
fi
