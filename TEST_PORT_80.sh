#!/bin/bash
# Test script for port 80 access

echo "=========================================="
echo "Testing Port 80 Access"
echo "=========================================="
echo ""

# Test localhost
echo "1. Testing localhost:"
curl -s -o /dev/null -w "HTTP Status: %{http_code}\n" http://localhost/api/actuator/health
curl -s -o /dev/null -w "HTTP Status: %{http_code}\n" http://localhost/ || echo "   Root path test"
echo ""

# Test with server IP
echo "2. Testing with server IP (10.85.45.166):"
curl -s -o /dev/null -w "HTTP Status: %{http_code}\n" http://10.85.45.166/api/actuator/health
curl -s -o /dev/null -w "HTTP Status: %{http_code}\n" http://10.85.45.166/ || echo "   Root path test"
echo ""

# Test API endpoint
echo "3. Testing API endpoint:"
curl -s http://localhost/api/actuator/health | head -1
echo ""

# Test root path
echo "4. Testing root path (should serve frontend):"
curl -s http://localhost/ | head -5
echo ""

# Check if frontend files are accessible
echo "5. Testing frontend static files:"
if [ -f "frontend/dist/index.html" ]; then
    echo "   ✅ index.html exists"
    curl -s -o /dev/null -w "   HTTP Status: %{http_code}\n" http://localhost/index.html
else
    echo "   ❌ index.html not found"
fi
echo ""

echo "=========================================="
echo "If you see 200 status codes, the app is"
echo "working! If you can't access from your"
echo "local machine, it's a firewall issue."
echo "=========================================="
