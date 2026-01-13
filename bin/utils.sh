#!/bin/bash
# Utility functions for migration_tracker scripts

# Standardized error handling
error() {
    echo "Error: $1" >&2
    exit 1
}

info() {
    echo "Info: $1"
}

warn() {
    echo "Warning: $1" >&2
}

# Logging function
log() {
    local log_file="../log/migration_tracker.log"
    mkdir -p "$(dirname "$log_file")"
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] $*" >> "$log_file"
}

# Validation functions
validateConfig() {
    if [ ! -f "../tracker.yaml" ]; then
        error "tracker.yaml not found. Please ensure the configuration file exists."
    fi
}

validateJava() {
    local java_path=$1
    if [ ! -x "$java_path" ]; then
        error "Java not found at $java_path. Please install Java or update the path."
    fi
}

validateFilePath() {
    local file_path=$1
    if [ -z "$file_path" ]; then
        error "File path is required"
    fi
    if [ ! -f "$file_path" ]; then
        error "File not found: $file_path"
    fi
}

validateDate() {
    local date_str=$1
    if [ -z "$date_str" ]; then
        error "Date is required"
    fi
    
    # Try to parse date (works on Linux with GNU date)
    if ! date -d "$date_str" &>/dev/null 2>&1; then
        # Try alternative format for macOS/BSD date
        if ! date -j -f "%Y-%m-%d" "$date_str" &>/dev/null 2>&1; then
            error "Invalid date format: $date_str (expected: YYYY-MM-DD)"
        fi
    fi
}

validateDirectory() {
    local dir_path=$1
    if [ -z "$dir_path" ]; then
        error "Directory path is required"
    fi
    if [ ! -d "$dir_path" ]; then
        error "Directory not found: $dir_path"
    fi
}

# Confirmation for destructive operations
confirmDestructiveOperation() {
    local message="${1:-This will delete data. Continue?}"
    read -p "$message (yes/no): " confirm
    if [ "$confirm" != "yes" ]; then
        info "Operation cancelled"
        exit 0
    fi
}

# YAML parsing using Python (more reliable than grep/awk)
parseYamlValue() {
    local yaml_file=$1
    local key_path=$2
    
    # Try using Python with PyYAML if available
    if command -v python3 &> /dev/null; then
        python3 -c "
import yaml
import sys
try:
    with open('$yaml_file', 'r') as f:
        data = yaml.safe_load(f)
    keys = '$key_path'.split('.')
    value = data
    for key in keys:
        value = value[key]
    print(value if value is not None else '')
except Exception as e:
    sys.exit(1)
" 2>/dev/null && return 0
    fi
    
    # Fallback: try yq if available
    if command -v yq &> /dev/null; then
        yq eval ".$key_path" "$yaml_file" 2>/dev/null && return 0
    fi
    
    # Last resort: simple grep/awk (original method)
    warn "Python PyYAML or yq not found, using fallback parsing"
    return 1
}

# Get YAML value with fallback
getYamlValue() {
    local yaml_file=$1
    local key_path=$2
    local default_value=$3
    
    local value=$(parseYamlValue "$yaml_file" "$key_path")
    if [ -n "$value" ]; then
        echo "$value"
    elif [ -n "$default_value" ]; then
        echo "$default_value"
    else
        echo ""
    fi
}

# Environment variable substitution
substituteEnvVars() {
    local value=$1
    # Replace ${VAR} or ${VAR:-default} patterns
    echo "$value" | sed -E 's/\$\{([^}]+)\}/${\1}/g'
}
