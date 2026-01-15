#!/bin/bash
# Diagnostic script for port 80 issues

echo "=========================================="
echo "Port 80 Diagnostic"
echo "=========================================="
echo ""

# Check if anything is listening on port 80
echo "1. Checking what's listening on port 80:"
sudo ss -tulpn | grep :80 || echo "   Nothing listening on port 80"
echo ""

# Check if application process is running
echo "2. Checking if application is running:"
ps aux | grep "migration-tracker-api.*jar" | grep -v grep || echo "   Application not running"
echo ""

# Check .env file
echo "3. Checking .env configuration:"
if [ -f ".env" ]; then
    grep SERVER_PORT .env || echo "   SERVER_PORT not found in .env"
else
    echo "   .env file not found"
fi
echo ""

# Check application logs
echo "4. Last 30 lines of application log:"
if [ -f "log/application.log" ]; then
    tail -n 30 log/application.log
else
    echo "   Log file not found"
fi
echo ""

# Check if port 80 requires root
echo "5. Testing if we can bind to port 80:"
if command -v nc &> /dev/null; then
    timeout 1 nc -l 80 2>&1 | head -1 || echo "   Cannot bind to port 80 (may need root or setcap)"
else
    echo "   nc (netcat) not available for testing"
fi
echo ""

# Check Java capability
echo "6. Checking Java capabilities:"
JAVA_BIN=$(readlink -f $(which java) 2>/dev/null || which java)
if [ -n "$JAVA_BIN" ]; then
    getcap "$JAVA_BIN" 2>/dev/null || echo "   No capabilities set on Java"
else
    echo "   Java not found"
fi
echo ""

# Check if frontend is built
echo "7. Checking if frontend is built:"
if [ -d "frontend/dist" ] && [ "$(ls -A frontend/dist 2>/dev/null)" ]; then
    echo "   ✅ Frontend dist directory exists"
    ls -lh frontend/dist | head -5
else
    echo "   ❌ Frontend dist directory missing or empty"
fi
echo ""

echo "=========================================="
echo "Recommendations:"
echo "=========================================="
echo ""
echo "If port 80 is not listening:"
echo "  1. Check logs for errors: tail -f log/application.log"
echo "  2. Try starting manually: java -jar backend/target/migration-tracker-api-1.0.0.jar --server.port=80"
echo "  3. Check if port 80 is already in use: sudo lsof -i :80"
echo ""
echo "If you see 'permission denied' errors:"
echo "  1. Run with sudo: sudo java -jar ..."
echo "  2. Or set capability: sudo setcap 'cap_net_bind_service=+ep' \$(readlink -f \$(which java))"
echo ""
