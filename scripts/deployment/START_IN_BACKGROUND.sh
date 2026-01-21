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
OLD_HEAD=$(git rev-parse HEAD 2>/dev/null || echo "")
git pull origin main || {
    echo "⚠️  Warning: git pull failed or no changes to pull"
    OLD_HEAD=""
}
NEW_HEAD=$(git rev-parse HEAD 2>/dev/null || echo "")

# Check if anything changed
if [ -z "$OLD_HEAD" ] || [ -z "$NEW_HEAD" ] || [ "$OLD_HEAD" = "$NEW_HEAD" ]; then
    if [ -z "$OLD_HEAD" ] || [ "$OLD_HEAD" = "$NEW_HEAD" ]; then
        echo "   No changes detected. Skipping rebuild."
        REBUILD_BACKEND=false
        REBUILD_FRONTEND=false
        RESTART_NEEDED=false
    else
        # First run or can't determine changes - rebuild everything to be safe
        echo "   Cannot determine changes. Rebuilding everything to be safe."
        REBUILD_BACKEND=true
        REBUILD_FRONTEND=true
        RESTART_NEEDED=true
    fi
else
    # Check what files changed
    CHANGED_FILES=$(git diff --name-only $OLD_HEAD $NEW_HEAD 2>/dev/null || echo "")
    
    if [ -z "$CHANGED_FILES" ]; then
        echo "   No file changes detected. Skipping rebuild."
        REBUILD_BACKEND=false
        REBUILD_FRONTEND=false
        RESTART_NEEDED=false
    else
        # Check if backend files changed
        BACKEND_CHANGED=false
        if echo "$CHANGED_FILES" | grep -qE "^backend/|^pom\.xml"; then
            BACKEND_CHANGED=true
        fi
        
        # Check if frontend files changed
        FRONTEND_CHANGED=false
        if echo "$CHANGED_FILES" | grep -qE "^frontend/"; then
            FRONTEND_CHANGED=true
        fi
        
        # Check if config files that affect both changed
        if echo "$CHANGED_FILES" | grep -qE "^backend/src/main/resources/application\.yml|^\.env"; then
            BACKEND_CHANGED=true  # Config changes require backend restart
        fi
        
        REBUILD_BACKEND=$BACKEND_CHANGED
        REBUILD_FRONTEND=$FRONTEND_CHANGED
        RESTART_NEEDED=$BACKEND_CHANGED  # Only restart if backend changed
    fi
fi

# Rebuild backend if needed
if [ "$REBUILD_BACKEND" = true ]; then
    echo ""
    echo "🔨 Building backend (backend files changed)..."
    cd backend
    mvn clean package -DskipTests
    cd ..
else
    echo ""
    echo "⏭️  Skipping backend build (no backend changes detected)"
fi

# Rebuild frontend if needed
if [ "$REBUILD_FRONTEND" = true ]; then
    echo ""
    echo "🔨 Building frontend (frontend files changed)..."
    cd frontend
    npm install
    npm run build
    cd ..
else
    echo ""
    echo "⏭️  Skipping frontend build (no frontend changes detected)"
fi

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

# Restart application if backend was rebuilt
if [ "$RESTART_NEEDED" = true ]; then
    # Stop any existing process
    # Use sudo pkill since the process might be running as root
    echo ""
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
    
    # Start in background
    # Note: Using sudo -E to preserve environment variables and allow:
    # 1. Binding to port 80 (requires root)
    # 2. Writing to PostgreSQL data directory (requires root/postgres permissions)
    echo ""
    echo "🚀 Starting Migration Tracker on port $PORT (with sudo for port 80 and PostgreSQL permissions)..."
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
else
    echo ""
    echo "⏭️  Skipping restart (no backend changes detected)"
    echo ""
    echo "✅ Update complete! No restart needed."
fi
