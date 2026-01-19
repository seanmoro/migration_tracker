# Change Application to Port 80

## Quick Fix

**On the remote server, edit the .env file:**

```bash
cd /home/seans/new_tracker
nano .env
```

**Change this line:**
```bash
export SERVER_PORT=8081
```

**To:**
```bash
export SERVER_PORT=80
```

**Save and exit (Ctrl+X, then Y, then Enter)**

**Then restart the application:**
```bash
# Stop current process
pkill -f 'migration-tracker-api.*jar'

# Restart with updated port
./START_IN_BACKGROUND.sh
```

## Alternative: One-liner

```bash
cd /home/seans/new_tracker
sed -i 's/export SERVER_PORT=8081/export SERVER_PORT=80/' .env
pkill -f 'migration-tracker-api.*jar'
./START_IN_BACKGROUND.sh
```

## Important: Port 80 Requires Privileges

Port 80 requires root or special permissions. You have two options:

### Option 1: Use setcap (Recommended - No Root)

```bash
# Give Java permission to bind to port 80
sudo setcap 'cap_net_bind_service=+ep' $(which java)

# Then restart normally
pkill -f 'migration-tracker-api.*jar'
./START_IN_BACKGROUND.sh
```

### Option 2: Run with sudo

The script will need to be modified to use `sudo` for the Java command, or you can run manually:

```bash
pkill -f 'migration-tracker-api.*jar'
source .env
sudo java -jar backend/target/migration-tracker-api-1.0.0.jar --server.port=80 --server.address=0.0.0.0 > log/application.log 2>&1 &
```

## Verify

```bash
# Check it's listening on port 80
sudo ss -tulpn | grep :80

# Test locally
curl http://localhost/api/actuator/health
```
