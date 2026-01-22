# Check Data Policy Schema

Please run this to see the actual structure of `ds3.data_policy`:

```sql
\d ds3.data_policy
```

Also check if there's a relationship table between data_policy and storage_domain:

```sql
-- Check for any foreign key references to storage_domain
SELECT 
    tc.table_schema, 
    tc.table_name, 
    kcu.column_name, 
    ccu.table_schema AS foreign_table_schema,
    ccu.table_name AS foreign_table_name,
    ccu.column_name AS foreign_column_name 
FROM information_schema.table_constraints AS tc 
JOIN information_schema.key_column_usage AS kcu
  ON tc.constraint_name = kcu.constraint_name
  AND tc.table_schema = kcu.table_schema
JOIN information_schema.constraint_column_usage AS ccu
  ON ccu.constraint_name = tc.constraint_name
  AND ccu.table_schema = tc.table_schema
WHERE tc.constraint_type = 'FOREIGN KEY' 
  AND ccu.table_name = 'storage_domain';
```

Also check what columns `ds3.data_policy` actually has and how it might relate to storage domains:

```sql
-- See all columns in data_policy
SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_schema = 'ds3' AND table_name = 'data_policy'
ORDER BY ordinal_position;
```
