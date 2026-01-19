# Debug Customer/Database Mismatch

## Issue
The customer ID being used doesn't match the expected customer name.

## Commands to Diagnose

### 1. Check what customer the ID belongs to
```bash
# Get customer details for the ID
CUSTOMER_ID="99669832-afb8-48d6-ab1c-5095e3316456"
curl -s http://localhost/api/customers | grep -A 10 "$CUSTOMER_ID"
```

### 2. List all customers to find Howard Stern
```bash
# List all customers
curl -s http://localhost/api/customers

# Or search specifically for Howard Stern
curl -s http://localhost/api/customers | grep -i "howard\|stern" -A 5 -B 5
```

### 3. List all PostgreSQL databases
```bash
# List all databases (including customer-specific ones)
sudo -u postgres psql -d postgres -c "\l" | grep -E "tapesystem|rio_db|Name"

# Or get just the database names
sudo -u postgres psql -d postgres -c "SELECT datname FROM pg_database WHERE datname NOT IN ('template0', 'template1', 'postgres') ORDER BY datname;"
```

### 4. Check what database name would be created for Howard Stern
```bash
# Get Howard Stern customer details
HOWARD_ID=$(curl -s http://localhost/api/customers | grep -i "howard\|stern" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)
HOWARD_NAME=$(curl -s http://localhost/api/customers | grep -i "howard\|stern" | grep -o '"name":"[^"]*' | head -1 | cut -d'"' -f4)

echo "Customer ID: $HOWARD_ID"
echo "Customer Name: $HOWARD_NAME"

# Calculate what the database name would be (lowercase, spaces -> underscores, special chars removed)
DB_NAME=$(echo "$HOWARD_NAME" | tr '[:upper:]' '[:lower:]' | tr ' ' '_' | tr -cd '[:alnum:]_')
echo "Expected database name: tapesystem_${DB_NAME}"
```

### 5. Check application logs for restore operations
```bash
# Check for recent restore operations
sudo tail -500 log/application.log | grep -i "restore\|howard\|amc\|database.*created\|restore.*complete" -A 5
```
