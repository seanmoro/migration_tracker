# Check Database Schema for Buckets

## Check What Tables Exist

```bash
# List all tables in BlackPearl database
psql -h localhost -p 5432 -U postgres -d tapesystem -c "\dt"

# List all tables in Rio database
psql -h localhost -p 5432 -U postgres -d rio_db -c "\dt"

# Get detailed table information
psql -h localhost -p 5432 -U postgres -d tapesystem -c "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name;"
```

## Check for Objects Table

The service also tries to query from an `objects` table:

```bash
# Check if objects table exists in BlackPearl
psql -h localhost -p 5432 -U postgres -d tapesystem -c "SELECT COUNT(*) FROM objects;" 2>&1

# Check if objects table exists in Rio
psql -h localhost -p 5432 -U postgres -d rio_db -c "SELECT COUNT(*) FROM objects;" 2>&1

# If objects table exists, check its structure
psql -h localhost -p 5432 -U postgres -d tapesystem -c "\d objects"

# Check if it has bucket_name column
psql -h localhost -p 5432 -U postgres -d tapesystem -c "SELECT column_name FROM information_schema.columns WHERE table_name = 'objects' AND column_name LIKE '%bucket%';"
```

## Check for Bucket Data in Objects Table

If objects table exists with bucket_name:

```bash
# Get unique bucket names from objects
psql -h localhost -p 5432 -U postgres -d tapesystem -c "SELECT DISTINCT bucket_name FROM objects LIMIT 10;"

# Get bucket statistics
psql -h localhost -p 5432 -U postgres -d tapesystem -c "SELECT bucket_name, COUNT(*) as object_count, SUM(size) as size_bytes FROM objects GROUP BY bucket_name ORDER BY bucket_name LIMIT 10;"
```

## Common Table Names

The service looks for:
- `buckets` table with columns: `name`, `object_count`, `size_bytes`
- `objects` table with columns: `bucket_name`, `size`

If your schema is different, you may need to:
1. Create views that match the expected schema
2. Or modify the BucketService to match your actual schema
