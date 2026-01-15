# Running on Port 80

## Option 1: Run with sudo (Quick Test)

```bash
cd /home/seans/new_tracker
source .env
sudo java -jar backend/target/migration-tracker-api-1.0.0.jar --server.port=80
```

## Option 2: Use setcap (Recommended - No Root Required)

Allow the Java binary to bind to port 80 without root:

```bash
# Find Java path
which java
# Usually: /usr/bin/java

# Give Java permission to bind to port 80
sudo setcap 'cap_net_bind_service=+ep' /usr/bin/java

# Now you can run without sudo
cd /home/seans/new_tracker
source .env
java -jar backend/target/migration-tracker-api-1.0.0.jar --server.port=80
```

## Option 3: Update .env File

Edit `/home/seans/new_tracker/.env`:

```bash
# Change this line:
export SERVER_PORT=8081

# To:
export SERVER_PORT=80
```

Then restart the application.

## Option 4: Use iptables Port Forwarding (Alternative)

If you can't bind to port 80, forward it:

```bash
# Forward port 80 to 8081 (requires root)
sudo iptables -t nat -A PREROUTING -p tcp --dport 80 -j REDIRECT --to-port 8081

# Make it persistent (Ubuntu/Debian)
sudo apt-get install iptables-persistent
sudo netfilter-persistent save
```

## Important Notes

- **Port 80 requires root or setcap**: Standard HTTP port needs elevated privileges
- **Check if port 80 is already in use**: `sudo ss -tulpn | grep :80`
- **Firewall**: Port 80 is usually allowed, but verify with your network admin
- **Security**: Running on port 80 means it's accessible without specifying a port in the URL

## Test After Starting

```bash
# From server
curl http://localhost/api/actuator/health

# From your machine (if firewall allows)
curl http://10.85.45.166/api/actuator/health
```
