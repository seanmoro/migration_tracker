# Deployment Scripts

Scripts for deploying and updating the Migration Tracker application.

## Scripts

### [DEPLOY_TO_REMOTE.sh](./DEPLOY_TO_REMOTE.sh)
**Purpose**: Complete deployment script for remote server  
**Usage**: Run on remote server after cloning from GitHub  
**Features**:
- Creates directory structure
- Creates `.env` file with defaults
- Builds backend and frontend
- Copies database from production (optional)

### [UPDATE_REMOTE.sh](./UPDATE_REMOTE.sh)
**Purpose**: Update code on remote server  
**Usage**: Run on remote server after pulling latest code  
**Features**:
- Pulls latest code from GitHub
- Rebuilds backend and frontend
- Provides restart instructions

### [START_IN_BACKGROUND.sh](./START_IN_BACKGROUND.sh)
**Purpose**: Pull latest code and start application in background  
**Usage**: Run on remote server to update and start  
**Features**:
- Pulls latest code
- Rebuilds backend and frontend
- Installs `zstd` if needed
- Stops existing process
- Starts application in background
- Shows access URLs

### [INSTALL_TO_NEW_TRACKER.sh](./INSTALL_TO_NEW_TRACKER.sh)
**Purpose**: Installation script for side-by-side installation  
**Usage**: Run to set up new installation alongside existing  
**Features**:
- Creates directory structure
- Creates `.env` file
- Provides transfer and build instructions

## Common Usage

```bash
# Initial deployment
./scripts/deployment/DEPLOY_TO_REMOTE.sh

# Update existing installation
./scripts/deployment/UPDATE_REMOTE.sh

# Update and start in background
./scripts/deployment/START_IN_BACKGROUND.sh
```

## Notes

- Most scripts assume the application is in `/home/seans/new_tracker`
- Scripts check for required files before proceeding
- All scripts include error handling and helpful messages
