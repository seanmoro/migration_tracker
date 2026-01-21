#!/bin/bash
# Script to update code on remote server
# Run this on the remote server after pulling latest code

set -e

APP_DIR="/home/seans/new_tracker"

echo "=========================================="
echo "Updating Migration Tracker"
echo "=========================================="
echo ""

# Check if we're in the right directory
if [ ! -f "backend/pom.xml" ]; then
    echo "❌ Error: backend/pom.xml not found"
    echo "   Make sure you're in the migration_tracker directory"
    exit 1
fi

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
    else
        # First run or can't determine changes - rebuild everything to be safe
        echo "   Cannot determine changes. Rebuilding everything to be safe."
        REBUILD_BACKEND=true
        REBUILD_FRONTEND=true
    fi
else
    # Check what files changed
    CHANGED_FILES=$(git diff --name-only $OLD_HEAD $NEW_HEAD 2>/dev/null || echo "")
    
    if [ -z "$CHANGED_FILES" ]; then
        echo "   No file changes detected. Skipping rebuild."
        REBUILD_BACKEND=false
        REBUILD_FRONTEND=false
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
            BACKEND_CHANGED=true  # Config changes require backend rebuild
        fi
        
        REBUILD_BACKEND=$BACKEND_CHANGED
        REBUILD_FRONTEND=$FRONTEND_CHANGED
    fi
fi

# Build backend if needed
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

# Build frontend if needed
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

echo ""
echo "=========================================="
echo "✅ Update complete!"
echo "=========================================="
echo ""
echo "Next steps:"
echo ""
echo "1. Restart the service (if using systemd):"
echo "   sudo systemctl restart migration-tracker-new"
echo ""
echo "2. Or restart manually:"
echo "   source .env"
echo "   java -jar backend/target/migration-tracker-api-1.0.0.jar --server.port=8081"
echo ""
