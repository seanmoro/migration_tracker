#!/bin/bash
# Network diagnostic script for remote server

echo "=========================================="
echo "Network Configuration Check"
echo "=========================================="
echo ""

echo "1. All IP addresses on this server:"
ip addr show | grep "inet " | awk '{print "   " $2}'
echo ""

echo "2. Default route:"
ip route | grep default
echo ""

echo "3. All listening ports:"
ss -tulpn | grep LISTEN
echo ""

echo "4. Test localhost connection:"
curl -s -o /dev/null -w "HTTP Status: %{http_code}\n" http://localhost:8081/api/actuator/health || echo "Failed"
echo ""

echo "5. Server hostname and FQDN:"
hostname
hostname -f 2>/dev/null || echo "FQDN not available"
echo ""

echo "=========================================="
echo "To access the application, use one of the"
echo "IP addresses shown above (not 10.84.45.166)"
echo "=========================================="
