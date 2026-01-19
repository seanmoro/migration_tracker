# Complete Deployment - Run Commands Separately

The deployment got interrupted by sudo password prompt. Run these commands **one at a time**:

## Step-by-Step Commands

### 1. Stop existing process (run separately to avoid password prompt)
```bash
sudo pkill -f "migration-tracker-api.*jar" || true
sleep 2
```

### 2. Start application (run separately)
```bash
cd /home/seans/new_tracker
source .env
sudo -E nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
    --server.port=80 \
    --server.address=0.0.0.0 \
    > log/application.log 2>&1 &
```

### 3. Verify it's running
```bash
sleep 5
ps aux | grep migration-tracker
sudo curl http://localhost/api/actuator/health
```

## Alternative: One-Liner Without Password Prompt

If you have passwordless sudo configured, you can use:

```bash
cd /home/seans/new_tracker && sudo pkill -f "migration-tracker-api.*jar" || true && sleep 2 && source .env && sudo -E nohup java -jar backend/target/migration-tracker-api-1.0.0.jar --server.port=80 --server.address=0.0.0.0 > log/application.log 2>&1 &
```

## Check Application Status

```bash
# Check if process is running
ps aux | grep migration-tracker

# Check logs
sudo tail -50 log/application.log

# Check health endpoint
sudo curl http://localhost/api/actuator/health
```

## If Application Didn't Start

Check the logs for errors:
```bash
sudo tail -100 log/application.log
```

Common issues:
- Port 80 already in use
- Database connection errors
- Missing dependencies
