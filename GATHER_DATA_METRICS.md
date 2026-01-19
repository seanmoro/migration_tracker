# Gather Data Metrics

## Overview
The "Gather Data" functionality collects migration statistics for a specific phase on a given date. These metrics are stored as `MigrationData` records.

## Metrics Tracked

The `MigrationData` model tracks the following metrics:

### 1. Source Metrics
- **`sourceObjects`** (Long): Number of objects in the source storage domain/broker
- **`sourceSize`** (Long): Total size of data in the source (in bytes)

### 2. Target Metrics
- **`targetObjects`** (Long): Number of objects in the target storage domain/broker
- **`targetSize`** (Long): Total size of data in the target (in bytes)
- **`targetScratchTapes`** (Integer, optional): Number of scratch tapes used in the target

### 3. Metadata
- **`timestamp`** (LocalDate): Date for which the metrics were collected
- **`migrationPhaseId`** (String): ID of the phase these metrics belong to
- **`type`** (String): Type of data point - "DATA" for gathered data, "REFERENCE" for baseline
- **`userId`** (String, optional): User who gathered the data
- **`createdAt`** (LocalDate): When the record was created
- **`lastUpdated`** (LocalDate): When the record was last updated

## Current Implementation Status

⚠️ **Note**: The current implementation in `MigrationService.gatherData()` is a **placeholder**. It:
- Creates a `MigrationData` record with all metrics set to `0`
- Does NOT actually query PostgreSQL databases yet
- Has a TODO comment indicating it needs to query BlackPearl/Rio databases

## Expected Behavior (When Fully Implemented)

When fully implemented, the gather data functionality should:

1. **Connect to PostgreSQL databases** (BlackPearl or Rio) based on the phase's source/target
2. **Query object counts and sizes** from the `objects` or `blob` tables
3. **Filter by selected buckets** if buckets are provided in the request
4. **Calculate metrics**:
   - Count objects in source storage domain
   - Sum object sizes in source storage domain
   - Count objects in target storage domain
   - Sum object sizes in target storage domain
   - Count scratch tapes (if applicable)

## API Endpoint

**POST** `/api/migration/gather-data`

**Request Body:**
```json
{
  "projectId": "string",
  "phaseId": "string",
  "date": "2024-01-19",
  "selectedBuckets": ["bucket1", "bucket2"]
}
```

**Response:**
```json
{
  "id": "uuid",
  "migrationPhaseId": "string",
  "timestamp": "2024-01-19",
  "sourceObjects": 1000,
  "sourceSize": 1073741824000,
  "targetObjects": 850,
  "targetSize": 912680550400,
  "targetScratchTapes": 10,
  "type": "DATA",
  "createdAt": "2024-01-19",
  "lastUpdated": "2024-01-19"
}
```

## Database Schema

The `migration_data` table stores:
```sql
CREATE TABLE migration_data (
  id TEXT PRIMARY KEY,
  created_at TEXT,
  last_updated TEXT,
  timestamp TEXT,
  migration_phase_id TEXT NOT NULL,
  user_id TEXT,
  source_objects INTEGER,
  source_size INTEGER,
  target_objects INTEGER,
  target_size INTEGER,
  type TEXT,
  target_scratch_tapes INTEGER,
  FOREIGN KEY (migration_phase_id) REFERENCES migration_phase(id)
);
```

## Usage

These metrics are used for:
- **Progress tracking**: See how many objects/size have been migrated
- **Forecasting**: Predict completion dates based on migration rate
- **Reporting**: Generate reports showing migration progress over time
- **Dashboard**: Display active phases and their progress

## Next Steps for Implementation

To fully implement the gather data functionality:

1. Query PostgreSQL databases (BlackPearl/Rio) based on phase source/target
2. Filter by selected buckets if provided
3. Calculate actual object counts and sizes
4. Query scratch tape counts if applicable
5. Store the calculated metrics in the database
