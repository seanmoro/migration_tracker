# Check Restored Database

## Problem
- Database `tapesystem` exists but is empty (only `schema_migrations` table)
- API can't connect due to authentication error
- No storage domain tables found

## Diagnostic Steps

### 1. Check PostgreSQL Connection (Authentication Issue)
```bash
# Test connection with password
export PGPASSWORD="your-password"
psql -h localhost -U postgres -d tapesystem -c "\dt"

# Or without password (if configured)
sudo -u postgres psql -d tapesystem -c "\dt"
```

### 2. Check What Databases Were Actually Restored
```bash
# List ALL databases (not just customer-specific)
sudo -u postgres psql -d postgres -c "\l"

# Check database sizes (to see which ones have data)
sudo -u postgres psql -d postgres -c "
SELECT 
    datname,
    pg_size_pretty(pg_database_size(datname)) as size
FROM pg_database
WHERE datistemplate = false
ORDER BY pg_database_size(datname) DESC;"
```

### 3. Check PostgreSQL Data Directory
```bash
# Check if restore actually populated the data directory
sudo ls -lh /var/lib/postgresql/16/main/base/ | head -20

# Check database OIDs to find which database is which
sudo -u postgres psql -d postgres -c "
SELECT oid, datname 
FROM pg_database 
WHERE datistemplate = false;"

# Then check if that OID directory has data
# For example, if tapesystem has OID 12345:
sudo ls -lh /var/lib/postgresql/16/main/base/12345/ | head -20
```

### 4. Check Restore Logs
```bash
# Check if restore actually completed successfully
sudo tail -500 log/application.log | grep -i "restore.*complete\|restore.*success\|data directory.*copied\|PostgreSQL.*started" | tail -20

# Check for any errors during restore
sudo tail -1000 log/application.log | grep -i "error\|exception\|failed" | grep -i "restore\|copy\|database" | tail -20
```

### 5. Check if Database Has Any Tables
```bash
# List all tables in tapesystem
sudo -u postgres psql -d tapesystem -c "\dt"

# List all schemas
sudo -u postgres psql -d tapesystem -c "\dn"

# Check if there are tables in other schemas
sudo -u postgres psql -d tapesystem -c "
SELECT schemaname, tablename 
FROM pg_tables 
WHERE schemaname != 'information_schema' 
AND schemaname != 'pg_catalog'
ORDER BY schemaname, tablename;"
```

## Possible Issues

1. **Empty Database**: The restore created an empty database
   - Check restore logs to see if data was actually copied
   - Check if the backup file had data

2. **Wrong Database**: The restore might have created/restored a different database
   - Check all databases and their sizes
   - Check which database has the actual data

3. **Authentication**: API can't connect even though database exists
   - Check if password is correct in .env
   - Test connection manually with psql

4. **Data Directory Restore Issue**: The restore might not have populated the database correctly
   - Check if files were actually copied to data directory
   - Check if PostgreSQL started correctly after restore

## Quick Check
```bash
# Check database sizes to see which has data
sudo -u postgres psql -d postgres -c "
SELECT datname, pg_size_pretty(pg_database_size(datname)) as size
FROM pg_database
WHERE datistemplate = false
ORDER BY pg_database_size(datname) DESC;"
```
