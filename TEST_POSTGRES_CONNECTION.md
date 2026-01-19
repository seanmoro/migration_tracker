# Test PostgreSQL Connection for Buckets

## Check if PostgreSQL is Running

```bash
# Check if PostgreSQL is running
sudo systemctl status postgresql

# Or check for postgres processes
ps aux | grep postgres | grep -v grep
```

## Test Connection Manually

```bash
# Test BlackPearl connection
psql -h localhost -p 5432 -U postgres -d tapesystem -c "SELECT version();"

# Test Rio connection
psql -h localhost -p 5432 -U postgres -d rio_db -c "SELECT version();"
```

## Check if Databases Exist

```bash
# List all databases
psql -h localhost -p 5432 -U postgres -l

# Or connect as postgres user and list databases
sudo -u postgres psql -c "\l"
```

## Update Password in Environment

The passwords are currently set to "your-password" which is a placeholder. Update them:

```bash
# Edit .env file
nano .env

# Or set environment variables directly
export MT_BLACKPEARL_PASSWORD="actual-password"
export MT_RIO_PASSWORD="actual-password"

# Then restart the application
```

## Check Database Tables

Once connected, check if the bucket tables exist:

```bash
# Check BlackPearl tables
psql -h localhost -p 5432 -U postgres -d tapesystem -c "\dt"

# Check Rio tables
psql -h localhost -p 5432 -U postgres -d rio_db -c "\dt"

# Look for buckets or objects tables
psql -h localhost -p 5432 -U postgres -d tapesystem -c "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';"
```

## Common Issues

1. **PostgreSQL not running**: Start with `sudo systemctl start postgresql`
2. **Wrong password**: Update the password in `.env` file
3. **Database doesn't exist**: Create with `createdb -U postgres tapesystem` or `createdb -U postgres rio_db`
4. **Wrong port**: Default is 5432, verify with `sudo netstat -tlnp | grep 5432`
