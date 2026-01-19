# Quick Start - Remote Server

## Pull Code and Start in Background

### Option 1: Use the Script (Easiest)

```bash
cd /home/seans/new_tracker
git pull
chmod +x START_IN_BACKGROUND.sh
./START_IN_BACKGROUND.sh
```

### Option 2: Manual Steps

```bash
# 1. Navigate to app directory
cd /home/seans/new_tracker

# 2. Pull latest code
git pull origin main

# 3. Rebuild backend
cd backend
mvn clean package -DskipTests
cd ..

# 4. Rebuild frontend
cd frontend
npm install
npm run build
cd ..

# 5. Install zstd (if not already installed, for .zst file support)
sudo apt-get install zstd  # Ubuntu/Debian
# OR
sudo yum install zstd      # CentOS/RHEL

# 6. Stop any existing process
pkill -f "migration-tracker-api.*jar" || true

# 7. Load environment and start in background
source .env
nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
    --server.port=80 \
    --server.address=0.0.0.0 \
    > log/application.log 2>&1 &

# 8. Check if it's running
sleep 3
ps aux | grep migration-tracker
```

## Verify It's Running

```bash
# Check process
ps aux | grep migration-tracker

# Check port
sudo ss -tulpn | grep :80

# Test locally
curl http://localhost/api/actuator/health

# View logs
tail -f /home/seans/new_tracker/log/application.log
```

## Access the Application

- **Frontend**: `http://10.85.45.166/`
- **API Health**: `http://10.85.45.166/api/actuator/health`

## Stop the Application

```bash
# Find and kill the process
pkill -f "migration-tracker-api.*jar"

# Or find PID first
ps aux | grep migration-tracker
kill <PID>
```

## View Logs

```bash
# Follow logs in real-time
tail -f /home/seans/new_tracker/log/application.log

# View last 100 lines
tail -n 100 /home/seans/new_tracker/log/application.log

# Search logs
grep ERROR /home/seans/new_tracker/log/application.log
```

## Troubleshooting

### Application Won't Start

```bash
# Check Java version
java -version  # Should be 17+

# Check if port is in use
sudo ss -tulpn | grep :80

# Check logs for errors
tail -n 50 /home/seans/new_tracker/log/application.log
```

### Port Permission Error (Port 80)

```bash
# Option 1: Use setcap (no root needed)
sudo setcap 'cap_net_bind_service=+ep' $(which java)

# Option 2: Run with sudo
sudo java -jar backend/target/migration-tracker-api-1.0.0.jar --server.port=80
```

### Frontend Not Loading

```bash
# Make sure frontend is built
cd /home/seans/new_tracker/frontend
npm run build
cd ..
```
