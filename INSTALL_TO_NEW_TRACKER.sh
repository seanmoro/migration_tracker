#!/bin/bash
# Installation script for /home/seans/new_tracker
# This installs the new Migration Tracker alongside the existing v0.6.2 installation

set -e

APP_DIR="/home/seans/new_tracker"
ORIGINAL_DIR="/home/seans/migration_tracker-v0.6.2"

echo "=========================================="
echo "Migration Tracker Installation"
echo "=========================================="
echo "Installation directory: $APP_DIR"
echo "Original installation: $ORIGINAL_DIR"
echo ""

# Check if original exists
if [ ! -d "$ORIGINAL_DIR" ]; then
    echo "⚠️  Warning: Original installation not found at $ORIGINAL_DIR"
    echo "   Continuing anyway..."
fi

# Create directory structure
echo "📁 Creating directory structure..."
mkdir -p "$APP_DIR"/{log,resources/{database,scripts,files},backend/target,frontend/dist}
cd "$APP_DIR"

# Create .env file
echo "⚙️  Creating .env configuration file..."
cat > "$APP_DIR/.env" << 'EOF'
# Installation directory
export APP_DIR="/home/seans/new_tracker"

# Database path (REQUIRED for alternate location)
export MIGRATION_TRACKER_DB_PATH="$APP_DIR/resources/database/migrations.db"

# Use different port (production uses 8080)
export SERVER_PORT=8081

# PostgreSQL credentials (update with your values)
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

echo "✅ Created $APP_DIR/.env"
echo "   ⚠️  Please edit this file and update PostgreSQL credentials!"

# Instructions
echo ""
echo "=========================================="
echo "Next Steps:"
echo "=========================================="
echo ""
echo "1. Transfer files to $APP_DIR:"
echo "   rsync -avz --exclude 'node_modules' --exclude 'target' \\"
echo "     --exclude '.git' --exclude 'dist' \\"
echo "     /path/to/migration_tracker/ user@server:$APP_DIR/"
echo ""
echo "2. Build the application:"
echo "   cd $APP_DIR"
echo "   cd backend && mvn clean package -DskipTests && cd .."
echo "   cd frontend && npm install && npm run build && cd .."
echo ""
echo "3. Update .env file with your PostgreSQL credentials:"
echo "   nano $APP_DIR/.env"
echo ""
echo "4. (Optional) Copy database from production:"
echo "   cp $ORIGINAL_DIR/resources/database/migrations.db \\"
echo "      $APP_DIR/resources/database/migrations.db"
echo ""
echo "5. Test run:"
echo "   cd $APP_DIR"
echo "   source .env"
echo "   java -jar backend/target/migration-tracker-api-1.0.0.jar --server.port=8081"
echo ""
echo "6. (Optional) Set up systemd service:"
echo "   See SIDE_BY_SIDE_INSTALLATION.md for systemd service configuration"
echo ""
echo "=========================================="
echo "Installation directory ready: $APP_DIR"
echo "=========================================="
