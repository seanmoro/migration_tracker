# Deployment Steps for /home/seans/new_tracker

This guide provides exact steps to deploy Migration Tracker to `/home/seans/new_tracker` on your remote server.

## Prerequisites

- Remote server access (SSH)
- Java 17+ installed on remote server
- Maven installed on remote server (for building)
- Node.js 18+ installed on remote server (for building frontend)
- PostgreSQL credentials (if using bucket selection feature)

## Option 1: Deploy via GitHub (Recommended)

### Step 1: Push to GitHub

**On your local machine:**

```bash
cd /Users/seanmoro/cursor/migration_tracker

# Check current status
git status

# If you haven't committed changes yet, commit them
git add -A
git commit -m "Prepare for deployment to /home/seans/new_tracker"

# Create GitHub repository (if you haven't already)
# Go to https://github.com/new and create a new repository
# Then add it as remote:

git remote add origin https://github.com/seanmoro/migration_tracker.git
# OR if you already have a remote:
# git remote set-url origin https://github.com/seanmoro/migration_tracker.git

# Push to GitHub
git push -u origin main
```

### Step 2: Deploy on Remote Server

**SSH into your remote server:**

```bash
ssh seans@your-server.com
```

**On the remote server, run these commands:**

```bash
# Set installation directory
export APP_DIR="/home/seans/new_tracker"

# Create directory structure
mkdir -p $APP_DIR
cd $APP_DIR

# Clone from GitHub (or pull if updating)
git clone https://github.com/seanmoro/migration_tracker.git .
# OR if directory already exists:
# cd $APP_DIR && git pull

# Create necessary directories
mkdir -p {log,resources/database,resources/scripts,resources/files}

# Create .env file
cat > $APP_DIR/.env << 'EOF'
# Installation directory
export APP_DIR="/home/seans/new_tracker"

# Database path (REQUIRED)
export MIGRATION_TRACKER_DB_PATH="$APP_DIR/resources/database/migrations.db"

# Use different port (production v0.6.2 uses 8080)
export SERVER_PORT=8081

# PostgreSQL credentials - UPDATE THESE!
export MT_BLACKPEARL_HOST=your-blackpearl-host
export MT_BLACKPEARL_PORT=5432
export MT_BLACKPEARL_DATABASE=tapesystem
export MT_BLACKPEARL_USERNAME=postgres
export MT_BLACKPEARL_PASSWORD=your-password

export MT_RIO_HOST=your-rio-host
export MT_RIO_PORT=5432
export MT_RIO_DATABASE=rio_db
export MT_RIO_USERNAME=postgres
export MT_RIO_PASSWORD=your-password

# Java options
export JAVA_OPTS="-Xms512m -Xmx2048m -XX:+UseG1GC"
EOF

# Edit .env with your actual PostgreSQL credentials
nano $APP_DIR/.env

# Build backend
cd $APP_DIR/backend
mvn clean package -DskipTests

# Build frontend
cd $APP_DIR/frontend
npm install
npm run build

# Go back to app directory
cd $APP_DIR

# (Optional) Copy database from production v0.6.2
if [ -f "/home/seans/migration_tracker-v0.6.2/resources/database/migrations.db" ]; then
    echo "Copying database from production..."
    cp /home/seans/migration_tracker-v0.6.2/resources/database/migrations.db \
       $APP_DIR/resources/database/migrations.db
    chmod 640 $APP_DIR/resources/database/migrations.db
fi

# Test run
source $APP_DIR/.env
java -jar $APP_DIR/backend/target/migration-tracker-api-1.0.0.jar --server.port=8081

# If successful, press Ctrl+C and continue to systemd setup
```

### Step 3: Set Up Systemd Service (Optional)

**On remote server:**

```bash
# Create systemd service file
sudo tee /etc/systemd/system/migration-tracker-new.service > /dev/null << 'EOF'
[Unit]
Description=Migration Tracker API (New Version)
After=network.target

[Service]
Type=simple
User=seans
Group=seans
WorkingDirectory=/home/seans/new_tracker
EnvironmentFile=/home/seans/new_tracker/.env
Environment="MIGRATION_TRACKER_DB_PATH=/home/seans/new_tracker/resources/database/migrations.db"
ExecStart=/usr/bin/java -jar /home/seans/new_tracker/backend/target/migration-tracker-api-1.0.0.jar --server.port=8081
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=migration-tracker-new

# Security settings
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/home/seans/new_tracker/log /home/seans/new_tracker/resources/database

[Install]
WantedBy=multi-user.target
EOF

# Enable and start service
sudo systemctl daemon-reload
sudo systemctl enable migration-tracker-new
sudo systemctl start migration-tracker-new

# Check status
sudo systemctl status migration-tracker-new

# View logs
sudo journalctl -u migration-tracker-new -f
```

### Step 4: Verify Deployment

```bash
# Check if service is running
sudo systemctl status migration-tracker-new

# Test API endpoint
curl http://localhost:8081/api/actuator/health

# Check port
sudo netstat -tulpn | grep 8081
```

---

## Option 2: Deploy via Direct Transfer (No GitHub)

### Step 1: Prepare on Local Machine

**On your local machine:**

```bash
cd /Users/seanmoro/cursor/migration_tracker

# Build backend (optional - can build on server)
cd backend
mvn clean package -DskipTests
cd ..

# Build frontend (optional - can build on server)
cd frontend
npm install
npm run build
cd ..
```

