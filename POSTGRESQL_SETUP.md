# PostgreSQL Database Setup for Bucket Selection

## Issue
The bucket selection feature requires connections to PostgreSQL databases (BlackPearl and Rio), but connections are failing with "Failed to obtain JDBC Connection".

## Important Note
**BlackPearl and Rio databases are typically on remote servers, not localhost!**

The application defaults to `localhost:5432`, but you'll likely need to configure remote database connections.

## Common Causes

### 1. PostgreSQL Not Running (Local) or Not Configured (Remote)
PostgreSQL server is not running on the configured host/port, OR you're trying to connect to a remote server that isn't configured.

**Check:**
```bash
# Check if PostgreSQL is running
ps aux | grep postgres

# Or check if port 5432 is listening
lsof -i :5432
```

**Fix:**
```bash
# Start PostgreSQL (varies by OS)
# macOS with Homebrew:
brew services start postgresql@14

# Linux:
sudo systemctl start postgresql
```

### 2. Wrong Host/Port Configuration (Most Common)
The application defaults to `localhost:5432`, but BlackPearl and Rio databases are typically on remote servers.

**Check current configuration:**
- Backend logs show connection attempts to:
  - `jdbc:postgresql://localhost:5432/tapesystem` (BlackPearl) - **This is likely wrong!**
  - `jdbc:postgresql://localhost:5432/rio_db` (Rio) - **This is likely wrong!**

**You need to configure the actual hostnames/IPs of your BlackPearl and Rio servers.**

**Configure via environment variables:**
```bash
export MT_BLACKPEARL_HOST=localhost
export MT_BLACKPEARL_PORT=5432
export MT_BLACKPEARL_DATABASE=tapesystem
export MT_BLACKPEARL_USERNAME=postgres
export MT_BLACKPEARL_PASSWORD=your_password

export MT_RIO_HOST=localhost
export MT_RIO_PORT=5432
export MT_RIO_DATABASE=rio_db
export MT_RIO_USERNAME=postgres
export MT_RIO_PASSWORD=your_password
```

**Or configure in `application.yml`:**
```yaml
postgres:
  blackpearl:
    host: localhost
    port: 5432
    database: tapesystem
    username: postgres
    password: your_password
  rio:
    host: localhost
    port: 5432
    database: rio_db
    username: postgres
    password: your_password
```

### 3. Databases Don't Exist
The databases `tapesystem` and `rio_db` may not exist.

**Check:**
```bash
psql -U postgres -l
```

**Create databases:**
```bash
# Connect to PostgreSQL
psql -U postgres

# Create databases
CREATE DATABASE tapesystem;
CREATE DATABASE rio_db;

# Exit
\q
```

### 4. Authentication Failed
Wrong username or password.

**Test connection manually:**
```bash
# Test BlackPearl connection
psql -h localhost -U postgres -d tapesystem

# Test Rio connection
psql -h localhost -U postgres -d rio_db
```

**Reset password:**
```bash
psql -U postgres
ALTER USER postgres WITH PASSWORD 'new_password';
```

### 5. Remote Database Access
If databases are on a remote server:

```bash
export MT_BLACKPEARL_HOST=remote-server.example.com
export MT_BLACKPEARL_PORT=5432
# ... etc
```

**Note:** Ensure PostgreSQL is configured to allow remote connections:
- Check `pg_hba.conf` for allowed hosts
- Check `postgresql.conf` for `listen_addresses`

## Testing Connection

### From Command Line
```bash
# Test BlackPearl
psql -h localhost -U postgres -d tapesystem -c "SELECT version();"

# Test Rio
psql -h localhost -U postgres -d rio_db -c "SELECT version();"
```

### From Application
After setting environment variables and restarting the backend, check logs:
```bash
tail -f log/migration_tracker_api.log | grep -i postgres
```

Look for:
- ✅ "Successfully connected to BlackPearl database"
- ✅ "Successfully connected to Rio database"
- ❌ "Cannot connect to BlackPearl database"
- ❌ "Cannot connect to Rio database"

## Expected Behavior

### When Connected Successfully
- Buckets will load when you click "Show Buckets"
- Logs will show: "Successfully queried BlackPearl buckets..."
- Frontend will display the bucket list

### When Connection Fails
- Frontend shows: "No buckets found. Check database connections."
- Logs show connection errors with specific details
- Application continues to work (bucket selection is optional)

## Troubleshooting Steps

1. **Verify PostgreSQL is running:**
   ```bash
   ps aux | grep postgres
   ```

2. **Check environment variables:**
   ```bash
   env | grep MT_
   ```

3. **Test manual connection:**
   ```bash
   psql -h localhost -U postgres -d tapesystem
   ```

4. **Check backend logs:**
   ```bash
   tail -50 log/migration_tracker_api.log | grep -i postgres
   ```

5. **Verify database exists:**
   ```bash
   psql -U postgres -l | grep -E "tapesystem|rio_db"
   ```

## Development vs Production

### Development (Local)
- PostgreSQL typically runs on `localhost:5432`
- Default user: `postgres`
- Databases may need to be created

### Production (Remote)
- PostgreSQL may be on a different host
- May require SSL connections
- May require specific firewall rules
- Credentials should be in environment variables (not hardcoded)

## Next Steps

Once PostgreSQL is configured and running:

1. **Restart the backend** to load new configuration
2. **Click "Show Buckets"** in the Gather Data page
3. **Check logs** for connection status
4. **Verify buckets load** in the frontend

If buckets still don't load after fixing connection issues, the problem may be:
- Database schema doesn't match expected table/column names
- Tables are empty
- Permissions issue (user can't SELECT from tables)

Check logs for specific table/column errors.
