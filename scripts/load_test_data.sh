#!/bin/bash
# Load test data into the Migration Tracker database

DB_PATH="resources/database/migrations.db"
SQL_SCRIPT="scripts/load_test_data.sql"

if [ ! -f "$DB_PATH" ]; then
    echo "Error: Database not found at $DB_PATH"
    exit 1
fi

if [ ! -f "$SQL_SCRIPT" ]; then
    echo "Error: SQL script not found at $SQL_SCRIPT"
    exit 1
fi

echo "Loading test data into database..."
sqlite3 "$DB_PATH" < "$SQL_SCRIPT"

if [ $? -eq 0 ]; then
    echo "✅ Test data loaded successfully!"
    echo ""
    echo "Test data includes:"
    echo "  - 4 customers"
    echo "  - 5 projects"
    echo "  - 6 phases"
    echo "  - 6 reference data points"
    echo "  - 18 migration data points"
else
    echo "❌ Error loading test data"
    exit 1
fi
