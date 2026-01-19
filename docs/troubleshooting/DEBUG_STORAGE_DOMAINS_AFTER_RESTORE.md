# Debug Storage Domains After Restore

## Check What Was Restored

### 1. Check What Databases Exist
```bash
# List all databases
sudo -u postgres psql -d postgres -c "\l" | grep -E "tapesystem|rio_db|Name"

# Or query directly
sudo -u postgres psql -d postgres -c "SELECT datname FROM pg_database WHERE datname LIKE 'tapesystem%' OR datname LIKE 'rio_db%' ORDER BY datname;"
```

### 2. Test Storage Domains API
```bash
# Use Howard Stern customer ID
HOWARD_ID="083a011a-9439-4119-92bb-c6550c773063"

# Test the API
curl -s "http://localhost/api/phases/storage-domains?customerId=${HOWARD_ID}&databaseType=blackpearl"

# Pretty print if Python available
curl -s "http://localhost/api/phases/storage-domains?customerId=${HOWARD_ID}&databaseType=blackpearl" | python3 -m json.tool 2>/dev/null || curl -s "http://localhost/api/phases/storage-domains?customerId=${HOWARD_ID}&databaseType=blackpearl"
```

### 3. Check Application Logs
```bash
# Check for storage domain queries
sudo tail -200 log/application.log | grep -A 10 "storage domain\|Querying storage domains\|Found.*storage domains\|Available tables\|generic database"

# Check for connection attempts
sudo tail -200 log/application.log | grep -A 5 "tapesystem_howard_stern\|tapesystem\|Cannot connect"
```

### 4. Check Database Contents
```bash
# Determine which database to check
# If customer-specific exists:
DB_NAME="tapesystem_howard_stern"

# Or if only generic exists:
DB_NAME="tapesystem"

# List all tables
sudo -u postgres psql -d "$DB_NAME" -c "\dt"

# Find domain/broker/storage columns
sudo -u postgres psql -d "$DB_NAME" -c "
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'public'
AND (column_name LIKE '%domain%' OR column_name LIKE '%broker%' OR column_name LIKE '%storage%')
ORDER BY table_name, column_name;"
```

### 5. Query Storage Domains Directly
```bash
# Try common table/column combinations
sudo -u postgres psql -d "$DB_NAME" -c "SELECT DISTINCT name FROM storage_domains WHERE name IS NOT NULL ORDER BY name;" 2>&1
sudo -u postgres psql -d "$DB_NAME" -c "SELECT DISTINCT name FROM domains WHERE name IS NOT NULL ORDER BY name;" 2>&1
sudo -u postgres psql -d "$DB_NAME" -c "SELECT DISTINCT name FROM brokers WHERE name IS NOT NULL ORDER BY name;" 2>&1

# Or find any column with domain values
sudo -u postgres psql -d "$DB_NAME" -c "
SELECT DISTINCT storage_domain 
FROM objects 
WHERE storage_domain IS NOT NULL 
ORDER BY storage_domain;" 2>&1
```

### 6. Check All Tables and Their Row Counts
```bash
# See what tables exist and if they have data
sudo -u postgres psql -d "$DB_NAME" -c "
SELECT 
    schemaname,
    tablename,
    (SELECT COUNT(*) FROM information_schema.tables t2 
     WHERE t2.table_schema = schemaname AND t2.table_name = tablename) as exists
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY tablename
LIMIT 20;"
```

## Common Issues

1. **Database name mismatch**: Restore created `tapesystem` but API is looking for `tapesystem_howard_stern`
   - Solution: API should fallback to `tapesystem` (we added this)

2. **No storage domain tables**: Database doesn't have `storage_domains`, `domains`, or `brokers` tables
   - Solution: Check what tables exist and update the service to query them

3. **Storage domains in different table/column**: Domains might be in `objects.storage_domain` or similar
   - Solution: Find the actual table/column and update the service

4. **Empty database**: Restore completed but database is empty
   - Solution: Check restore logs to see if restore actually populated data

## Quick Diagnostic Command
```bash
# One-liner to check everything
echo "=== Databases ===" && \
sudo -u postgres psql -d postgres -c "SELECT datname FROM pg_database WHERE datname LIKE 'tapesystem%' OR datname LIKE 'rio_db%';" && \
echo -e "\n=== API Response ===" && \
curl -s "http://localhost/api/phases/storage-domains?customerId=083a011a-9439-4119-92bb-c6550c773063&databaseType=blackpearl" && \
echo -e "\n=== Recent Logs ===" && \
sudo tail -50 log/application.log | grep -i "storage\|domain\|tapesystem" | tail -5
```
