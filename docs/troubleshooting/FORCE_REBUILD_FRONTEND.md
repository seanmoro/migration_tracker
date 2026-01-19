# Force Frontend Rebuild to Fix Cache Issue

## Complete Clean Rebuild

```bash
cd /home/seans/new_tracker/frontend

# Remove everything
rm -rf dist
rm -rf node_modules/.vite
rm -rf .vite

# Clear npm cache (optional but helps)
npm cache clean --force

# Reinstall dependencies (ensures everything is fresh)
rm -rf node_modules
npm install

# Build from scratch
npm run build

# Verify new files were created
ls -la dist/assets/
# The JavaScript filename should be different now

# Check the hash in index.html
cat dist/index.html | grep "index-"
```

## After Rebuild, Restart Application

```bash
cd /home/seans/new_tracker

# Stop old process
sudo pkill -f "migration-tracker-api.*jar"
sleep 2

# Start new process
source .env
sudo -E nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
    --server.port=80 \
    --server.address=0.0.0.0 \
    > log/application.log 2>&1 &

sleep 15
```

## Test API Response

```bash
# Check what API is actually returning
sudo curl http://localhost/api/dashboard/active-phases-by-customer | head -50
```

## Browser Cache - Nuclear Option

If hard reload doesn't work:

1. **Chrome/Edge:**
   - Settings → Privacy and security → Clear browsing data
   - Select "All time"
   - Check "Cached images and files"
   - Clear data

2. **Or use a different browser** to test

3. **Or add cache-busting query parameter:**
   - Visit: `http://<server-ip>/?v=2`
   - This forces a fresh load

## Check Network Tab

In browser DevTools (F12):
1. Go to Network tab
2. Check "Disable cache" checkbox
3. Refresh page
4. Look for the API call to `/api/dashboard/active-phases-by-customer`
5. Click on it and check the Response tab
6. See what data structure is actually being returned
