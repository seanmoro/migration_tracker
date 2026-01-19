# Query Storage Domains - Simple Commands

## Without jq (if not installed)

### Test API Endpoint

```bash
# Get customer ID (without jq)
CUSTOMER_ID=$(curl -s http://localhost/api/customers | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)
echo "Customer ID: $CUSTOMER_ID"

# Query storage domains via API
curl -s "http://localhost/api/phases/storage-domains?customerId=${CUSTOMER_ID}&databaseType=blackpearl"
```

## PostgreSQL Connection Options

### Option 1: Use PGPASSWORD Environment Variable

```bash
# Get password from .env file
source .env
export PGPASSWORD="${MT_BLACKPEARL_PASSWORD:-}"

# Now psql won't prompt for password
psql -h localhost -p 5432 -U postgres -d postgres -c "\l" | grep tapesystem
```

### Option 2: Use sudo (if postgres user has peer authentication)

```bash
# Connect as postgres user (no password needed)
sudo -u postgres psql -d postgres -c "\l" | grep tapesystem
```

### Option 3: Use .pgpass file

```bash
# Create .pgpass file in home directory
echo "localhost:5432:*:postgres:YOUR_PASSWORD" > ~/.pgpass
chmod 600 ~/.pgpass

# Now psql won't prompt
psql -h localhost -p 5432 -U postgres -d postgres -c "\l"
```

## Complete Query Commands (with password handling)

```bash
# Set password from .env
source .env
export PGPASSWORD="${MT_BLACKPEARL_PASSWORD:-}"

# 1. List all databases
psql -h localhost -p 5432 -U postgres -d postgres -c "\l" | grep -E "tapesystem|rio_db"

# 2. Find customer database (replace with actual customer name from UI)
# First, get customer name from API
CUSTOMER_NAME=$(curl -s http://localhost/api/customers | grep -o '"name":"[^"]*' | head -1 | cut -d'"' -f4 | tr '[:upper:]' '[:lower:]' | tr ' ' '_')
echo "Customer name (sanitized): $CUSTOMER_NAME"

# 3. List tables in customer database
DB_NAME="tapesystem_${CUSTOMER_NAME}"
psql -h localhost -p 5432 -U postgres -d "$DB_NAME" -c "\dt"

# 4. Find domain/broker columns
psql -h localhost -p 5432 -U postgres -d "$DB_NAME" -c "
SELECT table_name, column_name 
FROM information_schema.columns 
WHERE table_schema = 'public' 
AND (column_name LIKE '%domain%' OR column_name LIKE '%broker%' OR column_name LIKE '%storage%')
ORDER BY table_name, column_name;"

# 5. Try common tables
psql -h localhost -p 5432 -U postgres -d "$DB_NAME" -c "SELECT * FROM storage_domains LIMIT 10;" 2>&1
psql -h localhost -p 5432 -U postgres -d "$DB_NAME" -c "SELECT * FROM domains LIMIT 10;" 2>&1
psql -h localhost -p 5432 -U postgres -d "$DB_NAME" -c "SELECT * FROM brokers LIMIT 10;" 2>&1
```

## Check Application Logs Instead

If you can't connect to PostgreSQL directly, check what the application found:

```bash
# Check recent storage domain queries
sudo tail -200 log/application.log | grep -A 10 -B 5 "storage domain\|Available tables\|Found.*storage domains"

# Or watch in real-time while testing in UI
sudo tail -f log/application.log | grep -i "storage\|domain"
```

## Quick Test via API (no jq needed)

```bash
# Test storage domains API
curl -s "http://localhost/api/phases/storage-domains?customerId=YOUR_CUSTOMER_ID&databaseType=blackpearl"

# Replace YOUR_CUSTOMER_ID with actual ID from:
curl -s http://localhost/api/customers
```

## Find Customer Database Name

```bash
# Get customer name from API and construct database name
CUSTOMER_JSON=$(curl -s http://localhost/api/customers)
# Manually extract customer name, then:
CUSTOMER_NAME="amc_networks"  # Replace with actual customer name (lowercase, underscores)
DB_NAME="tapesystem_${CUSTOMER_NAME}"

# List databases to verify
source .env
export PGPASSWORD="${MT_BLACKPEARL_PASSWORD:-}"
psql -h localhost -p 5432 -U postgres -d postgres -c "SELECT datname FROM pg_database WHERE datname LIKE 'tapesystem_%' OR datname LIKE 'rio_db_%';"
```
