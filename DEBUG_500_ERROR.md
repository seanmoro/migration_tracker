# Debug 500 Error on Migration Data Endpoint

## Check Backend Logs

```bash
# Check recent errors in logs
sudo tail -100 log/application.log | grep -A 10 -i "error\|exception\|500"

# Check for specific phase ID errors
sudo tail -100 log/application.log | grep "7193bf4b-65a7-421b-893d-6042b13119b4"

# Check for date parsing errors
sudo tail -100 log/application.log | grep -i "date\|parse\|timestamp"
```

## Test the API Endpoint Directly

```bash
# Test the migration data endpoint
sudo curl -v http://localhost/api/migration/data?phaseId=7193bf4b-65a7-421b-893d-6042b13119b4

# Check what error is returned
sudo curl http://localhost/api/migration/data?phaseId=7193bf4b-65a7-421b-893d-6042b13119b4 2>&1
```

## Common Causes

1. **Date Parsing Error**: SQLite stores dates as strings, and if the format is unexpected, parsing fails
2. **Null Values**: Missing required fields in the database
3. **Database Connection Issue**: SQLite database might be locked or corrupted

## Check Database

```bash
# Check if the database has data for this phase
sqlite3 /home/seans/new_tracker/resources/database/migrations.db \
  "SELECT * FROM migration_data WHERE migration_phase_id = '7193bf4b-65a7-421b-893d-6042b13119b4' LIMIT 5;"

# Check date format in database
sqlite3 /home/seans/new_tracker/resources/database/migrations.db \
  "SELECT id, timestamp, created_at FROM migration_data WHERE migration_phase_id = '7193bf4b-65a7-421b-893d-6042b13119b4' LIMIT 5;"
```

## Fix: Add Error Handling to Backend

If the error is in date parsing, we may need to add try-catch in the repository mapper.
