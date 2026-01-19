# Fix Browser Cache Issue

## The Problem
The browser is caching the old JavaScript file (`index-D96YovlG.js`), so even after rebuilding, it's using the old code.

## Solution: Clean Rebuild

```bash
# 1. Remove old build
cd /home/seans/new_tracker/frontend
rm -rf dist
rm -rf node_modules/.vite

# 2. Rebuild from scratch
npm run build

# 3. Verify new files were created
ls -la dist/assets/

# 4. Restart application
cd ..
sudo pkill -f "migration-tracker-api.*jar"
sleep 2
source .env
sudo -E nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
    --server.port=80 \
    --server.address=0.0.0.0 \
    > log/application.log 2>&1 &
```

## Clear Browser Cache

**In Chrome/Edge:**
1. Open DevTools (F12)
2. Right-click the refresh button
3. Select "Empty Cache and Hard Reload"

**Or manually:**
- `Ctrl+Shift+Delete` (Windows/Linux) or `Cmd+Shift+Delete` (Mac)
- Select "Cached images and files"
- Click "Clear data"

**Or use Incognito/Private mode:**
- Open in incognito/private window to bypass cache

## Test API Directly

```bash
# Check what API is actually returning
sudo curl http://localhost/api/dashboard/active-phases-by-customer | jq .

# If jq is not installed:
sudo curl http://localhost/api/dashboard/active-phases-by-customer
```

## If Still Failing

Check browser console for the actual API response:
1. Open DevTools (F12)
2. Go to Network tab
3. Refresh page
4. Look for `/api/dashboard/active-phases-by-customer` request
5. Click on it and check the Response tab
6. See what data structure is actually being returned
