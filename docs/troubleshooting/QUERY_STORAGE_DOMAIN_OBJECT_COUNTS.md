# Query Storage Domain Object Counts

Based on the actual schema, storage domains relate to buckets through data persistence rules.

## Schema Relationship

```
ds3.storage_domain (name: "Tape First Copy")
  ↓
ds3.data_persistence_rule (links storage_domain to data_policy)
  ↓
ds3.data_policy (has the data policy)
  ↓
ds3.bucket (has bucket_id and data_policy_id)
  ↓
ds3.s3_object (objects in the bucket)
```

## Correct Query Pattern

### Source Storage Domain ("Tape First Copy") - Object Count:

```sql
SELECT COUNT(DISTINCT so.id) as object_count
FROM ds3.storage_domain sd
JOIN ds3.data_persistence_rule dpr ON dpr.storage_domain_id = sd.id
JOIN ds3.data_policy dp ON dp.id = dpr.data_policy_id
JOIN ds3.bucket b ON b.data_policy_id = dp.id
JOIN ds3.s3_object so ON so.bucket_id = b.id
WHERE sd.name ILIKE 'Tape First Copy';
```

### Target Storage Domain ("Tape First Copy-LTO-10") - Object Count:

```sql
SELECT COUNT(DISTINCT so.id) as object_count
FROM ds3.storage_domain sd
JOIN ds3.data_persistence_rule dpr ON dpr.storage_domain_id = sd.id
JOIN ds3.data_policy dp ON dp.id = dpr.data_policy_id
JOIN ds3.bucket b ON b.data_policy_id = dp.id
JOIN ds3.s3_object so ON so.bucket_id = b.id
WHERE sd.name ILIKE 'Tape First Copy-LTO-10';
```

### Both Storage Domains Side-by-Side:

```sql
SELECT 
    'Source (Tape First Copy)' as storage_domain,
    COUNT(DISTINCT so.id) as object_count,
    COALESCE(SUM(bl.length), 0) as total_size_bytes
FROM ds3.storage_domain sd
JOIN ds3.data_persistence_rule dpr ON dpr.storage_domain_id = sd.id
JOIN ds3.data_policy dp ON dp.id = dpr.data_policy_id
JOIN ds3.bucket b ON b.data_policy_id = dp.id
JOIN ds3.s3_object so ON so.bucket_id = b.id
LEFT JOIN ds3.blob bl ON bl.object_id = so.id
WHERE sd.name ILIKE 'Tape First Copy'

UNION ALL

SELECT 
    'Target (Tape First Copy-LTO-10)' as storage_domain,
    COUNT(DISTINCT so.id) as object_count,
    COALESCE(SUM(bl.length), 0) as total_size_bytes
FROM ds3.storage_domain sd
JOIN ds3.data_persistence_rule dpr ON dpr.storage_domain_id = sd.id
JOIN ds3.data_policy dp ON dp.id = dpr.data_policy_id
JOIN ds3.bucket b ON b.data_policy_id = dp.id
JOIN ds3.s3_object so ON so.bucket_id = b.id
LEFT JOIN ds3.blob bl ON bl.object_id = so.id
WHERE sd.name ILIKE 'Tape First Copy-LTO-10';
```

## Helper Queries

### Check which buckets belong to a storage domain:

```sql
SELECT DISTINCT
    sd.name as storage_domain_name,
    b.name as bucket_name,
    COUNT(DISTINCT so.id) as object_count
FROM ds3.storage_domain sd
JOIN ds3.data_persistence_rule dpr ON dpr.storage_domain_id = sd.id
JOIN ds3.data_policy dp ON dp.id = dpr.data_policy_id
JOIN ds3.bucket b ON b.data_policy_id = dp.id
LEFT JOIN ds3.s3_object so ON so.bucket_id = b.id
WHERE sd.name ILIKE '%Tape First Copy%'
GROUP BY sd.name, b.name
ORDER BY sd.name, b.name;
```

### List all storage domains:

```sql
SELECT DISTINCT name 
FROM ds3.storage_domain 
WHERE name IS NOT NULL AND name != '' 
ORDER BY name;
```
