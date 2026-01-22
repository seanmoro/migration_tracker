# Query Storage Domain Objects by Tape (Correct Method)

## Problem
When counting objects by storage domain, we need to count only objects that are **actually stored on tapes in that storage domain**, not just objects in buckets that belong to the storage domain.

## Correct Query: Objects and Size by Storage Domain and Bucket

This query counts objects that are actually stored on tapes in each storage domain:

```sql
SELECT DISTINCT
    sd.name as storage_domain_name,
    b.name as bucket_name,
    COUNT(DISTINCT so.id) as object_count,
    COALESCE(SUM(bl.length), 0) as total_size_bytes
FROM ds3.storage_domain sd
JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id
JOIN tape.tape t ON t.storage_domain_member_id = sdm.id
JOIN ds3.bucket b ON b.id = t.bucket_id
JOIN ds3.s3_object so ON so.bucket_id = b.id
LEFT JOIN ds3.blob bl ON bl.object_id = so.id
WHERE sd.name ILIKE '%Tape First Copy%'
GROUP BY sd.name, b.name
ORDER BY sd.name, b.name;
```

## Alternative: If objects are linked to tapes through blob

If the relationship is through blob -> tape, try this:

```sql
SELECT DISTINCT
    sd.name as storage_domain_name,
    b.name as bucket_name,
    COUNT(DISTINCT so.id) as object_count,
    COALESCE(SUM(bl.length), 0) as total_size_bytes
FROM ds3.storage_domain sd
JOIN ds3.storage_domain_member sdm ON sdm.storage_domain_id = sd.id
JOIN tape.tape t ON t.storage_domain_member_id = sdm.id
JOIN ds3.blob bl ON bl.tape_id = t.id  -- or whatever column links blob to tape
JOIN ds3.s3_object so ON so.id = bl.object_id
JOIN ds3.bucket b ON b.id = so.bucket_id
WHERE sd.name ILIKE '%Tape First Copy%'
GROUP BY sd.name, b.name
ORDER BY sd.name, b.name;
```

## Check blob table structure

First, check how blobs relate to tapes:

```sql
\d ds3.blob
```

Look for columns like `tape_id`, `tape_partition_id`, or similar that link blobs to tapes.

## Check tape table structure

Also check the tape table to see how it links to objects:

```sql
\d tape.tape
```
