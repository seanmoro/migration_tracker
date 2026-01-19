# Fix Database Creation Hang

## Problem
Restore is stuck at `createDatabaseIfNotExists` - likely waiting for password prompt from `psql`.

## Root Cause
The `psql` command is trying to connect with an empty password, but PostgreSQL requires SCRAM authentication. The process is waiting for a password prompt that will never come.

## Solution

### 1. Check for Stuck psql Process
```bash
# Check if psql is running and waiting
ps aux | grep psql | grep -v grep

# Check if it's waiting for input
sudo lsof -p 885329 2>/dev/null | grep -i "pipe\|fifo"
```

### 2. Kill Stuck Process and Restart
```bash
# Kill the stuck Java process
sudo pkill -f "migration-tracker-api.*jar"

# Wait
sleep 2

# Restart
cd /home/seans/new_tracker
source .env
sudo -E nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
  --server.port=80 \
  --server.address=0.0.0.0 \
  > log/application.log 2>&1 &
```

### 3. Fix PostgreSQL Authentication
The issue is that PostgreSQL requires a password, but the restore service is trying to connect with an empty password. Options:

**Option A: Set PostgreSQL password in .env**
```bash
# Edit .env file
nano .env

# Add or update:
export MT_BLACKPEARL_PASSWORD="your-postgres-password"
```

**Option B: Configure PostgreSQL to allow local connections without password**
```bash
# Edit pg_hba.conf
sudo nano /etc/postgresql/16/main/pg_hba.conf

# Change this line:
# local   all             all                                     peer
# To:
local   all             all                                     trust

# Or for host connections:
host    all             all             127.0.0.1/32            trust

# Restart PostgreSQL
sudo systemctl restart postgresql
```

**Option C: Use .pgpass file**
```bash
# Create .pgpass file for postgres user
sudo -u postgres bash -c 'echo "localhost:5432:*:postgres:your-password" > ~/.pgpass'
sudo -u postgres chmod 600 ~/.pgpass
```

## Quick Fix: Set Password in .env
```bash
# Get PostgreSQL password (if you know it)
# Then update .env
cd /home/seans/new_tracker
echo 'export MT_BLACKPEARL_PASSWORD="your-password-here"' >> .env

# Restart application
sudo pkill -f "migration-tracker-api.*jar"
sleep 2
source .env
sudo -E nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
  --server.port=80 \
  --server.address=0.0.0.0 \
  > log/application.log 2>&1 &
```

## After Fix: Retry Restore
Once the password is configured, try the restore again from the UI.
