# Fix pg_hba.conf for postgres User

## Problem
The first line in pg_hba.conf is:
```
local   all             postgres                                peer
```

This matches BEFORE the `trust` line, so PostgreSQL uses `peer` authentication for the postgres user.

## Solution

Edit pg_hba.conf and change the first line:

```bash
sudo nano /etc/postgresql/16/main/pg_hba.conf
```

Change:
```
local   all             postgres                                peer
```

To:
```
local   all             postgres                                trust
```

Or comment it out:
```
# local   all             postgres                                peer
```

## After Editing

```bash
# Restart PostgreSQL
sudo systemctl restart postgresql

# Test connection
psql -h localhost -U postgres -d postgres -c "SELECT version();"
```

## Why This Happens

PostgreSQL reads pg_hba.conf from top to bottom and uses the FIRST matching line. Since the `postgres` user line comes first, it matches before the `all` users line.
