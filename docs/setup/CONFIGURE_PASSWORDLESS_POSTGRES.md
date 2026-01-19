# Configure Passwordless PostgreSQL Access

## Option 1: Configure pg_hba.conf for Trust Authentication (Recommended for Local)

This allows local connections without a password.

### Steps:

```bash
# 1. Find pg_hba.conf location
sudo -u postgres psql -d postgres -c "SHOW hba_file;"

# Usually located at:
# /etc/postgresql/16/main/pg_hba.conf
# or
# /var/lib/postgresql/16/main/pg_hba.conf

# 2. Backup the current config
sudo cp /etc/postgresql/16/main/pg_hba.conf /etc/postgresql/16/main/pg_hba.conf.backup

# 3. Edit pg_hba.conf
sudo nano /etc/postgresql/16/main/pg_hba.conf

# 4. Find the line for local connections (usually near the top):
# local   all             all                                     peer
# or
# local   all             all                                     md5
# or
# local   all             all                                     scram-sha-256

# 5. Change it to:
local   all             all                                     trust

# 6. Also change the IPv4 localhost line:
# host    all             all             127.0.0.1/32            scram-sha-256
# To:
host    all             all             127.0.0.1/32            trust

# 7. Save and exit (Ctrl+X, Y, Enter)

# 8. Reload PostgreSQL configuration
sudo systemctl reload postgresql

# Or restart PostgreSQL
sudo systemctl restart postgresql
```

### Test:

```bash
# Should now work without password
psql -h localhost -U postgres -d postgres -c "SELECT version();"
```

## Option 2: Use .pgpass File

Create a password file that PostgreSQL clients will use automatically.

```bash
# Create .pgpass file in home directory
echo "localhost:5432:*:postgres:YOUR_PASSWORD" > ~/.pgpass
chmod 600 ~/.pgpass

# Test
psql -h localhost -U postgres -d postgres -c "SELECT version();"
```

## Option 3: Remove Password from .env (Use Trust Authentication)

If you configure Option 1 (trust authentication), you can remove or leave empty the password in .env:

```bash
# Edit .env
nano .env

# Set password to empty or remove the line:
# export MT_BLACKPEARL_PASSWORD=""

# Or comment it out:
# export MT_BLACKPEARL_PASSWORD="your-password"
```

## Option 4: Use Peer Authentication (Unix Socket Only)

If the application connects via Unix socket, you can use peer authentication:

```bash
# Edit pg_hba.conf
sudo nano /etc/postgresql/16/main/pg_hba.conf

# Change local connections to:
local   all             all                                     peer

# This only works for Unix socket connections (not TCP/IP)
```

## Recommended: Option 1 (Trust for Localhost)

For a local development/staging server, Option 1 is the easiest. It allows passwordless connections from localhost only, which is secure enough for internal use.

## After Configuration

1. **Restart the application** so it picks up the new authentication:
```bash
sudo pkill -f "migration-tracker-api.*jar"
sleep 2
cd /home/seans/new_tracker
source .env
sudo -E nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
  --server.port=80 \
  --server.address=0.0.0.0 \
  > log/application.log 2>&1 &
```

2. **Test the API**:
```bash
HOWARD_ID="083a011a-9439-4119-92bb-c6550c773063"
curl -s "http://localhost/api/phases/storage-domains?customerId=${HOWARD_ID}&databaseType=blackpearl"
```

## Security Note

Trust authentication allows connections without a password. This is fine for:
- Local development
- Internal networks
- Servers behind a firewall

For production, consider using:
- SCRAM-SHA-256 with strong passwords
- SSL/TLS connections
- Firewall rules to restrict access
