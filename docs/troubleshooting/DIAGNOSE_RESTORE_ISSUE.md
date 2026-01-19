# Diagnose Restore Issue

## Current Status
- Database `tapesystem_howard_stern` does NOT exist
- Storage domains API returns empty (expected - no database)

## Diagnostic Steps

### 1. Check All Databases
```bash
# List ALL databases (not just customer-specific)
sudo -u postgres psql -d postgres -c "\l"

# Check if restore went to generic database
sudo -u postgres psql -d postgres -c "SELECT datname FROM pg_database WHERE datname NOT IN ('template0', 'template1', 'postgres') ORDER BY datname;"
```

### 2. Check Restore Logs
```bash
# Check for ANY restore operations in last 2000 lines
sudo tail -2000 log/application.log | grep -i "restore" | tail -30

# Check for Howard Stern specifically
sudo tail -2000 log/application.log | grep -i "howard\|083a011a" | tail -30

# Check for database creation
sudo tail -2000 log/application.log | grep -i "create.*database\|database.*created\|tapesystem_\|rio_db_" | tail -30

# Check for errors
sudo tail -2000 log/application.log | grep -i "error\|exception\|failed" | tail -50
```

### 3. Check Recent Upload Activity
```bash
# Check for file uploads
sudo tail -2000 log/application.log | grep -i "upload\|file.*received\|saved.*file" | tail -20
```

### 4. Check if Restore Service is Working
```bash
# Check for restore service initialization
sudo tail -2000 log/application.log | grep -i "PostgreSQLRestoreService\|restore.*service" | tail -10
```

## Possible Issues

1. **Restore not attempted**: No restore was actually performed
2. **Restore failed silently**: Check logs for errors
3. **Restore to wrong database**: Check if it restored to generic `tapesystem` database
4. **Database name mismatch**: Check if customer name sanitization created different name
5. **Permission issues**: Check if PostgreSQL user has permission to create databases

## Next Steps

After running diagnostics:
- If no restore logs found → Restore was not attempted (need to run restore)
- If restore logs show errors → Fix the errors
- If restore logs show success but no database → Check database name or permissions
- If database exists with different name → Update code or use correct name
