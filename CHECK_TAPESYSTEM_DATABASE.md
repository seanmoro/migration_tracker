# Check tapesystem Database Contents

## Database Has Data (3138 MB) But Only One Table Visible

This suggests tables might be in a different schema or there's a permissions issue.

### 1. Check All Schemas
```bash
# List all schemas
sudo -u postgres psql -d tapesystem -c "\dn"

# List tables in all schemas
sudo -u postgres psql -d tapesystem -c "
SELECT schemaname, tablename 
FROM pg_tables 
WHERE schemaname != 'information_schema' 
AND schemaname != 'pg_catalog'
ORDER BY schemaname, tablename;"
```

### 2. Check Table Count
```bash
# Count tables in public schema
sudo -u postgres psql -d tapesystem -c "
SELECT COUNT(*) as table_count 
FROM pg_tables 
WHERE schemaname = 'public';"

# Count all tables
sudo -u postgres psql -d tapesystem -c "
SELECT COUNT(*) as total_tables 
FROM pg_tables 
WHERE schemaname NOT IN ('information_schema', 'pg_catalog');"
```

### 3. Check for Storage Domain Related Tables
```bash
# Find tables with 'domain', 'broker', or 'storage' in name
sudo -u postgres psql -d tapesystem -c "
SELECT schemaname, tablename 
FROM pg_tables 
WHERE (tablename LIKE '%domain%' 
   OR tablename LIKE '%broker%' 
   OR tablename LIKE '%storage%')
AND schemaname NOT IN ('information_schema', 'pg_catalog')
ORDER BY schemaname, tablename;"
```

### 4. Check for Domain/Broker Columns in Any Table
```bash
# Find columns with domain/broker/storage in name
sudo -u postgres psql -d tapesystem -c "
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'public'
AND (column_name LIKE '%domain%' 
  OR column_name LIKE '%broker%' 
  OR column_name LIKE '%storage%')
ORDER BY table_name, column_name;"
```

### 5. List First 20 Tables
```bash
# See what tables actually exist
sudo -u postgres psql -d tapesystem -c "
SELECT tablename 
FROM pg_tables 
WHERE schemaname = 'public'
ORDER BY tablename
LIMIT 20;"
```

### 6. Check Database Owner
```bash
# Check who owns the database and tables
sudo -u postgres psql -d tapesystem -c "
SELECT datname, datdba::regrole as owner 
FROM pg_database 
WHERE datname = 'tapesystem';"

sudo -u postgres psql -d tapesystem -c "
SELECT tablename, tableowner 
FROM pg_tables 
WHERE schemaname = 'public'
LIMIT 10;"
```

## Most Likely Issue

The database has data (3GB) but tables aren't showing. This could be:
1. **Tables in different schema** - Check all schemas
2. **Owner mismatch** - Tables owned by "Administrator" but querying as "postgres"
3. **Search path issue** - PostgreSQL not looking in the right schema

## Quick Check
```bash
# One-liner to check everything
echo "=== Schemas ===" && \
sudo -u postgres psql -d tapesystem -c "\dn" && \
echo -e "\n=== Table Count ===" && \
sudo -u postgres psql -d tapesystem -c "SELECT COUNT(*) FROM pg_tables WHERE schemaname = 'public';" && \
echo -e "\n=== First 10 Tables ===" && \
sudo -u postgres psql -d tapesystem -c "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename LIMIT 10;"
```
