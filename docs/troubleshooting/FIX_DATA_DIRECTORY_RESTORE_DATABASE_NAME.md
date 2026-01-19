# Fix Data Directory Restore Database Name Issue

## Problem
Data directory restores restore the entire PostgreSQL cluster, not individual databases. The application is trying to connect to `tapesystem_howard_stern`, but that database doesn't exist in the restored cluster.

## Solution: Check What Databases Exist

### 1. List All Databases in Restored Cluster
```bash
# Connect to PostgreSQL and list all databases
sudo -u postgres psql -d postgres -c "\l"

# Or get just the database names
sudo -u postgres psql -d postgres -c "SELECT datname FROM pg_database WHERE datistemplate = false ORDER BY datname;"
```

### 2. Check What Database the Restored Data Contains
The restored data directory likely contains the original database structure. Common names:
- `tapesystem` (generic)
- `blackpearl`
- `rio_db`
- Or some other name from the original backup

### 3. Options to Fix

**Option A: Use the actual database name from the restored cluster**
- If the restored cluster has a database like `tapesystem`, we need to either:
  - Update the code to use the actual database name
  - Or rename the database to match the expected customer-specific name

**Option B: Create the customer-specific database and restore data**
- This would require changing the restore logic to:
  1. Restore the data directory
  2. Extract the database from the restored cluster
  3. Create the customer-specific database
  4. Copy data from the restored database to the customer-specific one

**Option C: Query the generic database**
- If the restored cluster has a generic `tapesystem` database, we could query that instead

## Next Steps

1. First, check what databases exist in the restored cluster
2. Then decide which approach to take based on what's there
