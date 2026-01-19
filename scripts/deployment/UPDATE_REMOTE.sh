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
git pull origin main

# Build backend
echo ""
echo "🔨 Building backend..."
cd backend
mvn clean package -DskipTests
cd ..

# Build frontend
echo ""
echo "🔨 Building frontend..."
cd frontend
npm install
npm run build
cd ..

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
