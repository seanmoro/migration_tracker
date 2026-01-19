# Query tapesystem Database for Storage Domains

## Current Situation
- Restored cluster has `tapesystem` database (generic)
- Application is trying to connect to `tapesystem_howard_stern` (doesn't exist)
- Need to query `tapesystem` database instead

## Commands to Check Storage Domains in tapesystem

### 1. List Tables in tapesystem Database
```bash
sudo -u postgres psql -d tapesystem -c "\dt"
```

### 2. Find Domain/Broker/Storage Columns
```bash
sudo -u postgres psql -d tapesystem -c "
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'public'
AND (column_name LIKE '%domain%' OR column_name LIKE '%broker%' OR column_name LIKE '%storage%')
ORDER BY table_name, column_name;"
```

### 3. Query Common Storage Domain Tables
```bash
# Try storage_domains table
sudo -u postgres psql -d tapesystem -c "SELECT * FROM storage_domains LIMIT 10;" 2>&1

# Try domains table
sudo -u postgres psql -d tapesystem -c "SELECT * FROM domains LIMIT 10;" 2>&1

# Try brokers table
sudo -u postgres psql -d tapesystem -c "SELECT * FROM brokers LIMIT 10;" 2>&1
```

### 4. Get Distinct Storage Domain Values
```bash
# If storage_domains table exists
sudo -u postgres psql -d tapesystem -c "SELECT DISTINCT name FROM storage_domains WHERE name IS NOT NULL ORDER BY name;" 2>&1

# If domains table exists
sudo -u postgres psql -d tapesystem -c "SELECT DISTINCT name FROM domains WHERE name IS NOT NULL ORDER BY name;" 2>&1
```

## Solution Options

### Option 1: Update StorageDomainService to Fallback to Generic Database
Modify `StorageDomainService` to:
1. Try customer-specific database first (`tapesystem_howard_stern`)
2. If it doesn't exist, fallback to generic `tapesystem` database
3. Query storage domains from whichever database exists

### Option 2: Create Customer-Specific Database and Copy Data
1. Create `tapesystem_howard_stern` database
2. Copy all data from `tapesystem` to `tapesystem_howard_stern`
3. Query the customer-specific database

### Option 3: Use Generic Database for All Data Directory Restores
For data directory restores, always query the generic `tapesystem` database instead of trying to create customer-specific ones.

## Recommended: Option 1 (Fallback)
This is the most flexible - it works for both:
- Database restores (customer-specific databases)
- Data directory restores (generic database)
