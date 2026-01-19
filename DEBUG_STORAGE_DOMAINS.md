# Debug Storage Domains

## Issue
Storage domains API returns empty array because the customer-specific database doesn't exist yet.

## Steps to Debug

### 1. Check what databases exist
```bash
# List all PostgreSQL databases
sudo -u postgres psql -d postgres -c "\l" | grep -E "tapesystem|rio_db|Name"

# Or query directly
sudo -u postgres psql -d postgres -c "SELECT datname FROM pg_database WHERE datname LIKE 'tapesystem_%' OR datname LIKE 'rio_db_%' ORDER BY datname;"
```

### 2. Check application logs for connection errors
```bash
# Check for database connection errors
sudo tail -200 log/application.log | grep -A 5 "storage domain\|Querying storage domains\|Error fetching storage domains\|Available tables"

# Check for specific customer database connection attempts
sudo tail -500 log/application.log | grep -B 5 -A 10 "tapesystem_howard_stern\|rio_db_howard_stern"
```

### 3. If database doesn't exist, you need to restore it first
The storage domains feature requires a restored PostgreSQL database. To restore:

1. Go to the Database Upload page in the UI
2. Select the customer (Howard Stern)
3. Select the database type (BlackPearl or Rio)
4. Upload the `.zst` or `.tar` backup file
5. Wait for restore to complete
6. Then try the storage domains API again

### 4. Test the API after restore
```bash
# Get Howard Stern customer ID
CUSTOMER_ID=$(curl -s http://localhost/api/customers | grep -i "howard\|stern" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)
echo "Customer ID: $CUSTOMER_ID"

# Test storage domains API
curl -s "http://localhost/api/phases/storage-domains?customerId=${CUSTOMER_ID}&databaseType=blackpearl" | python3 -m json.tool
```

### 5. If database exists but domains are empty, check the database structure
```bash
# Replace with actual database name (customer name sanitized: lowercase, spaces -> underscores)
DB_NAME="tapesystem_howard_stern"

# List all tables
sudo -u postgres psql -d "$DB_NAME" -c "\dt"

# Find columns with domain/broker/storage in name
sudo -u postgres psql -d "$DB_NAME" -c "
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'public'
AND (column_name LIKE '%domain%' OR column_name LIKE '%broker%' OR column_name LIKE '%storage%')
ORDER BY table_name, column_name;"

# List all tables and their row counts
sudo -u postgres psql -d "$DB_NAME" -c "
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size,
    (SELECT COUNT(*) FROM information_schema.tables t2 WHERE t2.table_schema = schemaname AND t2.table_name = tablename) as exists
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY tablename;"
```

## Expected Behavior

1. **Before restore**: API returns empty domains array (expected)
2. **After restore**: API should query the database and return found storage domains
3. **If no domains found**: Check logs for "Available tables" message to see what tables exist

## Common Issues

1. **Database doesn't exist**: Restore a database backup first
2. **Wrong database name**: Check customer name sanitization (spaces -> underscores, lowercase)
3. **Connection error**: Check PostgreSQL credentials in `.env` file
4. **No matching tables/columns**: The database might have different schema than expected
