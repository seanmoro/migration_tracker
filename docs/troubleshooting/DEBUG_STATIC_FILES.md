# Debug Static File Serving

## Check Application Logs

```bash
# Check if frontend path was found
sudo tail -100 log/application.log | grep -i "frontend\|dist\|static"

# Check for any errors
sudo tail -100 log/application.log | grep -i "error\|exception"
```

## Verify Frontend Dist Directory

```bash
# Check if dist directory exists
ls -la /home/seans/new_tracker/frontend/dist/

# Check if assets directory exists
ls -la /home/seans/new_tracker/frontend/dist/assets/

# Verify the files are there
ls -la /home/seans/new_tracker/frontend/dist/assets/ | head -10
```

## Test Static File Access

```bash
# Test if we can access index.html directly
sudo curl -I http://localhost/

# Test if we can access a static asset
sudo curl -I http://localhost/assets/index-BLiBbC9x.js

# Check what the server returns
sudo curl http://localhost/assets/index-BLiBbC9x.js | head -20
```

## Check Current Working Directory

The application might be running from a different directory. Check:
```bash
# See what directory the Java process is running from
ps aux | grep migration-tracker | grep -v grep
# Then check that directory
```

## Manual Test of File Path

```bash
# Test if the file exists at the expected path
sudo ls -la /home/seans/new_tracker/frontend/dist/assets/index-BLiBbC9x.js

# If it exists, check permissions
sudo stat /home/seans/new_tracker/frontend/dist/assets/index-BLiBbC9x.js
```