### Step 2: Transfer Files to Remote Server

**On your local machine:**

```bash
# Set variables
APP_DIR="/home/seans/new_tracker"
REMOTE_USER="seans"
REMOTE_HOST="your-server.com"  # Update with your server address

# Transfer files (excluding build artifacts and git)
rsync -avz --progress \
  --exclude 'node_modules' \
  --exclude 'target' \
  --exclude '.git' \
  --exclude 'dist' \
  --exclude 'log' \
  --exclude '*.log' \
  --exclude '.DS_Store' \
  --exclude 'tracker.yaml' \
  /Users/seanmoro/cursor/migration_tracker/ \
  $REMOTE_USER@$REMOTE_HOST:$APP_DIR/
```

**Or using scp:**

```bash
# Create a tarball first
cd /Users/seanmoro/cursor
tar --exclude='node_modules' \
    --exclude='target' \
    --exclude='.git' \
    --exclude='dist' \
    --exclude='log' \
    --exclude='*.log' \
    -czf migration_tracker.tar.gz migration_tracker/

# Transfer tarball
scp migration_tracker.tar.gz $REMOTE_USER@$REMOTE_HOST:/tmp/

# Clean up local tarball
rm migration_tracker.tar.gz
```

### Step 3: Set Up on Remote Server

**SSH into your remote server:**

```bash
ssh seans@your-server.com
```

**On the remote server:**

```bash
# Set installation directory
export APP_DIR="/home/seans/new_tracker"

# If using tarball, extract it
cd /home/seans
tar -xzf /tmp/migration_tracker.tar.gz
mv migration_tracker new_tracker
rm /tmp/migration_tracker.tar.gz

# Create necessary directories
cd $APP_DIR
mkdir -p {log,resources/database,resources/scripts,resources/files}

# Create .env file (same as Option 1, Step 2)
cat > $APP_DIR/.env << 'EOF'
# ... (same .env content as Option 1)
EOF

# Edit .env with your credentials
nano $APP_DIR/.env

# Build backend (if not built locally)
cd $APP_DIR/backend
mvn clean package -DskipTests

# Build frontend (if not built locally)
cd $APP_DIR/frontend
npm install
npm run build

# (Optional) Copy database from production
if [ -f "/home/seans/migration_tracker-v0.6.2/resources/database/migrations.db" ]; then
    cp /home/seans/migration_tracker-v0.6.2/resources/database/migrations.db \
       $APP_DIR/resources/database/migrations.db
fi

# Test run
cd $APP_DIR
source .env
java -jar backend/target/migration-tracker-api-1.0.0.jar --server.port=8081
```

**Then follow Step 3 and Step 4 from Option 1** (systemd setup and verification).

---

## Quick Reference Commands

### Update Deployment (GitHub Method)

```bash
# On local machine
cd /Users/seanmoro/cursor/migration_tracker
git add -A
git commit -m "Update deployment"
git push origin main

# On remote server
cd /home/seans/new_tracker
git pull
cd backend && mvn clean package -DskipTests && cd ..
cd frontend && npm install && npm run build && cd ..
sudo systemctl restart migration-tracker-new
```

### Update Deployment (Direct Transfer Method)

```bash
# On local machine - rebuild and transfer
cd /Users/seanmoro/cursor/migration_tracker
# ... make changes ...
rsync -avz --exclude 'node_modules' --exclude 'target' \
  --exclude '.git' --exclude 'dist' \
  /Users/seanmoro/cursor/migration_tracker/ \
  seans@your-server.com:/home/seans/new_tracker/

# On remote server - rebuild
cd /home/seans/new_tracker
cd backend && mvn clean package -DskipTests && cd ..
cd frontend && npm install && npm run build && cd ..
sudo systemctl restart migration-tracker-new
```

### Useful Commands on Remote Server

```bash
# View service logs
sudo journalctl -u migration-tracker-new -f

# Restart service
sudo systemctl restart migration-tracker-new

# Stop service
sudo systemctl stop migration-tracker-new

# Check service status
sudo systemctl status migration-tracker-new

# Test API
curl http://localhost:8081/api/actuator/health

# Check what's running on ports
sudo netstat -tulpn | grep -E '8080|8081'
```

---

## Troubleshooting

### Port Already in Use

```bash
# Check what's using port 8081
sudo lsof -i :8081

# Or use different port
# Edit .env: export SERVER_PORT=8082
# Update systemd service ExecStart to use --server.port=8082
```

### Database Not Found

```bash
# Verify database path
echo $MIGRATION_TRACKER_DB_PATH

# Check if database exists
ls -la /home/seans/new_tracker/resources/database/migrations.db

# Create database directory if missing
mkdir -p /home/seans/new_tracker/resources/database
```

### Build Failures

```bash
# Check Java version
java -version  # Should be 17+

# Check Maven
mvn -version

# Check Node.js
node -v  # Should be 18+

# Clean and rebuild
cd /home/seans/new_tracker/backend
mvn clean
mvn package -DskipTests
```

---

## Recommendation

**Use Option 1 (GitHub)** because:
- ✅ Easy updates (just `git pull`)
- ✅ Version control and history
- ✅ Easy rollback
- ✅ Can set up CI/CD later
- ✅ Better for collaboration

**Use Option 2 (Direct Transfer)** if:
- You don't want to use GitHub
- You need to deploy immediately without setting up a repo
- You have network restrictions
