# Fix White Page Issue

## Check if Frontend is Built

```bash
# Check if dist directory exists and has files
ls -la /home/seans/new_tracker/frontend/dist/

# Check if index.html exists
ls -la /home/seans/new_tracker/frontend/dist/index.html

# Check if JavaScript files exist
ls -la /home/seans/new_tracker/frontend/dist/assets/
```

## If Frontend is Not Built

```bash
cd /home/seans/new_tracker/frontend

# Install dependencies (if needed)
npm install

# Build frontend
npm run build

# Verify build
ls -la dist/
```

## Check Browser Console

Open browser developer tools (F12) and check:
- Console tab for JavaScript errors
- Network tab to see if files are loading (404 errors?)

## Check File Permissions

```bash
# Make sure files are readable
sudo chmod -R 644 /home/seans/new_tracker/frontend/dist/*
sudo find /home/seans/new_tracker/frontend/dist -type d -exec chmod 755 {} \;
```

## Test Direct File Access

```bash
# Test if index.html is accessible
sudo curl http://localhost/index.html

# Test if API is working
sudo curl http://localhost/api/actuator/health
```

## Quick Fix Command

```bash
# Build frontend if not built
cd /home/seans/new_tracker/frontend
if [ ! -d "dist" ] || [ -z "$(ls -A dist 2>/dev/null)" ]; then
    echo "Building frontend..."
    npm install
    npm run build
else
    echo "Frontend already built"
fi
cd ..

# Restart application
sudo pkill -f "migration-tracker-api.*jar"
sleep 2
source .env
sudo -E nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
    --server.port=80 \
    --server.address=0.0.0.0 \
    > log/application.log 2>&1 &
```
