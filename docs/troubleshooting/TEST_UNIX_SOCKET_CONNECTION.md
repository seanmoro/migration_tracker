# Test Unix Socket Connection

## Issue
The `host` line is set to `trust`, but `psql -h localhost` might be using a different connection method or PostgreSQL might be using a different config file.

## Test Unix Socket Connection (No -h flag)

```bash
# Connect via Unix socket (no -h flag)
psql -U postgres -d postgres -c "SELECT version();"

# Or explicitly specify Unix socket
psql -h /var/run/postgresql -U postgres -d postgres -c "SELECT version();"
```

## Check Which Config File PostgreSQL is Using

```bash
# Check actual config file location
sudo -u postgres psql -d postgres -c "SHOW hba_file;"

# Check data directory (config might be there)
sudo -u postgres psql -d postgres -c "SHOW data_directory;"
```

## If Config File is Different

If PostgreSQL shows a different hba_file path, edit that file instead:

```bash
# Edit the file shown by SHOW hba_file
sudo nano /path/from/above/pg_hba.conf
```

## Alternative: Use sudo -u postgres (Already Works)

Since `sudo -u postgres psql` already works without password, the application might need to run as the postgres user, or we can use that for testing:

```bash
# This already works
sudo -u postgres psql -d tapesystem -c "\dt"
```

## Check Application Connection

The application might be connecting via TCP/IP. Let's check the logs to see the exact connection string:

```bash
# Check what connection the app is trying
sudo tail -100 log/application.log | grep -i "jdbc:postgresql\|Creating new JDBC\|Failed to obtain JDBC Connection" | tail -10
```
