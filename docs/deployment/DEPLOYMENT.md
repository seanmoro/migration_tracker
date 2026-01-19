# Deployment Guide

This guide covers deploying the Migration Tracker application on a remote Linux server.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Installation Location](#installation-location)
3. [Deployment Options](#deployment-options)
4. [Manual Deployment](#manual-deployment)
5. [Docker Deployment](#docker-deployment)
6. [Systemd Service](#systemd-service)
7. [Reverse Proxy Setup](#reverse-proxy-setup)
8. [Configuration](#configuration)
9. [Security Considerations](#security-considerations)
10. [Troubleshooting](#troubleshooting)

## Prerequisites

### System Requirements

- **OS**: Linux (Ubuntu 20.04+, CentOS 7+, or similar)
- **Java**: OpenJDK 17 or higher
- **Node.js**: 18+ (for building frontend, not required at runtime)
- **Maven**: 3.6+ (for building backend, not required at runtime)
- **SQLite**: 3.x (usually pre-installed)
- **PostgreSQL**: 14+ (optional, for BlackPearl/Rio connections)
- **Nginx** or **Apache**: For reverse proxy (recommended)

### Install Prerequisites

```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk maven nodejs npm nginx sqlite3

# CentOS/RHEL
sudo yum install -y java-17-openjdk-devel maven nodejs npm nginx sqlite3
```

## Installation Location

### Default Location

By default, the deployment guide uses `/opt/migration-tracker` as the installation directory. However, you can install to **any location** to run side-by-side with existing production software.

### Alternate Installation Location

To install to an alternate location (e.g., `/opt/migration-tracker-v2` or `/usr/local/migration-tracker`):

1. **Choose your installation directory**:
   ```bash
   # Example: Install to alternate location
   export APP_DIR="/opt/migration-tracker-v2"
   # Or: export APP_DIR="/usr/local/migration-tracker"
   # Or: export APP_DIR="/home/migration-tracker/app"
   ```

2. **Replace all `/opt/migration-tracker` references** in this guide with your chosen path

3. **Set environment variable** for database path:
   ```bash
   export MIGRATION_TRACKER_DB_PATH="$APP_DIR/resources/database/migrations.db"
   ```

4. **Update all configuration files** to use your installation path

### Side-by-Side Installation Example

If you have production software at `/opt/migration-tracker`, install the new version at `/opt/migration-tracker-v2`:

```bash
# Set installation directory
export APP_DIR="/opt/migration-tracker-v2"

# Create directory structure
sudo mkdir -p $APP_DIR
sudo chown $USER:$USER $APP_DIR
cd $APP_DIR

# Create necessary directories
mkdir -p {log,resources/database,resources/scripts,resources/files}

# Transfer files (from your local machine)
rsync -avz --exclude 'node_modules' --exclude 'target' \
  --exclude '.git' --exclude 'dist' \
  /path/to/migration_tracker/ user@remote-server:$APP_DIR/

# Set database path environment variable
export MIGRATION_TRACKER_DB_PATH="$APP_DIR/resources/database/migrations.db"
```

**Key Points for Side-by-Side Installation:**

- ✅ Use different port numbers (e.g., production on 8080, new version on 8081)
- ✅ Use different database files (separate SQLite databases)
- ✅ Use different log directories
- ✅ Use different systemd service names (e.g., `migration-tracker-v2.service`)
- ✅ Use different Nginx server blocks or subdomains

## Deployment Options

### Option 1: Manual Deployment (Recommended for Production)
- Full control over the deployment
- Easy to customize and maintain
- Requires manual setup of services

### Option 2: Docker Deployment
- Isolated environment
- Easy to update and rollback
- Requires Docker installed

### Option 3: Systemd Service
- Automatic startup on boot
- Service management with systemctl
- Log rotation and monitoring

## Manual Deployment

### Step 1: Prepare the Server

**Set your installation directory** (default: `/opt/migration-tracker`):

```bash
# Option 1: Use default location
export APP_DIR="/opt/migration-tracker"

# Option 2: Use alternate location (for side-by-side installation)
# export APP_DIR="/opt/migration-tracker-v2"
# export APP_DIR="/usr/local/migration-tracker"
```

**Create application directory**:

```bash
# Create application directory
sudo mkdir -p $APP_DIR
sudo chown $USER:$USER $APP_DIR
cd $APP_DIR

# Create necessary directories
mkdir -p {log,resources/database,resources/scripts,resources/files}
```

### Step 2: Transfer Files

Transfer the entire project to the server:

```bash
# From your local machine (replace $APP_DIR with your chosen path)
rsync -avz --exclude 'node_modules' --exclude 'target' \
  --exclude '.git' --exclude 'dist' \
  /path/to/migration_tracker/ user@remote-server:$APP_DIR/
```

Or use `scp`:

```bash
# Replace $APP_DIR with your chosen path, e.g., /opt/migration-tracker-v2
scp -r /path/to/migration_tracker/* user@remote-server:$APP_DIR/
```

### Step 3: Build the Application

On the remote server:

```bash
cd $APP_DIR  # or your chosen installation directory

# Build backend
cd backend
mvn clean package -DskipTests
cd ..

# Build frontend
cd frontend
npm install
npm run build
cd ..

# Copy frontend build to backend static resources (if serving from Spring Boot)
# Or configure nginx to serve static files (recommended)
```

### Step 4: Create Production Configuration

```bash
# Copy example config
cp tracker.yaml.example tracker.yaml

# Edit configuration
nano tracker.yaml
```

Update `tracker.yaml` with production values:

```yaml
databaseInfo:
    blackpearl:
        location: "/mnt/hdd0/postgres/tracker/bp/"
        version: "14"
        username: "${MT_BLACKPEARL_USERNAME:-postgres}"
        password: "${MT_BLACKPEARL_PASSWORD}"
    rio:
        location: "/mnt/hdd0/postgres/tracker/rio/"
        version: "16"
        username: "${MT_RIO_USERNAME:-postgres}"
        password: "${MT_RIO_PASSWORD}"
    tracker:
        location: "$APP_DIR/resources/database/migrations.db"  # Replace $APP_DIR with your path
```

### Step 5: Set Environment Variables

Create `$APP_DIR/.env` (or use systemd environment file) - replace `$APP_DIR` with your installation path:

```bash
# Installation directory (set this to your chosen path)
export APP_DIR="/opt/migration-tracker"  # or /opt/migration-tracker-v2, etc.

# Database path (override default location - REQUIRED for alternate installations)
export MIGRATION_TRACKER_DB_PATH="$APP_DIR/resources/database/migrations.db"

# PostgreSQL credentials
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

### Step 6: Make Scripts Executable

```bash
chmod +x bin/migration_tracker
chmod +x resources/scripts/*
```

### Step 7: Initialize Database

```bash
# The database will be created automatically on first run
# Or copy existing database:
# scp migrations.db user@remote-server:$APP_DIR/resources/database/
```

### Step 8: Configure Port (for Side-by-Side Installation)

If installing side-by-side with existing software, use a different port:

**Option 1: Environment Variable**
```bash
# In .env file or systemd service
export SERVER_PORT=8081  # Use different port than production
```

**Option 2: application.yml Override**
Create `$APP_DIR/backend/src/main/resources/application-prod.yml`:
```yaml
server:
  port: 8081  # Different port for side-by-side installation
```

Then run with: `java -jar app.jar --spring.profiles.active=prod`

## Docker Deployment

### Step 1: Create Dockerfile

Create `Dockerfile` in project root:

```dockerfile
# Build stage for backend
FROM maven:3.8-openjdk-17 AS backend-builder
WORKDIR /app
COPY backend/pom.xml backend/
COPY backend/src backend/src
RUN mvn clean package -DskipTests

# Build stage for frontend
FROM node:18-alpine AS frontend-builder
WORKDIR /app
COPY frontend/package*.json frontend/
RUN cd frontend && npm ci
COPY frontend/ frontend/
RUN cd frontend && npm run build

# Runtime stage
FROM openjdk:17-jre-slim
WORKDIR /app

# Install SQLite
RUN apt-get update && apt-get install -y sqlite3 && rm -rf /var/lib/apt/lists/*

# Copy backend JAR
COPY --from=backend-builder /app/backend/target/migration-tracker-api-*.jar app.jar

# Copy frontend build
COPY --from=frontend-builder /app/frontend/dist /app/static

# Copy resources
COPY resources/ resources/
COPY tracker.yaml.example tracker.yaml

# Create directories
RUN mkdir -p log resources/database

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD curl -f http://localhost:8080/api/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Step 2: Create docker-compose.yml

```yaml
version: '3.8'

services:
  migration-tracker:
    build: .
    container_name: migration-tracker
    ports:
      - "8080:8080"
    environment:
      - MT_BLACKPEARL_HOST=${MT_BLACKPEARL_HOST:-localhost}
      - MT_BLACKPEARL_PORT=${MT_BLACKPEARL_PORT:-5432}
      - MT_BLACKPEARL_DATABASE=${MT_BLACKPEARL_DATABASE:-tapesystem}
      - MT_BLACKPEARL_USERNAME=${MT_BLACKPEARL_USERNAME:-postgres}
      - MT_BLACKPEARL_PASSWORD=${MT_BLACKPEARL_PASSWORD}
      - MT_RIO_HOST=${MT_RIO_HOST:-localhost}
      - MT_RIO_PORT=${MT_RIO_PORT:-5432}
      - MT_RIO_DATABASE=${MT_RIO_DATABASE:-rio_db}
      - MT_RIO_USERNAME=${MT_RIO_USERNAME:-postgres}
      - MT_RIO_PASSWORD=${MT_RIO_PASSWORD}
      - JAVA_OPTS=-Xms512m -Xmx2048m
    volumes:
      - ./resources/database:/app/resources/database
      - ./log:/app/log
      - ./tracker.yaml:/app/tracker.yaml
    restart: unless-stopped
    networks:
      - migration-tracker-net

networks:
  migration-tracker-net:
    driver: bridge
```

### Step 3: Build and Run

```bash
# Build image
docker-compose build

# Start service
docker-compose up -d

# View logs
docker-compose logs -f

# Stop service
docker-compose down
```

## Systemd Service

### Step 1: Create Service File

Create `/etc/systemd/system/migration-tracker.service` (or `migration-tracker-v2.service` for side-by-side):

**Replace `$APP_DIR` with your installation path** (e.g., `/opt/migration-tracker-v2`):

```ini
[Unit]
Description=Migration Tracker API
After=network.target

[Service]
Type=simple
User=migration-tracker
Group=migration-tracker
WorkingDirectory=/opt/migration-tracker
EnvironmentFile=/opt/migration-tracker/.env
Environment="MIGRATION_TRACKER_DB_PATH=/opt/migration-tracker/resources/database/migrations.db"
ExecStart=/usr/bin/java -jar /opt/migration-tracker/backend/target/migration-tracker-api-1.0.0.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=migration-tracker

# Security settings
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/opt/migration-tracker/log /opt/migration-tracker/resources/database
```

**For alternate installation location**, replace `/opt/migration-tracker` with your path (e.g., `/opt/migration-tracker-v2`):

```ini
[Service]
Type=simple
User=migration-tracker
Group=migration-tracker
WorkingDirectory=/opt/migration-tracker-v2
EnvironmentFile=/opt/migration-tracker-v2/.env
Environment="MIGRATION_TRACKER_DB_PATH=/opt/migration-tracker-v2/resources/database/migrations.db"
ExecStart=/usr/bin/java -jar /opt/migration-tracker-v2/backend/target/migration-tracker-api-1.0.0.jar
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
ReadWritePaths=/opt/migration-tracker-v2/log /opt/migration-tracker-v2/resources/database

[Install]
WantedBy=multi-user.target
```

### Step 2: Create Service User

```bash
sudo useradd -r -s /bin/false migration-tracker
sudo chown -R migration-tracker:migration-tracker $APP_DIR  # Replace with your path
```

### Step 3: Enable and Start Service

```bash
# Reload systemd
sudo systemctl daemon-reload

# Enable service (start on boot)
sudo systemctl enable migration-tracker

# Start service
sudo systemctl start migration-tracker

# Check status
sudo systemctl status migration-tracker

# View logs
sudo journalctl -u migration-tracker -f
```

## Reverse Proxy Setup

### Nginx Configuration

Create `/etc/nginx/sites-available/migration-tracker` (or `migration-tracker-v2` for side-by-side):

**Replace `$APP_DIR` with your installation path**:

```nginx
server {
    listen 80;
    server_name migration-tracker.example.com;

    # Redirect HTTP to HTTPS (optional)
    # return 301 https://$server_name$request_uri;

    # Frontend static files
    location / {
        root $APP_DIR/frontend/dist;  # Replace $APP_DIR with your path
        try_files $uri $uri/ /index.html;
        add_header Cache-Control "no-cache";
    }

    # Backend API
    location /api {
        proxy_pass http://localhost:8080;  # Use 8081 for side-by-side installation
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;
        proxy_read_timeout 300s;
        proxy_connect_timeout 75s;
    }

    # Health check endpoint
    location /api/actuator/health {
        proxy_pass http://localhost:8080;
        access_log off;
    }

    # Security headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
}
```

Enable the site:

```bash
sudo ln -s /etc/nginx/sites-available/migration-tracker /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

### SSL/TLS with Let's Encrypt (Recommended)

```bash
# Install certbot
sudo apt-get install certbot python3-certbot-nginx

# Obtain certificate
sudo certbot --nginx -d migration-tracker.example.com

# Auto-renewal is set up automatically
```

## Configuration

### Environment Variables

Set these in `$APP_DIR/.env` or systemd service file (replace `$APP_DIR` with your installation path):

```bash
# PostgreSQL BlackPearl
MT_BLACKPEARL_HOST=blackpearl-db.example.com
MT_BLACKPEARL_PORT=5432
MT_BLACKPEARL_DATABASE=tapesystem
MT_BLACKPEARL_USERNAME=postgres
MT_BLACKPEARL_PASSWORD=secure-password

# PostgreSQL Rio
MT_RIO_HOST=rio-db.example.com
MT_RIO_PORT=5432
MT_RIO_DATABASE=rio_db
MT_RIO_USERNAME=postgres
MT_RIO_PASSWORD=secure-password

# Java Options
JAVA_OPTS=-Xms512m -Xmx2048m -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

### Application Configuration

Edit `$APP_DIR/backend/src/main/resources/application.yml` for production (or create `application-prod.yml`):

```yaml
spring:
  application:
    name: migration-tracker-api
  
server:
  port: 8080
  servlet:
    context-path: /api

logging:
  level:
    com.spectralogic.migrationtracker: INFO
    org.springframework.web: WARN
  file:
    name: $APP_DIR/log/migration_tracker_api.log  # Replace $APP_DIR with your path
  pattern:
    file: "%d{yyyy-MM-dd HH:mm:ss} %-5level %logger{36} - %msg%n"
```

## Security Considerations

### 1. File Permissions

```bash
# Restrict config file (replace $APP_DIR with your path)
chmod 600 $APP_DIR/tracker.yaml
chmod 600 $APP_DIR/.env

# Database file
chmod 640 $APP_DIR/resources/database/migrations.db
```

### 2. Firewall

```bash
# Allow only necessary ports
sudo ufw allow 22/tcp    # SSH
sudo ufw allow 80/tcp    # HTTP
sudo ufw allow 443/tcp   # HTTPS
sudo ufw enable
```

### 3. Database Backups

Set up automated backups:

```bash
# Add to crontab (replace $APP_DIR with your path)
0 2 * * * $APP_DIR/resources/scripts/backup
```

### 4. Log Rotation

Create `/etc/logrotate.d/migration-tracker`:

```
$APP_DIR/log/*.log {  # Replace $APP_DIR with your installation path
    daily
    rotate 30
    compress
    delaycompress
    missingok
    notifempty
    create 0640 migration-tracker migration-tracker
}
```

## Troubleshooting

### Backend Won't Start

```bash
# Check Java version
java -version

# Check logs (replace $APP_DIR with your path)
tail -f $APP_DIR/log/migration_tracker_api.log

# Check if port is in use (use 8081 for side-by-side)
sudo netstat -tulpn | grep 8080  # or 8081

# Test database connection
sqlite3 $APP_DIR/resources/database/migrations.db "SELECT 1;"
```

### Frontend Not Loading

```bash
# Check nginx configuration
sudo nginx -t

# Check nginx logs
sudo tail -f /var/log/nginx/error.log

# Verify static files exist (replace $APP_DIR with your path)
ls -la $APP_DIR/frontend/dist/
```

### PostgreSQL Connection Issues

```bash
# Test connection manually
psql -h $MT_BLACKPEARL_HOST -U $MT_BLACKPEARL_USERNAME -d $MT_BLACKPEARL_DATABASE

# Check network connectivity
telnet $MT_BLACKPEARL_HOST 5432

# Verify credentials in environment
env | grep MT_
```

### Service Management

```bash
# Restart service
sudo systemctl restart migration-tracker

# View service logs
sudo journalctl -u migration-tracker -n 100 -f

# Check service status
sudo systemctl status migration-tracker
```

## Quick Deployment Script

Save as `deploy.sh`:

```bash
#!/bin/bash
set -e

# Set installation directory (change this for side-by-side installation)
APP_DIR="${APP_DIR:-/opt/migration-tracker}"  # Default, or set via environment
# Example for side-by-side: export APP_DIR="/opt/migration-tracker-v2" before running

SERVICE_USER="migration-tracker"

echo "Creating directories..."
sudo mkdir -p $APP_DIR/{log,resources/{database,scripts,files}}
sudo chown -R $USER:$USER $APP_DIR

echo "Building backend..."
cd backend
mvn clean package -DskipTests
cd ..

echo "Building frontend..."
cd frontend
npm install
npm run build
cd ..

echo "Setting permissions..."
chmod +x bin/migration_tracker
chmod +x resources/scripts/*

echo "Creating service user..."
sudo useradd -r -s /bin/false $SERVICE_USER || true
sudo chown -R $SERVICE_USER:$SERVICE_USER $APP_DIR

echo "Deployment complete!"
echo "Installation directory: $APP_DIR"
echo "Next steps:"
echo "1. Configure tracker.yaml at $APP_DIR/tracker.yaml"
echo "2. Set environment variables in $APP_DIR/.env"
echo "   - Set MIGRATION_TRACKER_DB_PATH=$APP_DIR/resources/database/migrations.db"
echo "   - Set APP_DIR=$APP_DIR (if using alternate location)"
echo "3. Set up systemd service (see DEPLOYMENT.md)"
echo "4. Configure nginx reverse proxy"
echo "5. For side-by-side: Use different port (8081) and service name"
```

Make executable and run:

```bash
chmod +x deploy.sh
./deploy.sh
```

## Monitoring

### Health Check Endpoint

```bash
curl http://localhost:8080/api/actuator/health
```

### Log Monitoring

```bash
# Watch logs in real-time
tail -f /opt/migration-tracker/log/migration_tracker_api.log

# Search for errors
grep -i error /opt/migration-tracker/log/migration_tracker_api.log
```

## Updates

### Updating the Application

```bash
cd $APP_DIR  # Replace with your installation path

# Pull latest code (if using git)
git pull

# Rebuild
cd backend && mvn clean package -DskipTests && cd ..
cd frontend && npm install && npm run build && cd ..

# Restart service
sudo systemctl restart migration-tracker  # or migration-tracker-v2 for side-by-side
```

## Support

For issues or questions:
- Check logs: `$APP_DIR/log/` (replace `$APP_DIR` with your installation path)
- Review configuration: `$APP_DIR/tracker.yaml` and `$APP_DIR/.env`
- Test endpoints: `curl http://localhost:8080/api/actuator/health` (or 8081 for side-by-side)

## Side-by-Side Installation Summary

**Quick Reference for Alternate Installation:**

1. **Set installation directory**:
   ```bash
   export APP_DIR="/opt/migration-tracker-v2"  # Your chosen path
   ```

2. **Set database path**:
   ```bash
   export MIGRATION_TRACKER_DB_PATH="$APP_DIR/resources/database/migrations.db"
   ```

3. **Use different port** (in `.env` or `application.yml`):
   ```bash
   export SERVER_PORT=8081  # Different from production
   ```

4. **Use different service name**:
   ```bash
   sudo systemctl enable migration-tracker-v2
   ```

5. **Use different Nginx config**:
   - File: `/etc/nginx/sites-available/migration-tracker-v2`
   - Server name: `migration-tracker-v2.example.com` (or different subdomain)
   - Port: `8081` (in proxy_pass)

All paths in this guide can be customized by replacing `/opt/migration-tracker` with your chosen `$APP_DIR`.
