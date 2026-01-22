# Query Objects by Storage Domain and Bucket (with Size)

## Correct Query: Objects Actually Stored on Tapes

The relationship is:
- `ds3.storage_domain` → `ds3.storage_domain_member` → `tape.tape` → `tape.blob_tape` → `ds3.blob` → `ds3.s3_object` → `ds3.bucket`

### Query with Object Count and Size:

```sql
SELECT DISTINCT
    sd.name as storage_domain_name,
    b.name as bucket_name,
    COUNT(DISTINCT so.id) as object_count,
    COALESCE(SUM(bl.length), 0) as total_size_bytes
FROM ds3.storage_domain sd
JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id
JOIN tape.tape t ON t.storage_domain_member_id = sdm.id
JOIN tape.blob_tape bt ON bt.tape_id = t.id
JOIN ds3.blob bl ON bl.id = bt.blob_id
JOIN ds3.s3_object so ON so.id = bl.object_id
JOIN ds3.bucket b ON b.id = so.bucket_id
WHERE sd.name ILIKE '%Tape First Copy%'
GROUP BY sd.name, b.name
ORDER BY sd.name, b.name;
```

### Compare with Current Query (All Objects in Buckets):

This query counts ALL objects in buckets that belong to the storage domain (via data policy):

```sql
SELECT DISTINCT
    sd.name as storage_domain_name,
    b.name as bucket_name,
    COUNT(DISTINCT so.id) as object_count,
    COALESCE(SUM(bl.length), 0) as total_size_bytes
FROM ds3.storage_domain sd
JOIN ds3.data_persistence_rule dpr ON dpr.storage_domain_id = sd.id
JOIN ds3.data_policy dp ON dp.id = dpr.data_policy_id
JOIN ds3.bucket b ON b.data_policy_id = dp.id
LEFT JOIN ds3.s3_object so ON so.bucket_id = b.id
LEFT JOIN ds3.blob bl ON bl.object_id = so.id
WHERE sd.name ILIKE '%Tape First Copy%'
GROUP BY sd.name, b.name
ORDER BY sd.name, b.name;
```

## Key Difference

- **Current query**: Counts all objects in buckets that have a data policy linked to the storage domain
- **Correct query**: Counts only objects that are actually stored on tapes in that storage domain

The difference shows the actual migration progress - objects that have been physically written to tapes in the target storage domain.
