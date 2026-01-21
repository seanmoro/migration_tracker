#!/bin/bash
# Script to check the status of phases in the database
# This helps diagnose why Dashboard shows 0 active migrations

DB_PATH="${1:-resources/database/migrations.db}"

if [ ! -f "$DB_PATH" ]; then
    echo "❌ Database file not found: $DB_PATH"
    exit 1
fi

echo "=========================================="
echo "Phase Status Diagnostic"
echo "=========================================="
echo ""

# Check if active column exists
echo "1. Checking if 'active' column exists in migration_phase table..."
sqlite3 "$DB_PATH" "PRAGMA table_info(migration_phase);" | grep -q "active"
if [ $? -eq 0 ]; then
    echo "   ✅ 'active' column exists"
else
    echo "   ⚠️  'active' column does NOT exist - this is the problem!"
    echo "   All phases will be treated as inactive."
    exit 1
fi

echo ""
echo "2. Counting phases by active status..."
echo ""
sqlite3 "$DB_PATH" <<EOF
SELECT 
    CASE 
        WHEN active IS NULL THEN 'NULL (treated as active)'
        WHEN active = 1 THEN 'Active (1)'
        WHEN active = 0 THEN 'Inactive (0)'
        ELSE 'Unknown'
    END as status,
    COUNT(*) as count
FROM migration_phase
GROUP BY 
    CASE 
        WHEN active IS NULL THEN 'NULL (treated as active)'
        WHEN active = 1 THEN 'Active (1)'
        WHEN active = 0 THEN 'Inactive (0)'
        ELSE 'Unknown'
    END
ORDER BY count DESC;
EOF

echo ""
echo "3. Total phases:"
sqlite3 "$DB_PATH" "SELECT COUNT(*) as total FROM migration_phase;"

echo ""
echo "4. Phases that should appear on Dashboard (active IS NULL OR active = 1):"
sqlite3 "$DB_PATH" "SELECT COUNT(*) FROM migration_phase WHERE (active IS NULL OR active = 1);"

echo ""
echo "5. Sample of phases with their active status:"
sqlite3 -header -column "$DB_PATH" "SELECT id, name, active FROM migration_phase LIMIT 10;"

echo ""
echo "=========================================="
echo "Recommendations:"
echo "=========================================="
echo ""
echo "If all phases show active = 0:"
echo "  - Phases were marked as inactive"
echo "  - Solution: Activate them via the UI or run:"
echo "    sqlite3 $DB_PATH \"UPDATE migration_phase SET active = 1 WHERE active = 0;\""
echo ""
echo "If active column doesn't exist:"
echo "  - The column needs to be added"
echo "  - Solution: Restart the application - it should auto-create the column"
echo ""
