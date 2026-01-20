# Backup Cleanup for Database Restores

## Overview

When restoring a PostgreSQL data directory backup, the system automatically creates a backup of the existing data directory before restoring. To prevent the backup directory from growing too large, the system now automatically cleans up old backups, keeping only the most recent ones.

## How It Works

1. **Backup Creation**: Before restoring, the system creates a backup directory named:
   ```
   {data_directory_name}_backup_{timestamp}
   ```
   For example: `main_backup_1705671234567`

2. **Automatic Cleanup**: After creating a new backup, the system:
   - Finds all backup directories matching the pattern
   - Sorts them by timestamp (newest first)
   - Keeps the most recent N backups (configurable, default: 2)
   - Deletes older backups automatically

## Configuration

### Default Behavior
By default, the system keeps **3 most recent backups**.

### Customizing Backup Count

You can configure the number of backups to keep in `application.yml`:

```yaml
postgres:
  backup:
    keep-count: 3  # Keep 3 most recent backups
```

Or via environment variable:

```bash
export POSTGRES_BACKUP_KEEP_COUNT=3
```

## Example

If you have these backup directories:
- `main_backup_1705671000000` (oldest)
- `main_backup_1705672000000`
- `main_backup_1705673000000` (newest)

And `keep-count` is set to 3:
- The system will keep: `main_backup_1705673000000`, `main_backup_1705672000000`, and `main_backup_1705671000000`
- If there were 4 backups, the oldest would be deleted

## Backup Location

Backups are stored in the parent directory of the PostgreSQL data directory. For example:
- Data directory: `/var/lib/postgresql/16/main`
- Backups: `/var/lib/postgresql/16/main_backup_*`

## Safety Features

- Only deletes backups matching the exact pattern `{dataDirName}_backup_{timestamp}`
- Keeps the most recent backups (sorted by timestamp)
- Uses efficient system commands (`rm -rf`) when possible
- Falls back to Java Files API if system commands fail
- Logs all cleanup operations for audit

## Manual Cleanup

If you need to manually clean up backups:

```bash
# List all backups
ls -la /var/lib/postgresql/16/ | grep "_backup_"

# Delete specific backup (with sudo if needed)
sudo rm -rf /var/lib/postgresql/16/main_backup_1705671000000
```

## Troubleshooting

### Backups Not Being Deleted

1. Check logs for cleanup messages:
   ```bash
   grep "cleanup\|backup" log/application.log
   ```

2. Verify backup directory pattern matches:
   - Pattern: `{dataDirName}_backup_{timestamp}`
   - Example: `main_backup_1705671234567`

3. Check file permissions:
   - The application needs write/delete permissions on the backup directory
   - May require `sudo` if backups are in protected directories

### Too Many Backups Kept

- Check `postgres.backup.keep-count` configuration
- Verify the cleanup method is being called (check logs)
- Ensure backup directories match the expected naming pattern
