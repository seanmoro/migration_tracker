# Verify pg_hba.conf Configuration

## Check Current Configuration

```bash
# View the current pg_hba.conf
sudo cat /etc/postgresql/16/main/pg_hba.conf | grep -E "^local|^host.*127.0.0.1"

# Or view the whole file
sudo cat /etc/postgresql/16/main/pg_hba.conf
```

## What It Should Look Like

The lines for local and localhost connections should be:

```
# TYPE  DATABASE        USER            ADDRESS                 METHOD
local   all             all                                     trust
host    all             all             127.0.0.1/32            trust
host    all             all             ::1/128                 trust
```

## If Changes Weren't Applied

1. **Check file location**: Make sure you edited the right file
```bash
# Verify PostgreSQL is using this file
sudo -u postgres psql -d postgres -c "SHOW hba_file;"
```

2. **Check file permissions**: Make sure it's readable
```bash
ls -l /etc/postgresql/16/main/pg_hba.conf
```

3. **Check PostgreSQL is actually using the file**: Sometimes PostgreSQL uses a different config
```bash
# Check config file location
sudo -u postgres psql -d postgres -c "SHOW config_file;"
```

## Alternative: Check Data Directory Location

If PostgreSQL was installed differently, the config might be in the data directory:

```bash
# Check data directory
sudo -u postgres psql -d postgres -c "SHOW data_directory;"

# Config might be at:
# /var/lib/postgresql/16/main/pg_hba.conf
```

## Fix Steps

1. **Find the actual config file**:
```bash
sudo -u postgres psql -d postgres -c "SHOW hba_file;"
```

2. **Edit that file**:
```bash
sudo nano /path/from/above/pg_hba.conf
```

3. **Change the lines** (make sure to save):
```
local   all             all                                     trust
host    all             all             127.0.0.1/32            trust
```

4. **Restart PostgreSQL**:
```bash
sudo systemctl restart postgresql
```

5. **Test**:
```bash
psql -h localhost -U postgres -d postgres -c "SELECT version();"
```
