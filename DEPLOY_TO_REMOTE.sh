#!/bin/bash
# Quick deployment script for remote server
# Usage: Run this on your remote server after cloning from GitHub

set -e

APP_DIR="/home/seans/new_tracker"
ORIGINAL_DIR="/home/seans/migration_tracker-v0.6.2"

echo "=========================================="
echo "Migration Tracker Deployment"
echo "=========================================="
echo "Installation directory: $APP_DIR"
echo ""

# Check if we're in the right directory
if [ ! -f "backend/pom.xml" ]; then
    echo "❌ Error: backend/pom.xml not found"
    echo "   Make sure you're in the migration_tracker directory"
    exit 1
fi

# Create necessary directories
echo "📁 Creating directory structure..."
mkdir -p log resources/database resources/scripts resources/files

# Create .env file if it doesn't exist
if [ ! -f ".env" ]; then
    echo "⚙️  Creating .env file..."
    cat > .env << 'EOF'
# Installation directory
export APP_DIR="/home/seans/new_tracker"

# Database path (REQUIRED)
export MIGRATION_TRACKER_DB_PATH="$APP_DIR/resources/database/migrations.db"

# Use different port (production v0.6.2 uses 8080)
export SERVER_PORT=8081

# PostgreSQL credentials - UPDATE THESE!
export MT_BLACKPEARL_HOST=your-blackpearl-host
export MT_BLACKPEARL_PORT=5432
export MT_BLACKPEARL_DATABASE=tapesystem
export MT_BLACKPEARL_USERNAME=postgres
export MT_BLACKPEARL_PASSWORD=your-password

export MT_RIO_HOST=your-rio-host
export MT_RIO_PORT=5432
export MT_RIO_DATABASE=rio_db
export MT_RIO_USERNAME=postgres
export MT_RIO_PASSWORD=your-password

# Java options
export JAVA_OPTS="-Xms512m -Xmx2048m -XX:+UseG1GC"
EOF
    echo "✅ Created .env file"
    echo "   ⚠️  Please edit .env and update PostgreSQL credentials!"
    echo "   Run: nano .env"
else
    echo "✅ .env file already exists"
fi

# Build backend
echo ""
echo "🔨 Building backend..."
cd backend
if [ ! -f "target/migration-tracker-api-1.0.0.jar" ]; then
    mvn clean package -DskipTests
    echo "✅ Backend built successfully"
else
    echo "✅ Backend JAR already exists (skipping build)"
fi
cd ..

# Build frontend
echo ""
echo "🔨 Building frontend..."
cd frontend
if [ ! -d "dist" ] || [ -z "$(ls -A dist 2>/dev/null)" ]; then
    npm install
    npm run build
    echo "✅ Frontend built successfully"
else
    echo "✅ Frontend dist already exists (skipping build)"
fi
cd ..

# Copy database from production (optional)
if [ -f "$ORIGINAL_DIR/resources/database/migrations.db" ]; then
    echo ""
    echo "📋 Copying database from production..."
    cp "$ORIGINAL_DIR/resources/database/migrations.db" \
       "$APP_DIR/resources/database/migrations.db"
    chmod 640 "$APP_DIR/resources/database/migrations.db"
    echo "✅ Database copied"
else
    echo ""
    echo "ℹ️  Production database not found at $ORIGINAL_DIR"
    echo "   Database will be created on first run"
fi

echo ""
echo "=========================================="
echo "✅ Deployment setup complete!"
echo "=========================================="
echo ""
echo "Next steps:"
echo ""
echo "1. Edit .env file with your PostgreSQL credentials:"
echo "   nano .env"
echo ""
echo "2. Test the application:"
echo "   source .env"
echo "   java -jar backend/target/migration-tracker-api-1.0.0.jar --server.port=8081"
echo ""
echo "3. (Optional) Set up systemd service:"
echo "   See DEPLOYMENT_STEPS.md for systemd configuration"
echo ""
echo "4. Access the application:"
echo "   Frontend: http://your-server:8081"
echo "   API: http://your-server:8081/api"
echo ""
