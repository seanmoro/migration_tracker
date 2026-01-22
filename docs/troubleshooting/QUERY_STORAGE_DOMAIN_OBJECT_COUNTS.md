# Query Storage Domain Object Counts

These queries check the actual object counts in PostgreSQL for source and target storage domains.

## Prerequisites

1. Identify the customer name (e.g., from the phase/project)
2. Identify the storage domain names:
   - Source: "Tape First Copy"
   - Target: "Tape First Copy-LTO-10"
3. Determine the database name:
   - Customer-specific: `tapesystem_<customer_name>` or `rio_db_<customer_name>`
   - Generic fallback: `tapesystem` or `rio_db`

## Query Pattern 1: Storage Domain → Storage Domain Member → Bucket → Objects

This is the primary query pattern used by the application:

### Source Storage Domain ("Tape First Copy")

```sql
-- Object count
SELECT COUNT(DISTINCT so.id) as object_count
FROM ds3.storage_domain sd
JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id
JOIN ds3.bucket b ON (b.id = sdm.bucket_id OR b.id = sdm.tape_partition_id OR b.id = sdm.pool_partition_id)
JOIN ds3.s3_object so ON so.bucket_id = b.id
WHERE sd.name ILIKE 'Tape First Copy';

-- Total size in bytes
SELECT COALESCE(SUM(bl.length), 0) as total_size_bytes
FROM ds3.storage_domain sd
JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id
JOIN ds3.bucket b ON (b.id = sdm.bucket_id OR b.id = sdm.tape_partition_id OR b.id = sdm.pool_partition_id)
JOIN ds3.s3_object so ON so.bucket_id = b.id
LEFT JOIN ds3.blob bl ON bl.object_id = so.id
WHERE sd.name ILIKE 'Tape First Copy';
```

### Target Storage Domain ("Tape First Copy-LTO-10")

```sql
-- Object count
SELECT COUNT(DISTINCT so.id) as object_count
FROM ds3.storage_domain sd
JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id
JOIN ds3.bucket b ON (b.id = sdm.bucket_id OR b.id = sdm.tape_partition_id OR b.id = sdm.pool_partition_id)
JOIN ds3.s3_object so ON so.bucket_id = b.id
WHERE sd.name ILIKE 'Tape First Copy-LTO-10';

-- Total size in bytes
SELECT COALESCE(SUM(bl.length), 0) as total_size_bytes
FROM ds3.storage_domain sd
JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id
JOIN ds3.bucket b ON (b.id = sdm.bucket_id OR b.id = sdm.tape_partition_id OR b.id = sdm.pool_partition_id)
JOIN ds3.s3_object so ON so.bucket_id = b.id
LEFT JOIN ds3.blob bl ON bl.object_id = so.id
WHERE sd.name ILIKE 'Tape First Copy-LTO-10';
```

## Query Pattern 2: Direct Bucket Name Matching

If Pattern 1 doesn't work, try matching bucket names directly:

### Source Storage Domain

```sql
-- Object count
SELECT COUNT(DISTINCT so.id) as object_count
FROM ds3.bucket b
JOIN ds3.s3_object so ON so.bucket_id = b.id
WHERE b.name ILIKE 'Tape First Copy' OR b.name ILIKE 'Tape First Copy%';

-- Total size
SELECT COALESCE(SUM(bl.length), 0) as total_size_bytes
FROM ds3.bucket b
JOIN ds3.s3_object so ON so.bucket_id = b.id
LEFT JOIN ds3.blob bl ON bl.object_id = so.id
WHERE b.name ILIKE 'Tape First Copy' OR b.name ILIKE 'Tape First Copy%';
```

### Target Storage Domain

```sql
-- Object count
SELECT COUNT(DISTINCT so.id) as object_count
FROM ds3.bucket b
JOIN ds3.s3_object so ON so.bucket_id = b.id
WHERE b.name ILIKE 'Tape First Copy-LTO-10' OR b.name ILIKE 'Tape First Copy-LTO-10%';

-- Total size
SELECT COALESCE(SUM(bl.length), 0) as total_size_bytes
FROM ds3.bucket b
JOIN ds3.s3_object so ON so.bucket_id = b.id
LEFT JOIN ds3.blob bl ON bl.object_id = so.id
WHERE b.name ILIKE 'Tape First Copy-LTO-10' OR b.name ILIKE 'Tape First Copy-LTO-10%';
```

