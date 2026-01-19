# Verify Howard Stern Restore

## After Restore Completes

### 1. Check if Database Was Created
```bash
# Check for Howard Stern database
sudo -u postgres psql -d postgres -c "SELECT datname FROM pg_database WHERE datname LIKE '%howard%' OR datname LIKE '%stern%' OR datname LIKE 'tapesystem_%' OR datname LIKE 'rio_db_%';"

# Or list all databases
sudo -u postgres psql -d postgres -c "\l" | grep -E "tapesystem|rio_db|howard|stern"
```

### 2. Check Restore Logs
```bash
# Check recent restore logs
sudo tail -200 log/application.log | grep -i "restore\|howard\|083a011a\|database.*created" -A 5

# Check for success message
sudo tail -500 log/application.log | grep -i "restore.*complete\|restore.*success\|database.*created" -A 3
```

### 3. Test Storage Domains API
```bash
# Use correct Howard Stern customer ID
HOWARD_ID="083a011a-9439-4119-92bb-c6550c773063"

# Test BlackPearl
curl -s "http://localhost/api/phases/storage-domains?customerId=${HOWARD_ID}&databaseType=blackpearl"

# Test Rio (if applicable)
curl -s "http://localhost/api/phases/storage-domains?customerId=${HOWARD_ID}&databaseType=rio"
```

### 4. If Database Exists, Check Its Contents
```bash
# Replace with actual database name from step 1
DB_NAME="tapesystem_howard_stern"  # or "rio_db_howard_stern"

# List tables
sudo -u postgres psql -d "$DB_NAME" -c "\dt"

# Check for storage domain tables
sudo -u postgres psql -d "$DB_NAME" -c "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND (table_name LIKE '%domain%' OR table_name LIKE '%broker%' OR table_name LIKE '%storage%') ORDER BY table_name;"
```

## Expected Results

**If restore succeeded:**
- Database `tapesystem_howard_stern` or `rio_db_howard_stern` should exist
- Storage domains API should return domains (or empty array if none found in database)
- Logs should show "Restore completed successfully" or similar

**If restore failed:**
- No customer-specific database
- Logs will show error messages
- Storage domains API will return empty array with default suggestions
