# Test Storage Domains for Howard Stern

## Correct Customer ID
Howard Stern's customer ID: `083a011a-9439-4119-92bb-c6550c773063`

## Commands to Test

### 1. Test Storage Domains API with Correct ID
```bash
# Use the correct Howard Stern customer ID
HOWARD_ID="083a011a-9439-4119-92bb-c6550c773063"

# Test BlackPearl storage domains
curl -s "http://localhost/api/phases/storage-domains?customerId=${HOWARD_ID}&databaseType=blackpearl"

# Pretty print
curl -s "http://localhost/api/phases/storage-domains?customerId=${HOWARD_ID}&databaseType=blackpearl" | python3 -m json.tool 2>/dev/null || curl -s "http://localhost/api/phases/storage-domains?customerId=${HOWARD_ID}&databaseType=blackpearl"
```

### 2. Check What Database Name Should Be Created
```bash
# Customer name: "Howard Stern"
# Sanitized: "howard_stern" (lowercase, space -> underscore)
# Expected database: "tapesystem_howard_stern" or "rio_db_howard_stern"

# Check if it exists
sudo -u postgres psql -d postgres -c "SELECT datname FROM pg_database WHERE datname = 'tapesystem_howard_stern' OR datname = 'rio_db_howard_stern';"
```

### 3. Check All Databases (in case restore used different name)
```bash
# List all databases
sudo -u postgres psql -d postgres -c "\l" | grep -v template
```

### 4. Check Application Logs for Howard Stern Restore
```bash
# Search for Howard Stern in logs
sudo tail -1000 log/application.log | grep -i "howard\|083a011a" -A 10 -B 5

# Check for database creation
sudo tail -1000 log/application.log | grep -i "create.*database\|tapesystem_howard\|rio_db_howard" -A 5
```

### 5. If Database Doesn't Exist, Check Restore Status
```bash
# Check if restore was attempted
sudo tail -2000 log/application.log | grep -i "restore\|083a011a\|howard" | tail -50
```

## Expected Database Name
- Customer: "Howard Stern"
- Sanitized name: `howard_stern` (lowercase, space -> underscore, special chars removed)
- BlackPearl database: `tapesystem_howard_stern`
- Rio database: `rio_db_howard_stern`

## If Database Doesn't Exist
The restore may not have completed successfully. Check:
1. Restore logs for errors
2. Whether the restore was actually performed for Howard Stern
3. If the restore used a different database name
