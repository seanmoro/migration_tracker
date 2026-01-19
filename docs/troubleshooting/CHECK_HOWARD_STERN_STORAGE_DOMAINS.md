# Check Storage Domains for Howard Stern

## Quick Commands

### 1. Get Howard Stern Customer ID
```bash
# Get customer ID (no jq needed)
CUSTOMER_ID=$(curl -s http://localhost/api/customers | grep -i "howard\|stern" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)
echo "Customer ID: $CUSTOMER_ID"

# Or list all customers to find it manually
curl -s http://localhost/api/customers | grep -i "howard\|stern"
```

### 2. Test Storage Domains API
```bash
# Set customer ID (replace with actual ID from step 1)
CUSTOMER_ID="YOUR_CUSTOMER_ID_HERE"

# Test BlackPearl storage domains
curl -s "http://localhost/api/phases/storage-domains?customerId=${CUSTOMER_ID}&databaseType=blackpearl"

# Test Rio storage domains (if applicable)
curl -s "http://localhost/api/phases/storage-domains?customerId=${CUSTOMER_ID}&databaseType=rio"

# Pretty print with Python (if available)
curl -s "http://localhost/api/phases/storage-domains?customerId=${CUSTOMER_ID}&databaseType=blackpearl" | python3 -m json.tool
```

### 3. Check Database Directly
```bash
# Database name should be: tapesystem_howard_stern (or rio_db_howard_stern)
DB_NAME="tapesystem_howard_stern"

# Verify database exists
sudo -u postgres psql -d postgres -c "SELECT datname FROM pg_database WHERE datname LIKE '%howard%' OR datname LIKE '%stern%';"

# List all tables in the database
sudo -u postgres psql -d "$DB_NAME" -c "\dt"

# Find columns with domain/broker/storage in name
sudo -u postgres psql -d "$DB_NAME" -c "
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'public'
AND (column_name LIKE '%domain%' OR column_name LIKE '%broker%' OR column_name LIKE '%storage%')
ORDER BY table_name, column_name;"

# Query common storage domain tables
sudo -u postgres psql -d "$DB_NAME" -c "SELECT * FROM storage_domains LIMIT 10;" 2>&1
sudo -u postgres psql -d "$DB_NAME" -c "SELECT * FROM domains LIMIT 10;" 2>&1
sudo -u postgres psql -d "$DB_NAME" -c "SELECT * FROM brokers LIMIT 10;" 2>&1
```

### 4. Check Application Logs
```bash
# Check for storage domain queries
sudo tail -200 log/application.log | grep -A 10 "storage domain\|Querying storage domains\|Found.*storage domains\|Available tables"

# Check for Howard Stern specifically
sudo tail -500 log/application.log | grep -B 5 -A 10 "howard_stern\|Howard Stern"

# Watch logs in real-time while testing
sudo tail -f log/application.log | grep -i "storage\|domain\|howard"
```

### 5. One-Liner to Test Everything
```bash
# Get customer ID and test API in one go
CUSTOMER_ID=$(curl -s http://localhost/api/customers | grep -i "howard\|stern" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4) && \
echo "Customer ID: $CUSTOMER_ID" && \
echo "Testing BlackPearl storage domains:" && \
curl -s "http://localhost/api/phases/storage-domains?customerId=${CUSTOMER_ID}&databaseType=blackpearl" && \
echo -e "\n\nChecking database:" && \
sudo -u postgres psql -d postgres -c "SELECT datname FROM pg_database WHERE datname LIKE '%howard%' OR datname LIKE '%stern%';"
```

## Expected Results

**If storage domains are found:**
```json
{
  "domains": ["domain1", "domain2", "domain3"],
  "suggestedSource": "domain1",
  "suggestedTarget": "domain3",
  "suggestedTapePartition": "partition_name"
}
```

**If no storage domains found (but database exists):**
```json
{
  "domains": [],
  "suggestedSource": "BlackPearl",
  "suggestedTarget": "BlackPearl",
  "suggestedTapePartition": null
}
```

**If database doesn't exist:**
- Check logs for "Cannot connect to database" error
- Verify the restore completed successfully
- Check database name matches customer name (lowercase, spaces -> underscores)