## Complete Query: Both Storage Domains Side-by-Side

```sql
-- Compare source and target in one query
SELECT 
    'Source (Tape First Copy)' as storage_domain,
    COUNT(DISTINCT so.id) as object_count,
    COALESCE(SUM(bl.length), 0) as total_size_bytes
FROM ds3.storage_domain sd
JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id
JOIN ds3.bucket b ON (b.id = sdm.bucket_id OR b.id = sdm.tape_partition_id OR b.id = sdm.pool_partition_id)
JOIN ds3.s3_object so ON so.bucket_id = b.id
LEFT JOIN ds3.blob bl ON bl.object_id = so.id
WHERE sd.name ILIKE 'Tape First Copy'

UNION ALL

SELECT 
    'Target (Tape First Copy-LTO-10)' as storage_domain,
    COUNT(DISTINCT so.id) as object_count,
    COALESCE(SUM(bl.length), 0) as total_size_bytes
FROM ds3.storage_domain sd
JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id
JOIN ds3.bucket b ON (b.id = sdm.bucket_id OR b.id = sdm.tape_partition_id OR b.id = sdm.pool_partition_id)
JOIN ds3.s3_object so ON so.bucket_id = b.id
LEFT JOIN ds3.blob bl ON bl.object_id = so.id
WHERE sd.name ILIKE 'Tape First Copy-LTO-10';
```

## Helper Queries: Debug Storage Domain Structure

### List all storage domains

```sql
SELECT DISTINCT name 
FROM ds3.storage_domain 
WHERE name IS NOT NULL AND name != '' 
ORDER BY name;
```

### Check storage domain members

```sql
-- See which buckets/partitions belong to a storage domain
SELECT 
    sd.name as storage_domain_name,
    sdm.bucket_id,
    sdm.tape_partition_id,
    sdm.pool_partition_id,
    b.name as bucket_name
FROM ds3.storage_domain sd
JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id
LEFT JOIN ds3.bucket b ON b.id = sdm.bucket_id
WHERE sd.name ILIKE 'Tape First Copy' OR sd.name ILIKE 'Tape First Copy-LTO-10'
ORDER BY sd.name;
```

### Check bucket names that might match

```sql
-- Find buckets with similar names
SELECT DISTINCT b.name as bucket_name
FROM ds3.bucket b
WHERE b.name ILIKE '%Tape First Copy%'
ORDER BY b.name;
```

## Usage Examples

### For a specific customer database:

```bash
# Replace <customer_name> with actual customer name (lowercase, underscores)
# Example: if customer is "Howard Stern", database is "tapesystem_howard_stern"

psql -h localhost -p 5432 -U postgres -d tapesystem_<customer_name> -c "
SELECT 
    'Source (Tape First Copy)' as storage_domain,
    COUNT(DISTINCT so.id) as object_count,
    COALESCE(SUM(bl.length), 0) as total_size_bytes
FROM ds3.storage_domain sd
JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id
JOIN ds3.bucket b ON (b.id = sdm.bucket_id OR b.id = sdm.tape_partition_id OR b.id = sdm.pool_partition_id)
JOIN ds3.s3_object so ON so.bucket_id = b.id
LEFT JOIN ds3.blob bl ON bl.object_id = so.id
WHERE sd.name ILIKE 'Tape First Copy';"
```

### For generic database:

```bash
psql -h localhost -p 5432 -U postgres -d tapesystem -c "
SELECT 
    'Source (Tape First Copy)' as storage_domain,
    COUNT(DISTINCT so.id) as object_count,
    COALESCE(SUM(bl.length), 0) as total_size_bytes
FROM ds3.storage_domain sd
JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id
JOIN ds3.bucket b ON (b.id = sdm.bucket_id OR b.id = sdm.tape_partition_id OR b.id = sdm.pool_partition_id)
JOIN ds3.s3_object so ON so.bucket_id = b.id
LEFT JOIN ds3.blob bl ON bl.object_id = so.id
WHERE sd.name ILIKE 'Tape First Copy';"
```

## Expected Results

For a migration that just started:
- **Source** should show the full count (e.g., 79,833 objects)
- **Target** should show a lower count (e.g., 0 or a small number if migration has started)

If both show the same count, it might indicate:
1. The queries are matching the same buckets for both source and target
2. The storage domain names are not correctly distinguishing source vs target
3. The migration is actually complete (unlikely if it just started)
