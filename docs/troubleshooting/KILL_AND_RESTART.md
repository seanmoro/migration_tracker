# Kill All Processes and Restart

## Kill All Related Processes

```bash
# Kill all migration-tracker processes
sudo pkill -9 -f "migration-tracker-api.*jar"
sudo pkill -9 -f "sudo.*migration-tracker"

# Wait a moment
sleep 3

# Verify they're all gone
ps aux | grep "migration-tracker" | grep -v grep
```

## Then Restart

```bash
cd /home/seans/new_tracker

# Make sure password is in .env
# (You should have already added it)

# Source .env
source .env

# Start application
sudo -E nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
  --server.port=80 \
  --server.address=0.0.0.0 \
  > log/application.log 2>&1 &

# Wait for startup
sleep 10

# Verify it's running
ps aux | grep "migration-tracker-api.*jar" | grep -v grep

# Check logs for startup
sudo tail -50 log/application.log | grep -i "started\|error\|exception"
```
