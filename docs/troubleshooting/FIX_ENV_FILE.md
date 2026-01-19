# Fix .env File JAVA_OPTS Issue

## Problem

When sourcing `.env`, you see:
```
-bash: export: `-Xmx2048m': not a valid identifier
-bash: export: `-XX:+UseG1GC': not a valid identifier
```

This happens when `JAVA_OPTS` is not properly quoted in the `.env` file.

## Solution

### Option 1: Fix the .env file manually

Edit the `.env` file and ensure `JAVA_OPTS` is properly quoted:

```bash
nano /home/seans/new_tracker/.env
```

Make sure the line looks like this (with quotes):
```bash
export JAVA_OPTS="-Xms512m -Xmx2048m -XX:+UseG1GC"
```

Or use single quotes:
```bash
export JAVA_OPTS='-Xms512m -Xmx2048m -XX:+UseG1GC'
```

### Option 2: Recreate the .env file

```bash
cd /home/seans/new_tracker

cat > .env << 'EOF'
export APP_DIR="/home/seans/new_tracker"
export MIGRATION_TRACKER_DB_PATH="$APP_DIR/resources/database/migrations.db"
export SERVER_PORT=80
export MT_BLACKPEARL_HOST=localhost
export MT_BLACKPEARL_PORT=5432
export MT_BLACKPEARL_DATABASE=tapesystem
export MT_BLACKPEARL_USERNAME=postgres
export MT_BLACKPEARL_PASSWORD=your-password
export MT_RIO_HOST=localhost
export MT_RIO_PORT=5432
export MT_RIO_DATABASE=rio_db
export MT_RIO_USERNAME=postgres
export MT_RIO_PASSWORD=your-password
export JAVA_OPTS="-Xms512m -Xmx2048m -XX:+UseG1GC"
EOF
```

### Option 3: Use sed to fix existing file

```bash
cd /home/seans/new_tracker

# Fix JAVA_OPTS line if it exists
sed -i 's/^export JAVA_OPTS=.*/export JAVA_OPTS="-Xms512m -Xmx2048m -XX:+UseG1GC"/' .env

# Or add it if it doesn't exist
if ! grep -q "^export JAVA_OPTS=" .env; then
  echo 'export JAVA_OPTS="-Xms512m -Xmx2048m -XX:+UseG1GC"' >> .env
fi
```

## Verify the Fix

After fixing, verify the file:

```bash
cat .env | grep JAVA_OPTS
```

You should see:
```
export JAVA_OPTS="-Xms512m -Xmx2048m -XX:+UseG1GC"
```

Then test sourcing it:
```bash
source .env
echo $JAVA_OPTS
```

You should see:
```
-Xms512m -Xmx2048m -XX:+UseG1GC
```

## Complete Fix and Restart

```bash
cd /home/seans/new_tracker

# Fix .env file
sed -i 's/^export JAVA_OPTS=.*/export JAVA_OPTS="-Xms512m -Xmx2048m -XX:+UseG1GC"/' .env || \
  echo 'export JAVA_OPTS="-Xms512m -Xmx2048m -XX:+UseG1GC"' >> .env

# Verify
cat .env | grep JAVA_OPTS

# Source and test
source .env
echo "JAVA_OPTS is: $JAVA_OPTS"

# Restart application
sudo pkill -f "migration-tracker-api.*jar" || true
sleep 2
source .env
sudo -E nohup java -jar backend/target/migration-tracker-api-1.0.0.jar \
    --server.port=80 \
    --server.address=0.0.0.0 \
    > log/application.log 2>&1 &

# Verify
sleep 5
ps aux | grep migration-tracker
sudo curl http://localhost/api/actuator/health
```

## Alternative: Don't Use JAVA_OPTS

If you continue having issues, you can remove `JAVA_OPTS` from `.env` and pass Java options directly:

```bash
sudo -E nohup java -Xms512m -Xmx2048m -XX:+UseG1GC \
    -jar backend/target/migration-tracker-api-1.0.0.jar \
    --server.port=80 \
    --server.address=0.0.0.0 \
    > log/application.log 2>&1 &
```
