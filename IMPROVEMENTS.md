# Migration Tracker - Suggested Improvements

## 🔴 Critical Issues (Security & Bugs)

### 1. **Security: Plain Text Passwords**
**Issue:** Database credentials stored in plain text in `tracker.yaml`
```yaml
password: "spectra"  # ⚠️ Exposed in version control
```

**Recommendation:**
- Use environment variables for sensitive data
- Support `.env` file (gitignored) or OS keychain
- Add `tracker.yaml.example` template without credentials
- Implement credential encryption for stored passwords

**Example:**
```yaml
databaseInfo:
    blackpearl:
        location: "/mnt/hdd0/postgres/tracker/bp/"
        version: "14"
        username: "${BP_USERNAME:-postgres}"
        password: "${BP_PASSWORD}"  # Required env var
```

### 2. **Bug: Incorrect Parameter Order in `uploadRioDatabase`**
**Location:** `resources/scripts/unpack_database` line 62

**Issue:**
```bash
uploadRioDatabase() {
    echo "Uploading database from backup file to database: $1"
    sudo pg_restore -d rio_db -f "$2"  # ❌ Wrong: -f expects output file, not input
}
```

**Fix:**
```bash
uploadRioDatabase() {
    echo "Uploading database from backup file to database: $1"
    sudo pg_restore -d "$1" "$2"  # ✅ Correct: -d is database, $2 is input file
}
```

### 3. **Null Pointer Exceptions in Logs**
**Issue:** Logs show `Cannot invoke "java.sql.Connection.prepareStatement(String)" because "conn" is null`

**Recommendation:**
- Add connection validation before use
- Implement retry logic with exponential backoff
- Provide clear error messages when databases are unavailable
- Add health check command: `migration_tracker health-check`

---

## 🟠 High Priority (Code Quality & Reliability)

### 4. **Fragile YAML Parsing**
**Location:** `bin/migration_tracker` lines 17-31

**Issue:** Using `grep` and `awk` to parse YAML is brittle and error-prone

**Current:**
```bash
config=$(cat ../tracker.yaml | grep -A 4 "$db_type")
# Fragile parsing with grep/awk
```

**Recommendation:**
- Use proper YAML parser: `yq` (YAML processor) or Python with PyYAML
- Or move this logic to Java (already has SnakeYAML dependency)
- Add YAML schema validation

**Example:**
```bash
# Using yq (install: apt-get install yq)
location=$(yq eval ".databaseInfo.$db_type.location" ../tracker.yaml)
version=$(yq eval ".databaseInfo.$db_type.version" ../tracker.yaml)
```

### 5. **Missing Error Handling**
**Issues:**
- No validation that `tracker.yaml` exists before parsing
- No check if Java executable exists
- No verification of file paths before operations
- Destructive operations (`clearDirectory`, `clearDatabase`) have no confirmation

**Recommendation:**
```bash
# Add validation functions
validateConfig() {
    if [ ! -f "../tracker.yaml" ]; then
        echo "Error: tracker.yaml not found" >&2
        exit 1
    fi
}

validateJava() {
    if [ ! -x "$java_path" ]; then
        echo "Error: Java not found at $java_path" >&2
        exit 1
    fi
}

# Add confirmation for destructive operations
confirmDestructiveOperation() {
    read -p "This will delete data. Continue? (yes/no): " confirm
    [ "$confirm" = "yes" ] || exit 1
}
```

### 6. **Typos and Code Quality**
**Issues Found:**
- Line 14: "informatin" → "information"
- Line 15: "valdi" → "valid"
- Line 40: Missing space in comment "#Verify" → "# Verify"

**Recommendation:** Run spell checker and linter on bash scripts

### 7. **Hardcoded Paths and Values**
**Issues:**
- Hardcoded database name "rio_db" in multiple places
- Hardcoded Java path assumptions
- Hardcoded PostgreSQL cluster paths

**Recommendation:** Move to configuration with sensible defaults

---

## 🟡 Medium Priority (Usability & Maintainability)

### 8. **Missing Documentation**
**Issue:** No README.md with setup instructions, architecture overview, or troubleshooting

**Recommendation:** Create comprehensive README with:
- Installation instructions
- Configuration guide
- Architecture diagram
- Troubleshooting section
- Development setup

### 9. **No Input Validation**
**Issues:**
- No validation of date format in `gather-data --date`
- No validation of project/phase names (special characters, length)
- No validation of file paths exist before unpacking

**Recommendation:**
```bash
validateDate() {
    if ! date -d "$1" &>/dev/null; then
        echo "Error: Invalid date format: $1 (expected: YYYY-MM-DD)" >&2
        exit 1
    fi
}

validateFilePath() {
    if [ ! -f "$1" ]; then
        echo "Error: File not found: $1" >&2
        exit 1
    fi
}
```

