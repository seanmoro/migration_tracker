# Quick Rebuild and Restart Commands

## Summary

- **Rebuild**: Does NOT need sudo (just needs write access to `backend/target/`)
- **Restart**: DOES need sudo (for port 80 and PostgreSQL permissions)

## Commands

### Without Sudo (if not using port 80)

```bash
cd /home/seans/new_tracker
git pull origin main
cd backend && mvn clean package -DskipTests && cd ..
pkill -f "migration-tracker-api.*jar" || true
sleep 2
source .env
nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
    --server.port=8080 \
    --server.address=0.0.0.0 \
    > log/application.log 2>&1 &
```

### With Sudo (for port 80 and PostgreSQL restore)

```bash
cd /home/seans/new_tracker
git pull origin main

# Rebuild (NO sudo needed)
cd backend && mvn clean package -DskipTests && cd ..

# Stop (use sudo if process was started with sudo)
sudo pkill -f "migration-tracker-api.*jar" || pkill -f "migration-tracker-api.*jar" || true
sleep 2

# Start with sudo (sudo needed for port 80 and PostgreSQL operations)
source .env
sudo -E nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
    --server.port=80 \
    --server.address=0.0.0.0 \
    > log/application.log 2>&1 &
```

## Why?

- **Rebuild (`mvn`)**: Just compiles Java code and creates JAR file. Only needs write access to `backend/target/` directory, which you should own.
- **Restart (`java -jar`)**: Needs sudo for:
  - Binding to port 80 (privileged port)
  - Stopping/starting PostgreSQL (`systemctl`)
  - Copying files to `/var/lib/postgresql/` (protected directory)
  - Setting file permissions (`chown`)

## One-Liner (with sudo)

```bash
cd /home/seans/new_tracker && git pull origin main && cd backend && mvn clean package -DskipTests && cd .. && sudo pkill -f "migration-tracker-api.*jar" || true && sleep 2 && source .env && sudo -E nohup java -jar backend/target/migration-tracker-api-1.0.0.jar --server.port=80 --server.address=0.0.0.0 > log/application.log 2>&1 &
```

## Verify

```bash
# Check process
ps aux | grep migration-tracker

# Check health
sudo curl http://localhost/api/actuator/health
```
