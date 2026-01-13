#!/bin/bash
# Run script for macOS - handles Maven installation check

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "Maven not found. Installing via Homebrew..."
    if command -v brew &> /dev/null; then
        brew install maven
    else
        echo "Error: Homebrew not found. Please install Maven manually:"
        echo "  brew install maven"
        echo "Or download from: https://maven.apache.org/download.cgi"
        exit 1
    fi
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "Warning: Java 17+ required. Current version: $JAVA_VERSION"
    echo "Install Java 17+ with: brew install openjdk@17"
    echo "Or update JAVA_HOME to point to Java 17+"
fi

# Run Spring Boot
echo "Starting Migration Tracker API..."
mvn spring-boot:run
