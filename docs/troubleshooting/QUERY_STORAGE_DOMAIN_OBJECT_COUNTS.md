# Query Storage Domain Object Counts

These queries check the actual object counts in PostgreSQL for source and target storage domains.

## First: Check the Schema

Before running queries, check what columns exist in `ds3.storage_domain_member`:

```sql
-- Check columns in storage_domain_member
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'ds3'
AND table_name = 'storage_domain_member'
ORDER BY ordinal_position;

-- See sample data
SELECT * FROM ds3.storage_domain_member LIMIT 5;
```

## Query Pattern 1: Storage Domain → Storage Domain Member → Bucket → Objects

**Note**: This assumes `storage_domain_member` has a way to link to buckets. If not, use Pattern 2.

### Source Storage Domain ("Tape First Copy")

```sql
-- Object count
SELECT COUNT(DISTINCT so.id) as object_count
FROM ds3.storage_domain sd
JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id
JOIN ds3.bucket b ON (b.id = sdm.bucket_id OR b.id = sdm.tape_partition_id OR b.id = sdm.pool_partition_id)
JOIN ds3.s3_object so ON so.bucket_id = b.id
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
```

## Query Pattern 2: Direct Bucket Name Matching (Recommended)

If storage_domain_member doesn't link to buckets directly, match bucket names to storage domain names:

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

## Complete Query: Both Storage Domains Side-by-Side (Pattern 2)

```sql
-- Compare source and target in one query using bucket name matching
SELECT 
    'Source (Tape First Copy)' as storage_domain,
    COUNT(DISTINCT so.id) as object_count,
    COALESCE(SUM(bl.length), 0) as total_size_bytes
FROM ds3.bucket b
JOIN ds3.s3_object so ON so.bucket_id = b.id
LEFT JOIN ds3.blob bl ON bl.object_id = so.id
WHERE b.name ILIKE 'Tape First Copy' OR b.name ILIKE 'Tape First Copy%'

UNION ALL

SELECT 
    'Target (Tape First Copy-LTO-10)' as storage_domain,
    COUNT(DISTINCT so.id) as object_count,
    COALESCE(SUM(bl.length), 0) as total_size_bytes
FROM ds3.bucket b
JOIN ds3.s3_object so ON so.bucket_id = b.id
LEFT JOIN ds3.blob bl ON bl.object_id = so.id
WHERE b.name ILIKE 'Tape First Copy-LTO-10' OR b.name ILIKE 'Tape First Copy-LTO-10%';
```

## Helper Queries: Debug Storage Domain Structure

### List all storage domains

```sql
SELECT DISTINCT name 
FROM ds3.storage_domain 
WHERE name IS NOT NULL AND name != '' 
ORDER BY name;
```

### Check storage domain members structure

```sql
-- See what columns exist in storage_domain_member
\d ds3.storage_domain_member

-- Or via SQL:
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'ds3'
AND table_name = 'storage_domain_member'
ORDER BY ordinal_position;
```

### Check which buckets belong to storage domains

```sql
-- See storage domain members
SELECT 
    sd.name as storage_domain_name,
    sdm.*
FROM ds3.storage_domain sd
JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id
WHERE sd.name ILIKE '%Tape First Copy%'
ORDER BY sd.name
LIMIT 20;
```

### Check bucket names that might match

```sql
-- Find buckets with similar names
SELECT DISTINCT b.name as bucket_name, COUNT(DISTINCT so.id) as object_count
FROM ds3.bucket b
LEFT JOIN ds3.s3_object so ON so.bucket_id = b.id
WHERE b.name ILIKE '%Tape First Copy%'
GROUP BY b.name
ORDER BY b.name;
```

## Usage Examples

### Quick Check: Both Storage Domains

```bash
psql -h localhost -p 5432 -U postgres -d tapesystem -c "
SELECT 
    'Source (Tape First Copy)' as storage_domain,
    COUNT(DISTINCT so.id) as object_count,
    COALESCE(SUM(bl.length), 0) as total_size_bytes
FROM ds3.bucket b
JOIN ds3.s3_object so ON so.bucket_id = b.id
LEFT JOIN ds3.blob bl ON bl.object_id = so.id
WHERE b.name ILIKE 'Tape First Copy' OR b.name ILIKE 'Tape First Copy%'

UNION ALL

SELECT 
    'Target (Tape First Copy-LTO-10)' as storage_domain,
    COUNT(DISTINCT so.id) as object_count,
    COALESCE(SUM(bl.length), 0) as total_size_bytes
FROM ds3.bucket b
JOIN ds3.s3_object so ON so.bucket_id = b.id
LEFT JOIN ds3.blob bl ON bl.object_id = so.id
WHERE b.name ILIKE 'Tape First Copy-LTO-10' OR b.name ILIKE 'Tape First Copy-LTO-10%';"
```

## Expected Results

For a migration that just started:
- **Source** should show the full count (e.g., 79,833 objects)
- **Target** should show a lower count (e.g., 0 or a small number if migration has started)

If both show the same count, it might indicate:
1. The queries are matching the same buckets for both source and target
2. The storage domain names are not correctly distinguishing source vs target
3. The migration is actually complete (unlikely if it just started)
