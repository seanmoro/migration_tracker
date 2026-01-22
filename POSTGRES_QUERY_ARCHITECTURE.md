# PostgreSQL Query Architecture

## Current State

**PostgreSQL should ONLY be queried during `gatherData()` to populate SQLite. All UI operations should read from SQLite.**

## Where PostgreSQL is Currently Queried

### ✅ CORRECT - Only during gather data:
1. **`MigrationService.gatherData()`** - Queries PostgreSQL and stores results in SQLite (`migration_data` and `bucket_data` tables)
   - This is the ONLY place where PostgreSQL should be queried for migration metrics

### ⚠️ NEEDS FIXING - Still querying PostgreSQL for UI:
1. **`StorageDomainService.getStorageDomains()`** - Used by `/api/phases/storage-domains`
   - **Used for**: Dropdowns when creating/editing phases
   - **Should**: Store storage domains in SQLite when database is restored, then read from SQLite

2. **`BucketService.getBucketsForCustomer()`** - Used by `/api/migration/buckets/customer`
   - **Used for**: Bucket selection during gather data setup
   - **Should**: Store buckets in SQLite when database is restored, then read from SQLite

3. **`BucketService.getBucketSize()`** - Used by `/api/migration/buckets/size`
   - **Used for**: Bucket Query page
   - **Should**: Read from gathered `bucket_data` in SQLite instead

## Fixed

### ✅ FIXED - Now reads from SQLite:
1. **`ReportService.getPhaseProgress()`** - Now reads from SQLite `migration_data` table
   - Removed all PostgreSQL query methods (`queryObjectCountByStorageDomain`, `querySizeByStorageDomain`, `queryTapeCountByStorageDomain`)
   - Removed database existence caching (no longer needed)

## Next Steps

To fully implement the architecture:

1. **When database is restored**: Query PostgreSQL and store:
   - Storage domains → SQLite table
   - Buckets → SQLite table
   - Tape partitions → SQLite table

2. **Update services to read from SQLite**:
   - `StorageDomainService` → Read from SQLite
   - `BucketService` → Read from SQLite
   - `BucketService.getBucketSize()` → Read from `bucket_data` table

3. **Remove PostgreSQL queries from UI endpoints**:
   - All `/api/phases/storage-domains` calls should read from SQLite
   - All `/api/migration/buckets/*` calls should read from SQLite

## Benefits

- **No CPU pegging**: No expensive PostgreSQL queries on every page load
- **Faster UI**: SQLite queries are instant
- **Consistent data**: Shows what was actually gathered, not real-time data
- **Proper architecture**: PostgreSQL for gathering, SQLite for reporting
