# Start the Application

## Check if Running

```bash
# Check if process is running
ps aux | grep migration-tracker | grep -v grep

# Check if port 80 is listening
sudo ss -tulpn | grep :80
```

## Start the Application

```bash
cd /home/seans/new_tracker

# Load environment
source .env

# Start with sudo (for port 80 and PostgreSQL permissions)
sudo -E nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
    --server.port=80 \
    --server.address=0.0.0.0 \
    > log/application.log 2>&1 &

# Wait for startup (Spring Boot takes 10-15 seconds)
sleep 15

# Verify it's running
ps aux | grep migration-tracker | grep -v grep
sudo ss -tulpn | grep :80
sudo curl http://localhost/api/actuator/health
```

## Check Logs if Not Starting

```bash
# Check recent logs
sudo tail -50 log/application.log

# Or watch in real-time
sudo tail -f log/application.log
```

## Common Issues

### Port 80 Already in Use
```bash
# Check what's using port 80
sudo lsof -i :80

# Kill the process if needed
sudo kill <PID>
```

### Java Not Found
```bash
# Check Java version
java -version

# Should show Java 17 or higher
```

### Permission Denied
```bash
# Make sure you're using sudo
sudo -E java -jar backend/target/migration-tracker-api-1.0.0.jar
```
