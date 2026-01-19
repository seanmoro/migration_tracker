# Check ds3.storage_domain Table

## Found Storage Domain Tables in ds3 Schema

The storage domains are in the `ds3` schema, not `public`. We need to:

1. Check the table structure
2. Update StorageDomainService to query the `ds3` schema

## Commands

### 1. Check storage_domain Table Structure
```bash
# Describe the table
sudo -u postgres psql -d tapesystem -c "\d ds3.storage_domain"

# Or get column info
sudo -u postgres psql -d tapesystem -c "
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'ds3'
AND table_name = 'storage_domain'
ORDER BY ordinal_position;"
```

### 2. Query Storage Domains
```bash
# Get storage domain names
sudo -u postgres psql -d tapesystem -c "
SELECT DISTINCT name 
FROM ds3.storage_domain 
WHERE name IS NOT NULL 
ORDER BY name;" 2>&1

# Or if the column is different
sudo -u postgres psql -d tapesystem -c "
SELECT * 
FROM ds3.storage_domain 
LIMIT 5;" 2>&1
```

### 3. Check All Columns in storage_domain
```bash
# See all columns
sudo -u postgres psql -d tapesystem -c "
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'ds3'
AND table_name = 'storage_domain'
ORDER BY ordinal_position;"
```
