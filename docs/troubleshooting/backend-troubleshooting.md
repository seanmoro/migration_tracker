# Troubleshooting Guide

## Backend Startup Issues

### Issue: "Process terminated with exit code: 1"

**Solution:** The backend is actually starting! The error you saw was likely during initialization. Check the logs:

```bash
tail -f log/migration_tracker_api.log
```

Look for:
- `Started MigrationTrackerApiApplication` - means it's running!
- `Tomcat started on port(s): 8080` - server is up
- Any SQL errors or connection issues

### Database Path Issues

If you see errors about database path:

1. **Check database exists:**
   ```bash
   ls -la resources/database/migrations.db
   ```

2. **The DatabaseConfig.java automatically finds the database** by checking:
   - `../resources/database/migrations.db` (from backend directory)
   - `resources/database/migrations.db` (from project root)
   - Absolute path resolution

3. **Check the log output** - it prints: `Database path: /path/to/db`

### Port Already in Use

If port 8080 is already in use:

```bash
# Find what's using port 8080
lsof -i :8080

# Kill the process
kill -9 <PID>

# Or change port in application.yml
server:
  port: 8081
```

### Java Version Issues

The backend requires Java 17+. You have Java 25, which is fine, but if you see version errors:

```bash
# Check Java version
java -version

# Set JAVA_HOME if needed
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

### Maven Dependency Issues

If Maven can't download dependencies:

```bash
# Clear Maven cache
rm -rf ~/.m2/repository

# Rebuild
cd backend
mvn clean install
```

## Common Issues

### Backend Starts But API Doesn't Respond

1. **Check it's actually running:**
   ```bash
   curl http://localhost:8080/api/dashboard/stats
   ```

2. **Check CORS** - Make sure `CorsConfig.java` is loaded

3. **Check context path** - API is at `/api`, not root

### Test Data Not Loading

1. **Check if data.sql is being executed:**
   - Look for "Executing SQL script" in logs
   - Check for "Executed SQL script" message

2. **Manually load data:**
   ```bash
   ./scripts/load_test_data.sh
   ```

3. **Check database:**
   ```bash
   sqlite3 resources/database/migrations.db "SELECT COUNT(*) FROM customer;"
   ```

### Frontend Can't Connect to Backend

1. **Verify backend is running:**
   ```bash
   curl http://localhost:8080/api/dashboard/stats
   ```

2. **Check CORS configuration** in `CorsConfig.java`

3. **Check frontend proxy** in `vite.config.ts`:
   ```typescript
   proxy: {
     '/api': {
       target: 'http://localhost:8080',
       changeOrigin: true,
     },
   }
   ```

4. **Check browser console** for CORS errors

## Quick Health Check

```bash
# 1. Check backend is running
curl http://localhost:8080/api/dashboard/stats

# 2. Check database
sqlite3 resources/database/migrations.db "SELECT COUNT(*) FROM customer;"

# 3. Check frontend
curl http://localhost:3000
```

## Log Files

- **Backend logs:** `log/migration_tracker_api.log`
- **CLI logs:** `log/migration_tracker.log`

## Still Having Issues?

1. Check logs first: `tail -50 log/migration_tracker_api.log`
2. Verify database path in logs
3. Check Java version: `java -version`
4. Check Maven: `mvn --version`
5. Try rebuilding: `mvn clean package`
