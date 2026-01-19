# Quick Deployment Guide

## Step 1: Push to GitHub (Local Machine)

```bash
cd /Users/seanmoro/cursor/migration_tracker

# Commit all changes
git add -A
git commit -m "Initial deployment to /home/seans/new_tracker"

# Push to GitHub
git push -u origin main
```

## Step 2: Deploy on Remote Server

**SSH into your server:**
```bash
ssh seans@your-server.com
```

**Run these commands on the server:**

```bash
# Set installation directory
export APP_DIR="/home/seans/new_tracker"

# Clone from GitHub
git clone https://github.com/seanmoro/migration_tracker.git $APP_DIR
cd $APP_DIR

# Run deployment script
./DEPLOY_TO_REMOTE.sh

# Edit .env with your PostgreSQL credentials
nano .env

# Test the application
source .env
java -jar backend/target/migration-tracker-api-1.0.0.jar --server.port=8081
```

## Step 3: Set Up Systemd Service (Optional)

```bash
# Create systemd service
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

[Install]
WantedBy=multi-user.target
EOF

# Enable and start
sudo systemctl daemon-reload
sudo systemctl enable migration-tracker-new
sudo systemctl start migration-tracker-new

# Check status
sudo systemctl status migration-tracker-new
```

## Updating Deployment

**On local machine:**
```bash
cd /Users/seanmoro/cursor/migration_tracker
# Make changes...
git add -A
git commit -m "Update description"
git push origin main
```

**On remote server:**
```bash
cd /home/seans/new_tracker
git pull
cd backend && mvn clean package -DskipTests && cd ..
cd frontend && npm install && npm run build && cd ..
sudo systemctl restart migration-tracker-new
```

## Verify Deployment

```bash
# Check service
sudo systemctl status migration-tracker-new

# Test API
curl http://localhost:8081/api/actuator/health

# View logs
sudo journalctl -u migration-tracker-new -f
```
