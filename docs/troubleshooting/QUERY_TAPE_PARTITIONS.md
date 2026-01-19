# Query Tape Partitions from Database

## Commands to Check Tape Partitions

### 1. Check for Tape Partition Tables
```bash
# List tables with 'partition' or 'tape' in name
sudo -u postgres psql -d tapesystem -c "
SELECT schemaname, tablename 
FROM pg_tables 
WHERE (tablename LIKE '%partition%' OR tablename LIKE '%tape%')
AND schemaname NOT IN ('information_schema', 'pg_catalog')
ORDER BY schemaname, tablename;"
```

### 2. Check Common Table Names
```bash
# Try common table names
sudo -u postgres psql -d tapesystem -c "\d tape.tape_partitions" 2>&1
sudo -u postgres psql -d tapesystem -c "\d ds3.tape_partitions" 2>&1
sudo -u postgres psql -d tapesystem -c "\d public.tape_partitions" 2>&1
```

### 3. Query Tape Partitions (if table exists)
```bash
# Try different schemas and table/column combinations
sudo -u postgres psql -d tapesystem -c "
SELECT DISTINCT name 
FROM tape.tape_partitions 
WHERE name IS NOT NULL 
ORDER BY name;" 2>&1

sudo -u postgres psql -d tapesystem -c "
SELECT DISTINCT name 
FROM ds3.tape_partitions 
WHERE name IS NOT NULL 
ORDER BY name;" 2>&1

sudo -u postgres psql -d tapesystem -c "
SELECT DISTINCT name 
FROM public.tape_partitions 
WHERE name IS NOT NULL 
ORDER BY name;" 2>&1
```

### 4. Find All Columns with 'partition' in Name
```bash
# Find columns that might contain partition data
sudo -u postgres psql -d tapesystem -c "
SELECT table_schema, table_name, column_name, data_type
FROM information_schema.columns
WHERE (column_name LIKE '%partition%' OR column_name LIKE '%tape_partition%')
AND table_schema NOT IN ('information_schema', 'pg_catalog')
ORDER BY table_schema, table_name, column_name;"
```

### 5. Check All Tables in 'tape' Schema
```bash
# List all tables in tape schema
sudo -u postgres psql -d tapesystem -c "
SELECT tablename 
FROM pg_tables 
WHERE schemaname = 'tape'
ORDER BY tablename;"
```

### 6. Query Any Partition-Related Data
```bash
# Try to find partition data in any table
sudo -u postgres psql -d tapesystem -c "
SELECT table_schema, table_name, column_name
FROM information_schema.columns
WHERE column_name LIKE '%partition%'
AND table_schema IN ('tape', 'ds3', 'public')
ORDER BY table_schema, table_name;"
```

## Quick Check Command
```bash
# One-liner to check everything
echo "=== Partition Tables ===" && \
sudo -u postgres psql -d tapesystem -c "
SELECT schemaname, tablename 
FROM pg_tables 
WHERE tablename LIKE '%partition%'
ORDER BY schemaname, tablename;" && \
echo -e "\n=== Partition Columns ===" && \
sudo -u postgres psql -d tapesystem -c "
SELECT table_schema, table_name, column_name
FROM information_schema.columns
WHERE column_name LIKE '%partition%'
AND table_schema IN ('tape', 'ds3', 'public')
ORDER BY table_schema, table_name;"
```
