# Deploy Latest Changes to Remote Machine

## Quick Commands

### Full Deployment (Backend + Frontend)

Since we made changes to both backend and frontend, you need to rebuild both:

```bash
cd /home/seans/new_tracker

# 1. Pull latest code
git pull origin main

# 2. Rebuild backend (NO sudo needed)
cd backend && mvn clean package -DskipTests && cd ..

# 3. Rebuild frontend (NO sudo needed)
cd frontend && npm install && npm run build && cd ..

# 4. Stop existing process (use sudo if it was started with sudo)
sudo pkill -f "migration-tracker-api.*jar" || pkill -f "migration-tracker-api.*jar" || true
sleep 2

# 5. Start with sudo (needed for port 80 and PostgreSQL restore operations)
source .env
sudo -E nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
    --server.port=80 \
    --server.address=0.0.0.0 \
    > log/application.log 2>&1 &

# 6. Wait a few seconds for startup
sleep 5

# 7. Verify it's running
ps aux | grep migration-tracker
sudo curl http://localhost/api/actuator/health
```

### One-Liner Version

```bash
cd /home/seans/new_tracker && git pull origin main && cd backend && mvn clean package -DskipTests && cd .. && cd frontend && npm install && npm run build && cd .. && sudo pkill -f "migration-tracker-api.*jar" || true && sleep 2 && source .env && sudo -E nohup java -jar backend/target/migration-tracker-api-1.0.0.jar --server.port=80 --server.address=0.0.0.0 > log/application.log 2>&1 &
```

## Step-by-Step with Explanations

### Step 1: Navigate to Project Directory
```bash
cd /home/seans/new_tracker
```

### Step 2: Pull Latest Code
```bash
git pull origin main
```
This pulls all the latest changes including:
- Customer selection in restore UI
- Customer-specific database naming
- "Create New Phase" option
- Updated Gather Data workflow

### Step 3: Rebuild Backend
```bash
cd backend
mvn clean package -DskipTests
cd ..
```
**Note**: No sudo needed for Maven build. This compiles the Java code with the new customer-specific restore logic.

### Step 4: Rebuild Frontend
```bash
cd frontend
npm install
npm run build
cd ..
```
**Note**: No sudo needed for npm. This rebuilds the React frontend with:
- Customer dropdown in restore UI
- Updated Gather Data page
- Phase creation modal

### Step 5: Stop Existing Process
```bash
sudo pkill -f "migration-tracker-api.*jar" || pkill -f "migration-tracker-api.*jar" || true
sleep 2
```
Stops the currently running application. Uses `sudo` if the process was started with sudo.

### Step 6: Start Application
```bash
source .env
sudo -E nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
    --server.port=80 \
    --server.address=0.0.0.0 \
    > log/application.log 2>&1 &
```

**Why sudo?**
- Binding to port 80 (privileged port)
- PostgreSQL restore operations (stopping/starting PostgreSQL, copying to `/var/lib/postgresql/`)
- Setting file permissions for PostgreSQL data directory

**Why `-E`?**
- Preserves environment variables from `.env` file (like `JAVA_OPTS`, database passwords)

### Step 7: Verify
```bash
# Check if process is running
ps aux | grep migration-tracker

# Check health endpoint
sudo curl http://localhost/api/actuator/health

# Check logs
sudo tail -f log/application.log
```

## What's New in This Update?

1. **Customer Selection in Restore UI**
   - Must select a customer before uploading backup
   - Customer name used for database naming

2. **Customer-Specific Database Names**
   - BlackPearl: `tapesystem_<customer_name>`
   - Rio: `rio_db_<customer_name>`
   - Database automatically created if it doesn't exist

3. **Post-Restore Navigation**
   - After successful restore, automatically goes to Gather Data page
   - Customer is pre-selected

4. **"Create New Phase" Option**
   - New option in phase dropdown
   - Opens modal to create phase on the fly

5. **Improved Project Filtering**
   - Projects filtered by selected customer in Gather Data

## Troubleshooting

### Frontend Not Updating?
If you see old UI after deployment:
```bash
# Clear browser cache or do hard refresh
# Chrome/Edge: Ctrl+Shift+R (Windows) or Cmd+Shift+R (Mac)
# Firefox: Ctrl+F5 (Windows) or Cmd+Shift+R (Mac)
```

### Database Connection Issues?
Check your `.env` file has correct PostgreSQL credentials:
```bash
cat .env | grep MT_BLACKPEARL
cat .env | grep MT_RIO
```

### Port Already in Use?
```bash
# Check what's using port 80
sudo lsof -i :80

# Or use a different port (8080)
sudo -E nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
    --server.port=8080 \
    --server.address=0.0.0.0 \
    > log/application.log 2>&1 &
```

### Check Logs
```bash
# View recent logs
sudo tail -50 log/application.log

# Follow logs in real-time
sudo tail -f log/application.log
```

## Quick Reference

**Rebuild only backend:**
```bash
cd backend && mvn clean package -DskipTests && cd ..
```

**Rebuild only frontend:**
```bash
cd frontend && npm run build && cd ..
```

**Restart only (no rebuild):**
```bash
sudo pkill -f "migration-tracker-api.*jar" || true
sleep 2
source .env
sudo -E nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
    --server.port=80 \
    --server.address=0.0.0.0 \
    > log/application.log 2>&1 &
```
