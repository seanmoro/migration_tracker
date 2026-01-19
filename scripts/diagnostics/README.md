# Diagnostic Scripts

Scripts for diagnosing and troubleshooting issues with the Migration Tracker application.

## Scripts

### [CHECK_NETWORK.sh](./CHECK_NETWORK.sh)
**Purpose**: Network diagnostic script  
**Usage**: Run to check network configuration  
**Features**:
- Lists all IP addresses
- Shows default route
- Lists listening ports
- Tests localhost connection
- Shows hostname and FQDN

### [TEST_PORT_80.sh](./TEST_PORT_80.sh)
**Purpose**: Test port 80 access  
**Usage**: Run to verify application is accessible on port 80  
**Features**:
- Tests localhost connections
- Tests server IP connections
- Tests API endpoints
- Tests frontend static files

### [DIAGNOSE_PORT_80.sh](./DIAGNOSE_PORT_80.sh)
**Purpose**: Diagnose port 80 binding issues  
**Usage**: Run when application won't start on port 80  
**Features**:
- Checks what's listening on port 80
- Checks if application is running
- Checks `.env` configuration
- Shows application logs
- Tests port binding capability
- Checks Java capabilities
- Verifies frontend build

### [FIX_PORT_80.sh](./FIX_PORT_80.sh)
**Purpose**: Fix port 80 configuration  
**Usage**: Run to configure application for port 80  
**Features**:
- Sets Java capability for port 80 binding
- Updates `.env` file with port 80
- Provides restart instructions

### [FIND_UPLOAD_ERROR.sh](./FIND_UPLOAD_ERROR.sh)
**Purpose**: Find upload/restore errors in logs  
**Usage**: Run when database restore fails  
**Features**:
- Searches log files for errors
- Shows recent exceptions with context
- Displays last log lines

### [WATCH_UPLOAD_LOGS.sh](./WATCH_UPLOAD_LOGS.sh)
**Purpose**: Watch upload-related logs in real-time  
**Usage**: Run before uploading to monitor progress  
**Features**:
- Watches log files for upload activity
- Filters for restore/upload/database keywords
- Handles multiple log files
- Waits for log file creation if needed

## Common Usage

```bash
# Check network configuration
./scripts/diagnostics/CHECK_NETWORK.sh

# Test port 80 access
./scripts/diagnostics/TEST_PORT_80.sh

# Diagnose port 80 issues
./scripts/diagnostics/DIAGNOSE_PORT_80.sh

# Fix port 80 configuration
./scripts/diagnostics/FIX_PORT_80.sh

# Find upload errors
./scripts/diagnostics/FIND_UPLOAD_ERROR.sh

# Watch upload logs
./scripts/diagnostics/WATCH_UPLOAD_LOGS.sh
```

## Notes

- Most scripts assume the application is in `/home/seans/new_tracker`
- Diagnostic scripts are read-only (except FIX_PORT_80.sh)
- Scripts provide helpful recommendations when issues are found
