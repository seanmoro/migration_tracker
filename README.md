# Migration Tracker

A comprehensive tool for managing and monitoring Spectra Logic data migrations with tracking and forecasting capabilities.

## Features

- 📊 Track migration progress across multiple phases
- 📈 Generate forecasts and ETA predictions
- 👥 Manage customers, projects, and phases
- 📤 Export reports in multiple formats (PDF, Excel, CSV, JSON, HTML)
- 🔄 Support for IOM_BUCKET, IOM_EXCLUSION, and RIO_CRUISE migration types
- 💾 Database backup and restore functionality

## Installation

### Prerequisites

- Java 21+ (or use bundled JDK on Linux)
- PostgreSQL 14+ (for BlackPearl) and PostgreSQL 16+ (for Rio)
- SQLite 3.x (for tracker database)
- Python 3.x with PyYAML (recommended for YAML parsing)
- Bash 4.0+

### Quick Start

1. **Clone or extract the application**

2. **Create configuration file**
   ```bash
   cp tracker.yaml.example tracker.yaml
   ```

3. **Set environment variables**
   ```bash
   export MT_BLACKPEARL_PASSWORD="your_password"
   export MT_RIO_PASSWORD="your_password"
   ```

4. **Make scripts executable**
   ```bash
   chmod +x bin/migration_tracker
   chmod +x resources/scripts/*
   ```

5. **Run a command**
   ```bash
   ./bin/migration_tracker list-customers
   ```

## Configuration

### Environment Variables

For security, sensitive data should be provided via environment variables:

- `MT_BLACKPEARL_PASSWORD` - Password for BlackPearl database
- `MT_RIO_PASSWORD` - Password for Rio database
- `MT_BLACKPEARL_USERNAME` - Username for BlackPearl (default: postgres)
- `MT_RIO_USERNAME` - Username for Rio (default: postgres)
- `JAVA_PATH` - Custom Java executable path (optional)

### Configuration File

Edit `tracker.yaml` to configure database locations and settings:

```yaml
databaseInfo:
    blackpearl:
        location: "/mnt/hdd0/postgres/tracker/bp/"
        version: "14"
        username: "${MT_BLACKPEARL_USERNAME:-postgres}"
        password: "${MT_BLACKPEARL_PASSWORD}"
    rio:
        location: "/mnt/hdd0/postgres/tracker/rio/"
        version: "16"
        username: "${MT_RIO_USERNAME:-postgres}"
        password: "${MT_RIO_PASSWORD}"
    tracker:
        location: "../resources/database/migrations.db"
```

## Usage

### Basic Commands

```bash
# List all customers
./bin/migration_tracker list-customers

# Create a new customer
./bin/migration_tracker create-customer --name "BigDataCo"

# Create a project
./bin/migration_tracker create-project --project "DeepDive" --company "BigDataCo" --type IOM_BUCKET

# Create a phase
./bin/migration_tracker create-phase --project "DeepDive" --phase "logs-archive" \
    --type IOM_BUCKET --source "hot-storage" --target "tape-vault"

# Gather migration data
./bin/migration_tracker gather-data --project "DeepDive" --phase "logs-archive" --date 2025-01-15

# Generate progress report
./bin/migration_tracker report-progress-phase --project "DeepDive" --phase "logs-archive"
```

### Database Management

```bash
# Unpack a database backup
./bin/migration_tracker unpack-database --type blackpearl --file /path/to/backup.tar

# Dry run (see what would happen)
./bin/migration_tracker unpack-database --type blackpearl --file /path/to/backup.tar --dry-run

# Health check
./bin/migration_tracker health-check
```

### Backup

```bash
# Backup tracker database to S3
./resources/scripts/backup

# Custom backup name
./resources/scripts/backup my-backup-name

# Set custom bucket
BACKUP_BUCKET=my-bucket ./resources/scripts/backup
```

## Architecture

```
migration_tracker/
├── bin/
│   ├── migration_tracker      # Main executable script
│   └── utils.sh               # Utility functions
├── scripts/
│   ├── deployment/            # Deployment and update scripts
│   ├── diagnostics/           # Diagnostic and troubleshooting scripts
│   └── load_test_data.sh      # Load test data script
├── lib/
│   └── migration-tracker-*.jar # Java application
├── resources/
│   ├── database/
│   │   └── migrations.db      # SQLite tracker database
│   ├── scripts/
│   │   ├── backup             # Database backup script
│   │   └── unpack_database    # Database restore script
│   └── files/
│       └── postgresql.conf    # PostgreSQL config template
├── docs/                      # Organized documentation
├── tracker.yaml               # Configuration file
└── tracker.yaml.example       # Configuration template
```

## Workflow

1. **Create Customer** - Set up customer account
2. **Create Project** - Define migration project
3. **Create Phase** - Set up individual migration phases
4. **Gather Data** - Collect migration statistics at intervals
5. **Generate Reports** - View progress and forecasts
6. **Export** - Export reports in various formats

## Troubleshooting

### Java Not Found

If you see "Java not found" errors:

```bash
# Check Java installation
java -version

# Set custom Java path
export JAVA_PATH=/path/to/java
```

### Database Connection Issues

1. Verify PostgreSQL is running:
   ```bash
   sudo systemctl status postgresql
   ```

2. Check database credentials in `tracker.yaml` or environment variables

3. Test connection manually:
   ```bash
   psql -h localhost -U postgres -d tapesystem
   ```

### YAML Parsing Errors

If you see YAML parsing errors:

1. Install Python with PyYAML:
   ```bash
   pip3 install pyyaml
   ```

2. Or install `yq`:
   ```bash
   # Ubuntu/Debian
   sudo apt-get install yq
   
   # macOS
   brew install yq
   ```

### Permission Errors

For database operations, ensure proper permissions:

```bash
# Make scripts executable
chmod +x bin/migration_tracker
chmod +x resources/scripts/*

# For database operations, may need sudo
sudo ./bin/migration_tracker unpack-database ...
```

## Security Best Practices

1. **Never commit passwords** - Use environment variables
2. **Use tracker.yaml.example** - Keep credentials out of version control
3. **Restrict file permissions**:
   ```bash
   chmod 600 tracker.yaml
   ```
4. **Regular backups** - Use the backup script regularly
5. **Audit logs** - Check `log/migration_tracker.log` regularly

## Development

### Frontend

The frontend is a React + TypeScript application:

```bash
cd frontend
npm install
npm run dev
```

See `frontend/README.md` for more details.

### Backend API

The Java application provides the core functionality. API endpoints are documented in the frontend API files.

## Documentation

Comprehensive documentation is organized in the `docs/` directory:

- **Setup Guides**: See `docs/setup/` for installation and configuration
- **Deployment**: See `docs/deployment/` for deployment procedures
- **Troubleshooting**: See `docs/troubleshooting/` for debugging guides
- **Workflows**: See `docs/workflows/` for step-by-step workflows
- **Reference**: See `docs/reference/` for feature documentation

## Support

- **Documentation**: See `docs/README.md` for organized documentation
- **Command Reference**: See `lib/help/options.txt` for command reference
- **Version**: Check with `./bin/migration_tracker --version`
- **Logs**: Check `log/migration_tracker.log` for detailed logs

## License

Copyright © 2025 Spectra Logic Corporation.

## Author

Written by Sean Snyder.