### 10. **Negative Migration Values in Reports**
**Issue:** Logs show negative "remaining" values (e.g., `-7381606445664`)

**Recommendation:**
- Add data validation before storing
- Flag anomalies in reports
- Add data quality checks: `migration_tracker validate-data --project X`

### 11. **No Database Backup Before Destructive Operations**
**Issue:** `clearDatabase` and `clearDirectory` have no backup mechanism

**Recommendation:**
```bash
backupDatabase() {
    local db_name=$1
    local backup_file="backup_${db_name}_$(date +%Y%m%d_%H%M%S).sql"
    sudo -u postgres pg_dump "$db_name" > "$backup_file"
    echo "Backup created: $backup_file"
}
```

### 12. **Inconsistent Error Messages**
**Issue:** Some errors go to stdout, others to stderr; inconsistent formatting

**Recommendation:** Standardize error handling:
```bash
error() {
    echo "Error: $1" >&2
    exit 1
}

info() {
    echo "Info: $1"
}
```

### 13. **No Logging in Bash Scripts**
**Issue:** Bash operations have no logging; only Java app logs

**Recommendation:** Add logging to bash scripts:
```bash
log() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] $*" >> ../log/migration_tracker.log
}
```

---

## 🟢 Low Priority (Enhancements)

### 14. **Add Health Check Command**
```bash
migration_tracker health-check
# Checks:
# - SQLite database accessible
# - PostgreSQL connections working
# - Configuration valid
# - Required files exist
```

### 15. **Improve Argument Parsing**
**Issue:** Current parsing is fragile with `shift` operations

**Recommendation:** Use `getopts` or a proper argument parser:
```bash
while [[ $# -gt 0 ]]; do
    case $1 in
        --command)
            cmd="$2"
            shift 2
            ;;
        --file)
            file_path="$2"
            shift 2
            ;;
        *)
            args+=("$1")
            shift
            ;;
    esac
done
```

### 16. **Add Dry-Run Mode**
```bash
migration_tracker unpack-database --type blackpearl --file backup.tar --dry-run
# Shows what would happen without executing
```

### 17. **Better Cross-Platform Support**
**Issue:** Hardcoded Linux paths and `sudo` usage

**Recommendation:**
- Detect platform and adjust paths
- Support Windows PowerShell script
- Use relative paths where possible

### 18. **Add Configuration Validation**
```bash
migration_tracker validate-config
# Validates tracker.yaml syntax and required fields
```

### 19. **Improve Backup Script**
**Issue:** `resources/scripts/backup` has hardcoded paths

**Recommendation:** Make it configurable and add error handling:
```bash
#!/bin/bash
BACKUP_BUCKET="${BACKUP_BUCKET:-sv-spectra-backups-0}"
DB_PATH="${DB_PATH:-../resources/database/migrations.db}"

if [ ! -f "$DB_PATH" ]; then
    echo "Error: Database not found at $DB_PATH" >&2
    exit 1
fi

aws s3api put-object \
    --bucket "$BACKUP_BUCKET" \
    --key "migration-tracker-$(date +%Y%m%d).db" \
    --body "$DB_PATH" || {
    echo "Error: Backup failed" >&2
    exit 1
}
```

### 20. **Add Unit Tests**
**Recommendation:**
- Add shellcheck for bash scripts
- Add integration tests for database operations
- Test error conditions

---

## 📊 Summary by Category

| Category | Count | Priority |
|----------|-------|----------|
| Security | 1 | Critical |
| Bugs | 2 | Critical |
| Code Quality | 5 | High |
| Usability | 6 | Medium |
| Enhancements | 6 | Low |
| **Total** | **20** | |

---

## 🚀 Quick Wins (Can implement immediately)

1. Fix typos in comments
2. Add input validation functions
3. Fix `uploadRioDatabase` parameter bug
4. Add error handling for missing files
5. Create README.md
6. Add health check command
7. Standardize error messages

---

## 📝 Implementation Priority

**Phase 1 (Week 1):** Critical security and bugs
- Move passwords to environment variables
- Fix `uploadRioDatabase` bug
- Add connection validation

**Phase 2 (Week 2):** Code quality
- Replace grep/awk YAML parsing
- Add comprehensive error handling
- Fix typos and code quality issues

**Phase 3 (Week 3):** Usability
- Create documentation
- Add validation functions
- Improve error messages

**Phase 4 (Ongoing):** Enhancements
- Health checks
- Better logging
- Testing infrastructure
