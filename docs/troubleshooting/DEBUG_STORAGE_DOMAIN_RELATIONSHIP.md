# Debug Storage Domain Relationship

## Most Likely: Buckets link to Storage Domains through Data Policy

Based on the schema, `ds3.bucket` has a `data_policy_id`, and `ds3.data_policy` has a `storage_domain_id`. This is likely the correct relationship!

### Try this query first:
```sql
SELECT 
    sd.name as storage_domain_name,
    b.name as bucket_name,
    COUNT(DISTINCT so.id) as object_count,
    COALESCE(SUM(bl.length), 0) as total_size_bytes
FROM ds3.storage_domain sd
JOIN ds3.data_policy dp ON dp.storage_domain_id = sd.id
JOIN ds3.bucket b ON b.data_policy_id = dp.id
LEFT JOIN ds3.s3_object so ON so.bucket_id = b.id
LEFT JOIN ds3.blob bl ON bl.object_id = so.id
WHERE sd.name ILIKE '%Tape First Copy%'
GROUP BY sd.name, b.name
ORDER BY sd.name, b.name;
```

### Object count for a specific storage domain:
```sql
SELECT COUNT(DISTINCT so.id) as object_count
FROM ds3.storage_domain sd
JOIN ds3.data_policy dp ON dp.storage_domain_id = sd.id
JOIN ds3.bucket b ON b.data_policy_id = dp.id
JOIN ds3.s3_object so ON so.bucket_id = b.id
WHERE sd.name ILIKE 'Tape First Copy';
```

### Total size for a specific storage domain:
```sql
SELECT COALESCE(SUM(bl.length), 0) as total_size_bytes
FROM ds3.storage_domain sd
JOIN ds3.data_policy dp ON dp.storage_domain_id = sd.id
JOIN ds3.bucket b ON b.data_policy_id = dp.id
JOIN ds3.s3_object so ON so.bucket_id = b.id
LEFT JOIN ds3.blob bl ON bl.object_id = so.id
WHERE sd.name ILIKE 'Tape First Copy';
```

---

## Diagnostic Queries (if data_policy doesn't work)

### Step 1: Check if storage domain exists
```sql
SELECT id, name FROM ds3.storage_domain WHERE name ILIKE '%Tape First Copy%';
```

### Step 2: Check data_policy structure
```sql
\d ds3.data_policy
```

### Step 3: Check if data_policy links to storage domain
```sql
SELECT 
    sd.name as storage_domain_name,
    dp.id as data_policy_id,
    COUNT(b.id) as bucket_count
FROM ds3.storage_domain sd
LEFT JOIN ds3.data_policy dp ON dp.storage_domain_id = sd.id
LEFT JOIN ds3.bucket b ON b.data_policy_id = dp.id
WHERE sd.name ILIKE '%Tape First Copy%'
GROUP BY sd.name, dp.id;
```

### Step 4: Check storage domain members
```sql
SELECT 
    sd.name as storage_domain_name,
    sdm.id as member_id,
    sdm.pool_partition_id,
    sdm.tape_partition_id,
    sdm.state
FROM ds3.storage_domain sd
JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id
WHERE sd.name ILIKE '%Tape First Copy%';
```

### Step 5: Check if pools exist for this storage domain member
```sql
SELECT 
    sd.name as storage_domain_name,
    sdm.id as member_id,
    p.id as pool_id,
    p.bucket_id,
    p.storage_domain_member_id
FROM ds3.storage_domain sd
JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id
LEFT JOIN pool.pool p ON p.storage_domain_member_id = sdm.id
WHERE sd.name ILIKE '%Tape First Copy%';
```

### Step 6: Check if tapes exist for this storage domain member
```sql
SELECT 
    sd.name as storage_domain_name,
    sdm.id as member_id,
    t.id as tape_id,
    t.bucket_id,
    t.storage_domain_member_id
FROM ds3.storage_domain sd
JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id
LEFT JOIN tape.tape t ON t.storage_domain_member_id = sdm.id
WHERE sd.name ILIKE '%Tape First Copy%';
```

### Step 7: Check what buckets have objects
```sql
SELECT 
    b.name as bucket_name,
    COUNT(DISTINCT so.id) as object_count
FROM ds3.bucket b
LEFT JOIN ds3.s3_object so ON so.bucket_id = b.id
GROUP BY b.name
ORDER BY object_count DESC;
```
