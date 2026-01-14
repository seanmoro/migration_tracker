# Side-by-Side Installation Guide

This guide helps you install Migration Tracker alongside existing production software without conflicts.

## Quick Start

### 1. Choose Installation Location

**For this deployment, use:**
```bash
export APP_DIR="/home/seans/new_tracker"
```

**Other examples:**
```bash
# Example: Install to alternate location
export APP_DIR="/opt/migration-tracker-v2"

# Or use a versioned path
# export APP_DIR="/opt/migration-tracker-2.0"

# Or use a different base directory
# export APP_DIR="/usr/local/migration-tracker"
```

**Note:** The original production installation is at `/home/seans/migration_tracker-v0.6.2`, so installing to `/home/seans/new_tracker/` keeps them side-by-side.

### 2. Set Environment Variables

Create `$APP_DIR/.env`:

```bash
# Installation directory
export APP_DIR="/home/seans/new_tracker"

# Database path (REQUIRED for alternate location)
export MIGRATION_TRACKER_DB_PATH="$APP_DIR/resources/database/migrations.db"

# Use different port (production uses 8080)
export SERVER_PORT=8081

# PostgreSQL credentials (same as production or different)
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
```

### 3. Deploy Application

```bash
# Set installation directory
export APP_DIR="/home/seans/new_tracker"

# Create directory
sudo mkdir -p $APP_DIR
sudo chown $USER:$USER $APP_DIR
cd $APP_DIR

# Create subdirectories
mkdir -p {log,resources/database,resources/scripts,resources/files}

# Transfer files (from your local machine)
rsync -avz --exclude 'node_modules' --exclude 'target' \
  --exclude '.git' --exclude 'dist' \
  /path/to/migration_tracker/ user@remote-server:$APP_DIR/

# Build application
cd backend && mvn clean package -DskipTests && cd ..
cd frontend && npm install && npm run build && cd ..
```

### 4. Configure Systemd Service

Create `/etc/systemd/system/migration-tracker-new.service`:

```ini
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
SyslogIdentifier=migration-tracker-v2

# Security settings
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/home/seans/new_tracker/log /home/seans/new_tracker/resources/database

[Install]
WantedBy=multi-user.target
```

Enable and start:

```bash
sudo systemctl daemon-reload
sudo systemctl enable migration-tracker-new
sudo systemctl start migration-tracker-new
sudo systemctl status migration-tracker-new
```

**Note:** If you prefer to run as your user (`seans`) without systemd, you can also run directly:
```bash
cd /home/seans/new_tracker
source .env
java -jar backend/target/migration-tracker-api-1.0.0.jar --server.port=8081
```

### 5. Configure Nginx (Optional)

Create `/etc/nginx/sites-available/migration-tracker-new`:

```nginx
server {
    listen 80;
    server_name migration-tracker-new.example.com;  # Different subdomain

    # Frontend static files
    location / {
        root /home/seans/new_tracker/frontend/dist;
        try_files $uri $uri/ /index.html;
        add_header Cache-Control "no-cache";
    }

    # Backend API (note: port 8081)
    location /api {
        proxy_pass http://localhost:8081;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;
    }
}
```

Enable site:

```bash
sudo ln -s /etc/nginx/sites-available/migration-tracker-new /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

## Key Differences for Side-by-Side

| Component | Production (v0.6.2) | New Installation |
|-----------|---------------------|-----------------|
| **Installation Path** | `/home/seans/migration_tracker-v0.6.2` | `/home/seans/new_tracker` |
| **Database File** | `/home/seans/migration_tracker-v0.6.2/resources/database/migrations.db` | `/home/seans/new_tracker/resources/database/migrations.db` |
| **Port** | `8080` | `8081` |
| **Service Name** | `migration-tracker.service` (or existing) | `migration-tracker-new.service` |
| **Log Directory** | `/home/seans/migration_tracker-v0.6.2/log` | `/home/seans/new_tracker/log` |
| **Nginx Config** | `migration-tracker` (or existing) | `migration-tracker-new` |
| **Domain** | `migration-tracker.example.com` | `migration-tracker-new.example.com` |

## Verification

### Check Service Status

```bash
# Check both services
sudo systemctl status migration-tracker  # or whatever the existing service is named
sudo systemctl status migration-tracker-new

# Check ports
sudo netstat -tulpn | grep -E '8080|8081'
```

### Test Endpoints

```bash
# Production (port 8080)
curl http://localhost:8080/api/actuator/health

# Side-by-side (port 8081)
curl http://localhost:8081/api/actuator/health
```

### Check Databases

```bash
# Production database (v0.6.2)
sqlite3 /home/seans/migration_tracker-v0.6.2/resources/database/migrations.db "SELECT COUNT(*) FROM customer;"

# New installation database
sqlite3 /home/seans/new_tracker/resources/database/migrations.db "SELECT COUNT(*) FROM customer;"
```

## Troubleshooting

### Port Already in Use

If port 8081 is also in use:

```bash
# Find available port
sudo netstat -tulpn | grep LISTEN

# Update .env file
export SERVER_PORT=8082  # Use different port

# Update systemd service ExecStart
ExecStart=/usr/bin/java -jar /home/seans/new_tracker/backend/target/migration-tracker-api-1.0.0.jar --server.port=8082

# Restart service
sudo systemctl restart migration-tracker-new
```

### Database Path Issues

If the application can't find the database:

```bash
# Verify environment variable is set
echo $MIGRATION_TRACKER_DB_PATH

# Check database file exists
ls -la $MIGRATION_TRACKER_DB_PATH

# Verify in systemd service
sudo systemctl show migration-tracker-new | grep MIGRATION_TRACKER_DB_PATH
```

### Service Conflicts

If service names conflict:

```bash
# Use unique service name
sudo systemctl disable migration-tracker  # or existing service name
sudo systemctl enable migration-tracker-new

# Use unique syslog identifier in service file
SyslogIdentifier=migration-tracker-new
```

## Migration from Production

To migrate data from production to side-by-side installation:

```bash
# Copy database from production (v0.6.2) to new installation
cp /home/seans/migration_tracker-v0.6.2/resources/database/migrations.db \
   /home/seans/new_tracker/resources/database/migrations.db

# Set permissions (if needed)
chmod 640 /home/seans/new_tracker/resources/database/migrations.db
```

## Rollback

To rollback to production:

```bash
# Stop new installation service
sudo systemctl stop migration-tracker-new
sudo systemctl disable migration-tracker-new

# Production (v0.6.2) continues running on port 8080
# Check existing service status
sudo systemctl status migration-tracker  # or whatever the existing service is named
```

## Summary

✅ **Isolated Installation**: Separate directory, database, and logs  
✅ **No Conflicts**: Different port, service name, and domain  
✅ **Easy Rollback**: Production remains untouched  
✅ **Testing Safe**: Test new version without affecting production  

For full deployment details, see [DEPLOYMENT.md](./DEPLOYMENT.md).
