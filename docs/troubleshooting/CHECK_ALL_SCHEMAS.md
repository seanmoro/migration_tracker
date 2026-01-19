# Check All Schemas in BlackPearl Database

## Check All Schemas

```bash
# List all schemas
psql -h localhost -p 5432 -U postgres -d tapesystem -c "\dn"

# Check tables in all schemas
psql -h localhost -p 5432 -U postgres -d tapesystem -c "SELECT schemaname, tablename FROM pg_tables ORDER BY schemaname, tablename;"

# Check for any table with "bucket" in the name
psql -h localhost -p 5432 -U postgres -d tapesystem -c "SELECT schemaname, tablename FROM pg_tables WHERE tablename LIKE '%bucket%' OR tablename LIKE '%object%';"
```

## Check Application Logs

When you click "Show Buckets" in the UI, check what the service finds:

```bash
# Check recent bucket query attempts
sudo tail -50 log/application.log | grep -i "blackpearl\|bucket\|table"

# Watch logs in real-time while clicking "Show Buckets"
sudo tail -f log/application.log | grep -i "blackpearl\|bucket\|table"
```

## Possible Scenarios

1. **Database is empty/new**: The database exists but hasn't been populated with bucket data yet
2. **Different schema**: Buckets might be in a different schema (not `public`)
3. **Different database**: Bucket data might be in a different database
4. **Different table names**: Tables might have different names than expected

## Next Steps

If no bucket tables are found:
- The service will return an empty array (which is expected)
- Bucket selection is optional - you can still gather migration data without it
- Once bucket data is added to the database, it will automatically appear
