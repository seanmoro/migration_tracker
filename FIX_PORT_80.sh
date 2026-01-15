#!/bin/bash
# Script to fix port 80 binding and update .env

set -e

APP_DIR="/home/seans/new_tracker"

echo "=========================================="
echo "Fixing Port 80 Configuration"
echo "=========================================="
echo ""

cd "$APP_DIR" || exit 1

# Find actual Java binary (not symlink)
echo "🔍 Finding Java binary..."
JAVA_BIN=$(readlink -f $(which java) || which java)
echo "   Java binary: $JAVA_BIN"

# Try to set capability
echo ""
echo "🔐 Setting capability for port 80 binding..."
if sudo setcap 'cap_net_bind_service=+ep' "$JAVA_BIN" 2>/dev/null; then
    echo "✅ Capability set successfully"
else
    echo "⚠️  Could not set capability. You may need to run with sudo."
    echo "   Alternative: Use iptables port forwarding (see RUN_ON_PORT_80.md)"
fi

# Update .env file
echo ""
echo "📝 Updating .env file to use port 80..."
if [ -f ".env" ]; then
    # Backup .env
    cp .env .env.backup
    
    # Update SERVER_PORT
    if grep -q "export SERVER_PORT=" .env; then
        sed -i 's/export SERVER_PORT=.*/export SERVER_PORT=80/' .env
        echo "✅ Updated SERVER_PORT to 80"
    else
        echo "export SERVER_PORT=80" >> .env
        echo "✅ Added SERVER_PORT=80"
    fi
else
    echo "⚠️  .env file not found. Creating it..."
    cat > .env << 'EOF'
export APP_DIR="/home/seans/new_tracker"
export MIGRATION_TRACKER_DB_PATH="$APP_DIR/resources/database/migrations.db"
export SERVER_PORT=80
export JAVA_OPTS="-Xms512m -Xmx2048m -XX:+UseG1GC"
EOF
    echo "✅ Created .env file with port 80"
fi

echo ""
echo "=========================================="
echo "✅ Configuration updated!"
echo "=========================================="
echo ""
echo "Next steps:"
echo "1. Stop current process:"
echo "   pkill -f 'migration-tracker-api.*jar'"
echo ""
echo "2. Restart on port 80:"
echo "   ./START_IN_BACKGROUND.sh"
echo ""
echo "3. Verify:"
echo "   sudo ss -tulpn | grep :80"
echo "   curl http://localhost/api/actuator/health"
echo ""
