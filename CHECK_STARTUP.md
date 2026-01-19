# Check Application Startup

## If curl fails immediately, check logs:

```bash
# Check recent logs for errors
sudo tail -50 log/application.log

# Or watch logs in real-time
sudo tail -f log/application.log
```

## Common Issues:

### 1. Application Still Starting
- Wait 10-15 seconds and try again
- Spring Boot takes time to initialize

### 2. Port 80 Permission Error
- Check logs for "Permission denied" or "Address already in use"
- May need to check if another process is using port 80

### 3. Database Connection Error
- Check logs for SQLite or PostgreSQL connection errors
- Verify database file exists and is accessible

## Verify Startup:

```bash
# Wait a bit longer
sleep 10

# Check if process is still running
ps aux | grep migration-tracker | grep -v grep

# Check if port is listening
sudo ss -tulpn | grep :80

# Try health check again
sudo curl http://localhost/api/actuator/health

# If still failing, check logs
sudo tail -100 log/application.log | grep -i error
```
