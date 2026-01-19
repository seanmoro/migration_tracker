# Fix Deployment Permission Issues

## Problem

If you see `EACCES: permission denied` when building the frontend, it means the `frontend/dist` directory has files owned by root (from a previous sudo build).

## Solution

### Option 1: Remove dist directory and rebuild (Recommended)

```bash
cd /home/seans/new_tracker

# Remove dist directory (may need sudo if owned by root)
sudo rm -rf frontend/dist

# Rebuild frontend (NO sudo needed)
cd frontend && npm run build && cd ..
```

### Option 2: Change ownership of dist directory

```bash
cd /home/seans/new_tracker

# Change ownership of dist directory to your user
sudo chown -R seans:seans frontend/dist

# Rebuild frontend
cd frontend && npm run build && cd ..
```

## Complete Deployment Sequence

Here's the corrected sequence that handles permissions properly:

```bash
cd /home/seans/new_tracker

# 1. Pull latest code
git pull origin main

# 2. Fix permissions on dist directory (if needed)
sudo rm -rf frontend/dist 2>/dev/null || true

# 3. Rebuild backend (NO sudo needed)
cd backend && mvn clean package -DskipTests && cd ..

# 4. Rebuild frontend (NO sudo needed)
cd frontend && npm run build && cd ..

# 5. Create .env if it doesn't exist
if [ ! -f ".env" ]; then
  cat > .env << 'EOF'
export APP_DIR="/home/seans/new_tracker"
export MIGRATION_TRACKER_DB_PATH="$APP_DIR/resources/database/migrations.db"
export SERVER_PORT=80
export MT_BLACKPEARL_HOST=localhost
export MT_BLACKPEARL_PORT=5432
export MT_BLACKPEARL_DATABASE=tapesystem
export MT_BLACKPEARL_USERNAME=postgres
export MT_BLACKPEARL_PASSWORD=your-password
export MT_RIO_HOST=localhost
export MT_RIO_PORT=5432
export MT_RIO_DATABASE=rio_db
export MT_RIO_USERNAME=postgres
export MT_RIO_PASSWORD=your-password
export JAVA_OPTS="-Xms512m -Xmx2048m -XX:+UseG1GC"
EOF
  echo "✅ Created .env file - please update PostgreSQL passwords!"
fi

# 6. Stop existing process (separate command to avoid password prompt issues)
sudo pkill -f "migration-tracker-api.*jar" || true
sleep 2

# 7. Start application (separate command)
source .env
sudo -E nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
    --server.port=80 \
    --server.address=0.0.0.0 \
    > log/application.log 2>&1 &

# 8. Verify
sleep 5
ps aux | grep migration-tracker
sudo curl http://localhost/api/actuator/health
```

## Important Notes

1. **Never use sudo for npm/maven builds** - Only use sudo for:
   - Removing root-owned files
   - Starting the Java application (for port 80)
   - PostgreSQL operations

2. **Separate sudo commands** - Don't chain sudo commands in one line if password prompts are involved. Run them separately.

3. **Check ownership** - If you see permission errors, check ownership:
   ```bash
   ls -la frontend/dist
   ```

4. **Fix ownership recursively** - If needed:
   ```bash
   sudo chown -R seans:seans frontend/
   ```

## Quick Fix Command

If you just need to fix permissions and rebuild:

```bash
cd /home/seans/new_tracker/frontend
sudo rm -rf dist
npm run build
cd ..
```
