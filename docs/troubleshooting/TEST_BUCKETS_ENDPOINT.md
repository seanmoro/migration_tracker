# Test Buckets Endpoint

## Test the API Endpoint

```bash
# Test buckets endpoint (should trigger logging)
sudo curl http://localhost/api/migration/buckets

# Test with source parameter
sudo curl http://localhost/api/migration/buckets?source=blackpearl
```

## Check Logs After Testing

```bash
# Check recent logs (last 100 lines)
sudo tail -100 log/application.log

# Look for bucket-related messages
sudo tail -100 log/application.log | grep -i "bucket"

# Check for BlackPearl connection attempts
sudo tail -100 log/application.log | grep -i "blackpearl"

# Check for table listing messages
sudo tail -100 log/application.log | grep -i "available tables"
```

## Enable Debug Logging (if needed)

If logs aren't showing enough detail, you can check the log level in application.yml or add more logging.

## Expected Behavior

When buckets endpoint is called:
1. Service tries to connect to BlackPearl database
2. Tries query 1: `SELECT name FROM buckets`
3. If that fails, tries query 2: `SELECT bucket_name FROM objects GROUP BY bucket_name`
4. If that fails, lists available tables
5. Returns empty array if no buckets found
