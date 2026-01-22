# Query Storage Domain Object Counts

Based on the actual schema, storage domains relate to buckets through pools and tapes.

## Schema Relationship

```
ds3.storage_domain (name: "Tape First Copy")
  ↓
ds3.storage_domain_member (links storage_domain to pool_partition or tape_partition)
  ↓
pool.pool or tape.tape (has storage_domain_member_id and bucket_id)
  ↓
ds3.bucket (has the actual bucket)
  ↓
ds3.s3_object (objects in the bucket)
```

## Correct Query Pattern

### Source Storage Domain ("Tape First Copy") - Object Count:

```sql
SELECT COUNT(DISTINCT so.id) as object_count
FROM ds3.storage_domain sd
JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id
LEFT JOIN pool.pool p ON p.storage_domain_member_id = sdm.id
LEFT JOIN tape.tape t ON t.storage_domain_member_id = sdm.id
LEFT JOIN ds3.bucket b ON (b.id = p.bucket_id OR b.id = t.bucket_id)
JOIN ds3.s3_object so ON so.bucket_id = b.id
WHERE sd.name ILIKE 'Tape First Copy' AND b.id IS NOT NULL;
```

### Target Storage Domain ("Tape First Copy-LTO-10") - Object Count:

```sql
SELECT COUNT(DISTINCT so.id) as object_count
FROM ds3.storage_domain sd
JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id
LEFT JOIN pool.pool p ON p.storage_domain_member_id = sdm.id
LEFT JOIN tape.tape t ON t.storage_domain_member_id = sdm.id
LEFT JOIN ds3.bucket b ON (b.id = p.bucket_id OR b.id = t.bucket_id)
JOIN ds3.s3_object so ON so.bucket_id = b.id
WHERE sd.name ILIKE 'Tape First Copy-LTO-10' AND b.id IS NOT NULL;
```

### Both Storage Domains Side-by-Side:

```sql
SELECT 
    'Source (Tape First Copy)' as storage_domain,
    COUNT(DISTINCT so.id) as object_count,
    COALESCE(SUM(bl.length), 0) as total_size_bytes
FROM ds3.storage_domain sd
JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id
LEFT JOIN pool.pool p ON p.storage_domain_member_id = sdm.id
LEFT JOIN tape.tape t ON t.storage_domain_member_id = sdm.id
LEFT JOIN ds3.bucket b ON (b.id = p.bucket_id OR b.id = t.bucket_id)
JOIN ds3.s3_object so ON so.bucket_id = b.id
LEFT JOIN ds3.blob bl ON bl.object_id = so.id
WHERE sd.name ILIKE 'Tape First Copy' AND b.id IS NOT NULL

UNION ALL

SELECT 
    'Target (Tape First Copy-LTO-10)' as storage_domain,
    COUNT(DISTINCT so.id) as object_count,
    COALESCE(SUM(bl.length), 0) as total_size_bytes
FROM ds3.storage_domain sd
JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id
LEFT JOIN pool.pool p ON p.storage_domain_member_id = sdm.id
LEFT JOIN tape.tape t ON t.storage_domain_member_id = sdm.id
LEFT JOIN ds3.bucket b ON (b.id = p.bucket_id OR b.id = t.bucket_id)
JOIN ds3.s3_object so ON so.bucket_id = b.id
LEFT JOIN ds3.blob bl ON bl.object_id = so.id
WHERE sd.name ILIKE 'Tape First Copy-LTO-10' AND b.id IS NOT NULL;
```

## Helper Queries

### Check which buckets belong to a storage domain:

```sql
SELECT DISTINCT
    sd.name as storage_domain_name,
    b.name as bucket_name,
    COUNT(DISTINCT so.id) as object_count
FROM ds3.storage_domain sd
JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id
LEFT JOIN pool.pool p ON p.storage_domain_member_id = sdm.id
LEFT JOIN tape.tape t ON t.storage_domain_member_id = sdm.id
LEFT JOIN ds3.bucket b ON (b.id = p.bucket_id OR b.id = t.bucket_id)
LEFT JOIN ds3.s3_object so ON so.bucket_id = b.id
WHERE sd.name ILIKE '%Tape First Copy%' AND b.id IS NOT NULL
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
