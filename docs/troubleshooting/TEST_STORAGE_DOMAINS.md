# Testing Storage Domains from PostgreSQL Database

## Quick Deployment

```bash
cd /home/seans/new_tracker

# 1. Pull latest code
git pull origin main

# 2. Rebuild backend
cd backend && mvn clean package -DskipTests && cd ..

# 3. Rebuild frontend
cd frontend && npm run build && cd ..

# 4. Stop existing process
sudo pkill -f "migration-tracker-api.*jar" || true
sleep 2

# 5. Start application
source .env
sudo -E nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
    --server.port=80 \
    --server.address=0.0.0.0 \
    > log/application.log 2>&1 &

# 6. Wait for startup
sleep 5

# 7. Verify it's running
ps aux | grep migration-tracker
sudo curl http://localhost/api/actuator/health
```

## Testing Steps

### Step 1: Restore a PostgreSQL Database

1. **Navigate to PostgreSQL Restore page** in the UI
2. **Select a customer** from the dropdown
3. **Select database type** (BlackPearl or Rio)
4. **Upload a backup file** (.zst, .tar, .dump, etc.)
5. Wait for restore to complete
6. You should be automatically redirected to Gather Data page

### Step 2: Verify Storage Domains API

Test the storage domains endpoint directly:

```bash
# Replace CUSTOMER_ID and DATABASE_TYPE with actual values
# Get customer ID first:
curl http://localhost/api/customers | jq '.[0].id'

# Test storage domains endpoint (replace with actual customer ID)
CUSTOMER_ID="your-customer-id-here"
DATABASE_TYPE="blackpearl"  # or "rio"

curl "http://localhost/api/phases/storage-domains?customerId=${CUSTOMER_ID}&databaseType=${DATABASE_TYPE}" | jq
```

Expected response:
```json
{
  "domains": ["domain1", "domain2", "domain3"],
  "suggestedSource": "domain1",
  "suggestedTarget": "domain3",
  "suggestedTapePartition": null
}
```

### Step 3: Test in UI

1. **Navigate to Gather Data page** (should happen automatically after restore)
2. **Select a project** (filtered by customer)
3. **Click "Create New Phase"** from the phase dropdown
4. **Verify form is pre-populated**:
   - Source field should have a value from the database
   - Target field should have a value from the database
   - Tape Partition may be empty (if not found in database)

### Step 4: Check Logs

Monitor the logs to see storage domain queries:

```bash
# Watch logs in real-time
sudo tail -f log/application.log | grep -i "storage\|domain"

# Or check recent logs
sudo tail -100 log/application.log | grep -i "storage\|domain"
```

Look for messages like:
- `Querying storage domains from blackpearl database: ...`
- `Found X storage domains from table.column`
- `Found X unique storage domains from blackpearl database`

## Troubleshooting

### No Storage Domains Found

If the API returns empty domains:

1. **Check database connection**:
   ```bash
   # Test connection to customer-specific database
   psql -h localhost -p 5432 -U postgres -d tapesystem_customer_name -c "\dt"
   ```

2. **Check what tables exist**:
   ```bash
   # List all tables
   psql -h localhost -p 5432 -U postgres -d tapesystem_customer_name -c "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name;"
   ```

3. **Check for domain-related columns**:
   ```bash
   # Find columns with 'domain' or 'broker' in name
   psql -h localhost -p 5432 -U postgres -d tapesystem_customer_name -c "SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = 'public' AND (column_name LIKE '%domain%' OR column_name LIKE '%broker%') ORDER BY table_name, column_name;"
   ```

4. **Check application logs** for specific errors:
   ```bash
   sudo tail -200 log/application.log | grep -A 5 -B 5 "storage domain\|StorageDomain"
   ```

### Database Connection Issues

If you see connection errors:

1. **Verify database exists**:
   ```bash
   psql -h localhost -p 5432 -U postgres -d postgres -c "\l" | grep tapesystem
   ```

2. **Check database credentials in .env**:
   ```bash
   cat .env | grep MT_BLACKPEARL
   cat .env | grep MT_RIO
   ```

3. **Test connection manually**:
   ```bash
   psql -h localhost -p 5432 -U postgres -d tapesystem_customer_name -c "SELECT 1;"
   ```

### Form Not Pre-populated

If the phase form is not pre-populated:

1. **Check browser console** for errors:
   - Open browser DevTools (F12)
   - Check Console tab for API errors
   - Check Network tab for failed requests

2. **Verify customer ID and database type are passed**:
   - Check URL parameters in Network tab
   - Should see: `/api/phases/storage-domains?customerId=...&databaseType=...`

3. **Check React Query cache**:
   - In browser console: `window.__REACT_QUERY_STATE__`
   - Look for `storage-domains` query

## Manual Database Query Test

If you want to manually test what the service would find:

```bash
# Connect to customer-specific database
psql -h localhost -p 5432 -U postgres -d tapesystem_customer_name

# Try common queries the service uses:
-- Check for storage_domains table
SELECT * FROM storage_domains LIMIT 10;

-- Check for domains table
SELECT * FROM domains LIMIT 10;

-- Check for brokers table
SELECT * FROM brokers LIMIT 10;

-- Find any column with 'domain' in name
SELECT table_name, column_name 
FROM information_schema.columns 
WHERE table_schema = 'public' 
AND column_name LIKE '%domain%';

-- Find any column with 'broker' in name
SELECT table_name, column_name 
FROM information_schema.columns 
WHERE table_schema = 'public' 
AND column_name LIKE '%broker%';
```

## Expected Behavior

### Successful Flow

1. ✅ Restore completes successfully
2. ✅ Redirects to Gather Data with customer pre-selected
3. ✅ Storage domains API is called automatically
4. ✅ Storage domains are found in database
5. ✅ Phase form is pre-populated with source/target
6. ✅ User can create phase with pre-filled values

### Fallback Behavior

If no storage domains are found:
- Source defaults to "BlackPearl" or "Rio" based on database type
- Target defaults to same as source
- User can still manually enter values

## Quick Test Script

```bash
#!/bin/bash
# Quick test script for storage domains

cd /home/seans/new_tracker

echo "Testing Storage Domains API..."

# Get first customer
CUSTOMER_ID=$(curl -s http://localhost/api/customers | jq -r '.[0].id')
echo "Using customer ID: $CUSTOMER_ID"

# Test BlackPearl
echo ""
echo "Testing BlackPearl storage domains..."
curl -s "http://localhost/api/phases/storage-domains?customerId=${CUSTOMER_ID}&databaseType=blackpearl" | jq

# Test Rio
echo ""
echo "Testing Rio storage domains..."
curl -s "http://localhost/api/phases/storage-domains?customerId=${CUSTOMER_ID}&databaseType=rio" | jq

echo ""
echo "Done!"
```

Save as `test_storage_domains.sh`, make executable, and run:
```bash
chmod +x test_storage_domains.sh
./test_storage_domains.sh
```
