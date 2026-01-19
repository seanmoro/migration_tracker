# macOS Setup Guide

## Quick Setup for macOS

### Option 1: Install Maven (Recommended)

```bash
# Install Maven via Homebrew
brew install maven

# Verify installation
mvn --version
```

### Option 2: Use the Run Script

The backend includes a helper script that will install Maven if needed:

```bash
cd backend
./run.sh
```

This script will:
- Check for Maven and install via Homebrew if missing
- Check Java version
- Start the Spring Boot application

## Java Version

The backend requires **Java 17 or higher**. You currently have Java 17, which is perfect!

If you need to upgrade or install Java:

```bash
# Install Java 21 (latest LTS)
brew install openjdk@21

# Or install Java 17
brew install openjdk@17

# Set JAVA_HOME (add to ~/.zshrc)
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

## Complete Setup Steps

### 1. Install Maven
```bash
brew install maven
```

### 2. Load Test Data
```bash
./scripts/load_test_data.sh
```

### 3. Start Backend
```bash
cd backend
mvn spring-boot:run
```

Or use the helper script:
```bash
cd backend
./run.sh
```

### 4. Start Frontend (in another terminal)
```bash
cd frontend
npm install  # First time only
npm run dev
```

## Alternative: Use Maven Wrapper

If you prefer not to install Maven globally, you can use the Maven Wrapper:

```bash
cd backend
# Download Maven wrapper (one-time setup)
mvn wrapper:wrapper

# Then use ./mvnw instead of mvn
./mvnw spring-boot:run
```

## Troubleshooting

### Maven not found after installation
```bash
# Add to ~/.zshrc
export PATH="/opt/homebrew/bin:$PATH"

# Reload shell
source ~/.zshrc
```

### Java version issues
```bash
# Check current Java
java -version

# List installed Java versions
/usr/libexec/java_home -V

# Set specific version
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

### Port already in use
```bash
# Check what's using port 8080
lsof -i :8080

# Kill the process if needed
kill -9 <PID>
```

## Quick Test

Once everything is installed:

```bash
# Terminal 1: Backend
cd backend
mvn spring-boot:run

# Terminal 2: Frontend  
cd frontend
npm run dev

# Terminal 3: Load test data (if not auto-loaded)
./scripts/load_test_data.sh
```

Then visit `http://localhost:3000` in your browser!
