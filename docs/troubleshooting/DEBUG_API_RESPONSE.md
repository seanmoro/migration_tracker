# Debug API Response

## Test What API is Actually Returning

```bash
# Test all dashboard endpoints
echo "=== Active Phases ==="
sudo curl -s http://localhost/api/dashboard/active-phases | head -20

echo -e "\n=== Phases By Customer ==="
sudo curl -s http://localhost/api/dashboard/active-phases-by-customer | head -50

echo -e "\n=== Phases Needing Attention ==="
sudo curl -s http://localhost/api/dashboard/phases-needing-attention | head -20

echo -e "\n=== Stats ==="
sudo curl -s http://localhost/api/dashboard/stats
```

## Check if Code Was Pulled

```bash
# Verify latest code is there
cd /home/seans/new_tracker
git log --oneline -5

# Check if the fixes are in the files
grep -n "normalizedActivePhases" frontend/src/views/Dashboard.tsx
grep -n "Array.isArray" frontend/src/api/dashboard.ts
```

## Force New Build Hash

If the filename is the same, we can force a new hash by making a small change:

```bash
cd /home/seans/new_tracker/frontend

# Add a comment to force rebuild
echo "// Build timestamp: $(date)" >> src/main.tsx

# Rebuild
npm run build

# Check new filename
ls -la dist/assets/
```

## Browser: Check Network Tab

1. Open DevTools (F12)
2. Go to Network tab
3. Check "Disable cache" 
4. Refresh page
5. Find the request to `/api/dashboard/active-phases-by-customer`
6. Click it and check the Response tab
7. See what data structure is actually returned
