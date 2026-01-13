# Test Data Guide

## Overview

Test data has been created to help you test the entire Migration Tracker stack. The data includes realistic scenarios with various progress levels and migration types.

## Test Data Contents

### Customers (4)
1. **BigDataCo** - Large enterprise customer
2. **Acme Corporation** - Medium business
3. **TechStart Inc** - Small startup
4. **MediaVault Systems** - Media company

### Projects (5)
1. **DeepDive** (BigDataCo) - IOM_BUCKET migration
2. **Archive2024** (BigDataCo) - IOM_BUCKET migration
3. **DataMigration** (Acme) - RIO_CRUISE_DIVA migration
4. **BackupRestore** (TechStart) - IOM_EXCLUSION migration
5. **ContentArchive** (MediaVault) - IOM_BUCKET migration

### Phases (6)
1. **logs-archive** (DeepDive) - 95% complete, 1000 objects
2. **backups-2024** (DeepDive) - 60% complete, 500 objects
3. **media-files** (Archive2024) - 90% complete, 2000 objects
4. **database-backups** (DataMigration) - 67% complete, 750 objects
5. **excluded-data** (BackupRestore) - 25% complete, 300 objects (needs attention)
6. **video-content** (ContentArchive) - 0% complete, 1500 objects (just started)

### Data Points
- **6 Reference Data Points** - Baseline measurements
- **18 Migration Data Points** - Progress over time with realistic growth

## Loading Test Data

### Option 1: Automatic (Spring Boot)
The test data will be automatically loaded when you start the Spring Boot application if `spring.sql.init.mode=always` is set in `application.yml`.

### Option 2: Manual SQL Script
```bash
# Load test data directly into database
./scripts/load_test_data.sh

# Or manually
sqlite3 resources/database/migrations.db < scripts/load_test_data.sql
```

### Option 3: Via SQLite CLI
```bash
sqlite3 resources/database/migrations.db
.read scripts/load_test_data.sql
```

## Test Scenarios

### Scenario 1: High Progress Phase
- **Phase:** logs-archive
- **Progress:** 95% complete
- **Data Points:** 5 data points showing steady progress
- **Use Case:** Test progress visualization, forecasting, export

### Scenario 2: Medium Progress Phase
- **Phase:** backups-2024
- **Progress:** 60% complete
- **Data Points:** 4 data points
- **Use Case:** Test progress tracking, charts

### Scenario 3: Needs Attention
- **Phase:** excluded-data
- **Progress:** 25% complete (below 50%)
- **Data Points:** 2 data points
- **Use Case:** Test dashboard alerts, phase filtering

### Scenario 4: Just Started
- **Phase:** video-content
- **Progress:** 0% (only reference data)
- **Data Points:** 1 reference point
- **Use Case:** Test new phase creation, initial data gathering

## Testing the Full Stack

### 1. Start Backend
```bash
cd backend
mvn spring-boot:run
```

The API will be at `http://localhost:8080/api`

### 2. Load Test Data (if not auto-loaded)
```bash
./scripts/load_test_data.sh
```

### 3. Start Frontend
```bash
cd frontend
npm install  # First time only
npm run dev
```

The frontend will be at `http://localhost:3000`

### 4. Test Endpoints

#### Dashboard
- Visit `http://localhost:3000` to see dashboard
- Should show:
  - 6 active migrations
  - Total objects migrated
  - Average progress
  - Phases needing attention

#### Customers
- Visit `http://localhost:3000/customers`
- Should see 4 customers
- Try creating, editing, searching

#### Projects
- Visit `http://localhost:3000/projects`
- Should see 5 projects
- Click on "DeepDive" to see phases

#### Phases
- Navigate to a project's phases
- Should see multiple phases with progress bars
- Click on a phase to see detailed progress

#### Phase Progress
- Click on "logs-archive" phase
- Should see:
  - Progress chart with 5 data points
  - Forecast with ETA
  - Data points table
  - Export button

## API Testing

### Using curl

```bash
# Get dashboard stats
curl http://localhost:8080/api/dashboard/stats

# Get all customers
curl http://localhost:8080/api/customers

# Get phase progress
curl http://localhost:8080/api/reports/phases/phase-1/progress

# Get forecast
curl http://localhost:8080/api/reports/phases/phase-1/forecast

# Get phase data points
curl http://localhost:8080/api/reports/phases/phase-1/data
```

### Using Swagger UI
Visit `http://localhost:8080/api/swagger-ui.html` to explore and test all endpoints interactively.

## Expected Results

### Dashboard Stats
- Active Migrations: 6
- Total Objects Migrated: ~4,625 objects
- Average Progress: ~60-70%
- Phases Needing Attention: 1-2 (phases below 50%)

### Phase Progress Examples

**logs-archive (phase-1):**
- Progress: 95%
- Source Objects: 1,000
- Target Objects: 950
- ETA: Should calculate based on rate

**excluded-data (phase-5):**
- Progress: 25%
- Source Objects: 300
- Target Objects: 75
- Status: Needs attention

## Data Characteristics

- **Realistic sizes:** Data sizes in bytes (TB, GB ranges)
- **Time progression:** Data points span from Jan 2024 to April 2024
- **Varied progress:** Different phases at different completion levels
- **Reference data:** Each phase has a baseline reference point
- **Tape partitions:** Some phases include tape partition tracking

## Clearing Test Data

If you need to clear the test data:

```sql
-- Connect to database
sqlite3 resources/database/migrations.db

-- Delete test data (be careful!)
DELETE FROM migration_data WHERE id LIKE 'data-%' OR id LIKE 'ref-%';
DELETE FROM migration_phase WHERE id LIKE 'phase-%';
DELETE FROM migration_project WHERE id LIKE 'project-%';
DELETE FROM customer WHERE id LIKE 'customer-%';
```

## Notes

- Test data uses `INSERT OR IGNORE` to prevent duplicates
- Data IDs are prefixed (customer-*, project-*, phase-*, data-*, ref-*)
- All dates are in 2024 for testing
- Progress calculations are based on reference vs latest data points
- Some phases have tape partition tracking, others don't

## Troubleshooting

### Data not loading
- Check database path in `application.yml`
- Verify SQLite database exists
- Check Spring Boot logs for errors
- Try manual SQL script load

### No data showing in frontend
- Verify backend is running on port 8080
- Check browser console for API errors
- Verify CORS is configured correctly
- Check network tab for API calls

### Progress calculations wrong
- Verify reference data exists for each phase
- Check that data points have correct phase IDs
- Ensure source_objects and target_objects are set correctly
