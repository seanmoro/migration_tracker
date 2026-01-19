# Configuring Remote PostgreSQL Databases

## Quick Setup

BlackPearl and Rio databases are typically on remote servers. Configure them using environment variables:

```bash
# BlackPearl Database (replace with actual server details)
export MT_BLACKPEARL_HOST=blackpearl-server.example.com
export MT_BLACKPEARL_PORT=5432
export MT_BLACKPEARL_DATABASE=tapesystem
export MT_BLACKPEARL_USERNAME=postgres
export MT_BLACKPEARL_PASSWORD=your_blackpearl_password

# Rio Database (replace with actual server details)
export MT_RIO_HOST=rio-server.example.com
export MT_RIO_PORT=5432
export MT_RIO_DATABASE=rio_db
export MT_RIO_USERNAME=postgres
export MT_RIO_PASSWORD=your_rio_password
```

Then restart the backend:
```bash
cd backend
mvn spring-boot:run
```

## Finding Your Database Server Details

### Option 1: Check tracker.yaml
If you have a `tracker.yaml` file, it may contain database connection info:
```bash
cat tracker.yaml | grep -A 5 "blackpearl\|rio"
```

### Option 2: Check Existing Scripts
Look at your existing migration tracker scripts for database connection details:
```bash
grep -r "postgres\|5432" bin/ resources/scripts/
```

### Option 3: Ask Your Team
Contact your team or check documentation for:
- BlackPearl server hostname/IP
- Rio server hostname/IP
- Database names
- Username/password credentials

## Testing Connection

Once configured, test the connection manually:
```bash
# Test BlackPearl connection
psql -h blackpearl-server.example.com -U postgres -d tapesystem

# Test Rio connection
psql -h rio-server.example.com -U postgres -d rio_db
```

**Note:** You may need to install PostgreSQL client tools:
```bash
# macOS
brew install postgresql

# Or use Docker
docker run -it --rm postgres:14 psql -h blackpearl-server.example.com -U postgres -d tapesystem
```

## Alternative: Local PostgreSQL for Testing

If you want to test locally without remote databases:

### Install PostgreSQL (macOS)
```bash
brew install postgresql@14
brew services start postgresql@14
```

### Create Test Databases
```bash
# Connect to local PostgreSQL
psql postgres

# Create databases
CREATE DATABASE tapesystem;
CREATE DATABASE rio_db;

# Create test buckets table (example schema)
\c tapesystem
CREATE TABLE buckets (
    name VARCHAR(255) PRIMARY KEY,
    object_count BIGINT DEFAULT 0,
    size_bytes BIGINT DEFAULT 0
);

-- Insert test data
INSERT INTO buckets (name, object_count, size_bytes) VALUES
    ('test-bucket-1', 1000, 1073741824),
    ('test-bucket-2', 2000, 2147483648);

\q
```

### Configure for Local Testing
```bash
export MT_BLACKPEARL_HOST=localhost
export MT_BLACKPEARL_PORT=5432
export MT_BLACKPEARL_DATABASE=tapesystem
export MT_BLACKPEARL_USERNAME=postgres
export MT_BLACKPEARL_PASSWORD=  # Leave empty if no password

export MT_RIO_HOST=localhost
export MT_RIO_PORT=5432
export MT_RIO_DATABASE=rio_db
export MT_RIO_USERNAME=postgres
export MT_RIO_PASSWORD=  # Leave empty if no password
```

## Troubleshooting Remote Connections

### Connection Refused
- Check if the server is accessible: `ping blackpearl-server.example.com`
- Check if port 5432 is open: `nc -zv blackpearl-server.example.com 5432`
- Verify firewall rules allow connections

### Authentication Failed
- Double-check username and password
- Verify the user has access to the database
- Check PostgreSQL `pg_hba.conf` allows your IP

### Database Does Not Exist
- Verify database names are correct
- Check if databases need to be created
- Verify user has permissions

### SSL/TLS Required
If the remote server requires SSL:
```bash
export MT_BLACKPEARL_SSL=true
export MT_RIO_SSL=true
```

(Note: SSL support may need to be added to the application)

## Making Bucket Selection Optional

The bucket selection feature is **optional**. The application will work without PostgreSQL connections:
- You can still gather data (it will track all buckets)
- You just won't be able to select specific buckets
- The "No buckets found" message is expected if databases aren't configured

## Next Steps

1. **Get database server details** from your team/documentation
2. **Set environment variables** with correct hostnames
3. **Test connections** manually with `psql`
4. **Restart backend** to load new configuration
5. **Check logs** for connection status
6. **Try "Show Buckets"** in the UI
