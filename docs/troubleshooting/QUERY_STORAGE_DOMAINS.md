# Query Storage Domains from PostgreSQL Database

## Quick API Test

Test the storage domains API endpoint:

```bash
# Get a customer ID
CUSTOMER_ID=$(curl -s http://localhost/api/customers | jq -r '.[0].id')
echo "Customer ID: $CUSTOMER_ID"

# Query storage domains for BlackPearl
curl -s "http://localhost/api/phases/storage-domains?customerId=${CUSTOMER_ID}&databaseType=blackpearl" | jq

# Query storage domains for Rio
curl -s "http://localhost/api/phases/storage-domains?customerId=${CUSTOMER_ID}&databaseType=rio" | jq
```

## Direct Database Query

Query the PostgreSQL database directly to see what's in there:

### 1. List All Tables

```bash
# For BlackPearl (replace customer_name with actual customer name)
psql -h localhost -p 5432 -U postgres -d tapesystem_customer_name -c "\dt"

# For Rio
psql -h localhost -p 5432 -U postgres -d rio_db_customer_name -c "\dt"

# Or get table names programmatically
psql -h localhost -p 5432 -U postgres -d tapesystem_customer_name -c "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name;"
```

### 2. Find Domain/Broker Related Columns

```bash
# Find all columns with 'domain', 'broker', or 'storage' in the name
psql -h localhost -p 5432 -U postgres -d tapesystem_customer_name -c "
SELECT table_name, column_name, data_type 
FROM information_schema.columns 
WHERE table_schema = 'public' 
AND (column_name LIKE '%domain%' OR column_name LIKE '%broker%' OR column_name LIKE '%storage%')
ORDER BY table_name, column_name;"
```

### 3. Query Common Storage Domain Tables

```bash
# Try storage_domains table
psql -h localhost -p 5432 -U postgres -d tapesystem_customer_name -c "SELECT * FROM storage_domains LIMIT 10;" 2>&1

# Try domains table
psql -h localhost -p 5432 -U postgres -d tapesystem_customer_name -c "SELECT * FROM domains LIMIT 10;" 2>&1

# Try brokers table
psql -h localhost -p 5432 -U postgres -d tapesystem_customer_name -c "SELECT * FROM brokers LIMIT 10;" 2>&1
```

### 4. Query Domain Columns in Any Table

```bash
# Find tables with domain-related columns and query them
psql -h localhost -p 5432 -U postgres -d tapesystem_customer_name -c "
SELECT 
    t.table_name,
    c.column_name,
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_name = t.table_name) as column_count
FROM information_schema.tables t
JOIN information_schema.columns c ON t.table_name = c.table_name
WHERE t.table_schema = 'public'
AND (c.column_name LIKE '%domain%' OR c.column_name LIKE '%broker%')
ORDER BY t.table_name, c.column_name;"
```

### 5. Get Distinct Values from Domain Columns

Once you find a table/column with domain data:

```bash
# Replace TABLE_NAME and COLUMN_NAME with actual values
psql -h localhost -p 5432 -U postgres -d tapesystem_customer_name -c "
SELECT DISTINCT COLUMN_NAME 
FROM TABLE_NAME 
WHERE COLUMN_NAME IS NOT NULL 
AND COLUMN_NAME != '' 
ORDER BY COLUMN_NAME;"
```

## Check Application Logs

The application logs what it finds when querying storage domains:

```bash
# Watch logs in real-time while testing
sudo tail -f log/application.log | grep -i "storage\|domain"

# Or check recent logs
sudo tail -200 log/application.log | grep -A 5 -B 5 "storage domain\|StorageDomain"
```

Look for messages like:
- `Querying storage domains from blackpearl database: ...`
- `Found X storage domains from table.column`
- `Available tables in database: ...`
- `Found potential domain/broker columns: ...`

## Example: Find Customer Database Name

First, find the customer database name:

```bash
# List all databases
psql -h localhost -p 5432 -U postgres -d postgres -c "\l" | grep tapesystem

# Or for a specific customer (replace customer name)
CUSTOMER_NAME="amc_networks"  # lowercase, underscores
psql -h localhost -p 5432 -U postgres -d postgres -c "SELECT datname FROM pg_database WHERE datname LIKE 'tapesystem_%' OR datname LIKE 'rio_db_%';"
```

## Complete Diagnostic Script

```bash
#!/bin/bash
# Diagnostic script to find storage domains

CUSTOMER_NAME="your_customer_name"  # Replace with actual customer name
DB_TYPE="blackpearl"  # or "rio"

if [ "$DB_TYPE" = "blackpearl" ]; then
    DB_NAME="tapesystem_${CUSTOMER_NAME}"
else
    DB_NAME="rio_db_${CUSTOMER_NAME}"
fi

echo "=========================================="
echo "Storage Domain Diagnostic"
echo "=========================================="
echo "Database: $DB_NAME"
echo ""

# 1. List all tables
echo "1. All tables in database:"
psql -h localhost -p 5432 -U postgres -d "$DB_NAME" -c "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name;"
echo ""

# 2. Find domain/broker columns
echo "2. Columns with 'domain', 'broker', or 'storage' in name:"
psql -h localhost -p 5432 -U postgres -d "$DB_NAME" -c "
SELECT table_name, column_name 
FROM information_schema.columns 
WHERE table_schema = 'public' 
AND (column_name LIKE '%domain%' OR column_name LIKE '%broker%' OR column_name LIKE '%storage%')
ORDER BY table_name, column_name;"
echo ""

# 3. Try common table names
echo "3. Testing common table names:"
for table in storage_domains domains brokers storage_domain domain; do
    echo "  Checking table: $table"
    psql -h localhost -p 5432 -U postgres -d "$DB_NAME" -c "SELECT COUNT(*) FROM $table;" 2>&1 | head -1
done
echo ""

# 4. Sample data from domain columns
echo "4. Sample data from domain-related columns:"
psql -h localhost -p 5432 -U postgres -d "$DB_NAME" -c "
SELECT table_name, column_name 
FROM information_schema.columns 
WHERE table_schema = 'public' 
AND (column_name LIKE '%domain%' OR column_name LIKE '%broker%')
LIMIT 5;" | while read table column; do
    if [ ! -z "$table" ] && [ ! -z "$column" ] && [ "$table" != "table_name" ]; then
        echo "  Querying $table.$column:"
        psql -h localhost -p 5432 -U postgres -d "$DB_NAME" -c "SELECT DISTINCT $column FROM $table WHERE $column IS NOT NULL LIMIT 10;" 2>&1 | head -5
    fi
done

echo ""
echo "=========================================="
echo "Done!"
```

Save as `diagnose_storage_domains.sh`, make executable, and run:
```bash
chmod +x diagnose_storage_domains.sh
./diagnose_storage_domains.sh
```

## What the Service Looks For

The `StorageDomainService` searches for storage domains in:

1. **Common table names:**
   - `storage_domains` table with `name` column
   - `domains` table with `name` column
   - `brokers` table with `name` column

2. **Common column names:**
   - `name`, `domain_name`, `storage_domain`, `domain`, `broker_name`

3. **Any column containing:**
   - "domain" or "broker" in the column name (in any table)

If your database uses different table/column names, you may need to:
- Create views that match the expected schema
- Or modify the `StorageDomainService` to match your actual schema
