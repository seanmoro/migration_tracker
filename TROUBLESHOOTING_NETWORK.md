# Network Troubleshooting Guide

## Can't Access Web Interface on Remote Server

### Step 1: Check if Service is Running

```bash
# Check if the service is running
sudo systemctl status migration-tracker-new

# Or if running manually, check the process
ps aux | grep migration-tracker
```

### Step 2: Check What Port It's Listening On

```bash
# Check if port 8081 is listening
sudo netstat -tulpn | grep 8081

# Or using ss
sudo ss -tulpn | grep 8081

# Or using lsof
sudo lsof -i :8081
```

**Expected output should show:**
```
tcp6  0  0 :::8081  :::*  LISTEN  12345/java
```

**If it shows `127.0.0.1:8081` instead of `:::8081` or `0.0.0.0:8081`, the app is only listening on localhost!**

### Step 3: Check Firewall Rules

```bash
# Check firewall status (Ubuntu/Debian)
sudo ufw status

# Check firewall status (CentOS/RHEL)
sudo firewall-cmd --list-all

# If firewall is active, allow port 8081
# Ubuntu/Debian:
sudo ufw allow 8081/tcp

# CentOS/RHEL:
sudo firewall-cmd --permanent --add-port=8081/tcp
sudo firewall-cmd --reload
```

### Step 4: Check Application Binding

Spring Boot by default should bind to all interfaces (`0.0.0.0`), but let's verify:

```bash
# Check application logs
sudo journalctl -u migration-tracker-new -n 50

# Or if running manually, check the startup logs
# Look for: "Tomcat started on port(s): 8081"
```

### Step 5: Test Local Connection First

```bash
# Test from the server itself
curl http://localhost:8081/api/actuator/health

# Test from the server using its IP
curl http://10.84.45.166:8081/api/actuator/health
```

### Step 6: Check Network Interface

```bash
# Verify the server is listening on the correct interface
ip addr show

# Check if 10.84.45.166 is assigned to an interface
```

## Common Issues and Fixes

### Issue 1: Application Only Listening on Localhost

**Symptom:** `netstat` shows `127.0.0.1:8081` instead of `0.0.0.0:8081`

**Fix:** Add to `application.yml` or `.env`:
```yaml
server:
  address: 0.0.0.0
```

Or in `.env`:
```bash
export SERVER_ADDRESS=0.0.0.0
```

### Issue 2: Firewall Blocking Port

**Symptom:** Can connect locally but not remotely

**Fix:**
```bash
# Ubuntu/Debian
sudo ufw allow 8081/tcp
sudo ufw reload

# CentOS/RHEL
sudo firewall-cmd --permanent --add-port=8081/tcp
sudo firewall-cmd --reload
```

### Issue 3: Service Not Running

**Symptom:** No process listening on port 8081

**Fix:**
```bash
# Start the service
sudo systemctl start migration-tracker-new

# Or run manually
cd /home/seans/new_tracker
source .env
java -jar backend/target/migration-tracker-api-1.0.0.jar --server.port=8081
```

### Issue 4: Wrong Port Configuration

**Symptom:** Service running but on different port

**Fix:** Check `.env` file:
```bash
cat /home/seans/new_tracker/.env | grep SERVER_PORT
```

### Issue 5: SELinux Blocking (CentOS/RHEL)

**Symptom:** Firewall allows but still can't connect

**Fix:**
```bash
# Check SELinux status
getenforce

# If enforcing, allow the port
sudo semanage port -a -t http_port_t -p tcp 8081
```

## Quick Diagnostic Commands

Run these on the remote server:

```bash
# 1. Is service running?
sudo systemctl status migration-tracker-new

# 2. Is port listening?
sudo netstat -tulpn | grep 8081

# 3. Can we connect locally?
curl http://localhost:8081/api/actuator/health

# 4. Check firewall
sudo ufw status  # Ubuntu/Debian
sudo firewall-cmd --list-all  # CentOS/RHEL

# 5. Check application logs
sudo journalctl -u migration-tracker-new -n 100 --no-pager
```

## Testing from Your Local Machine

```bash
# Test if port is reachable
telnet 10.84.45.166 8081

# Or using nc (netcat)
nc -zv 10.84.45.166 8081

# Test HTTP connection
curl http://10.84.45.166:8081/api/actuator/health
```
