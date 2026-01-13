# Quick Start Guide - Testing the Full Stack

## 🚀 Quick Start (3 Steps)

### Step 1: Load Test Data
```bash
./scripts/load_test_data.sh
```

This loads:
- 4 customers
- 5 projects  
- 6 phases
- 24 data points (6 reference + 18 migration)

### Step 2: Start Backend
```bash
cd backend
mvn spring-boot:run
```

Backend runs at: `http://localhost:8080/api`

### Step 3: Start Frontend
```bash
cd frontend
npm install  # First time only
npm run dev
```

Frontend runs at: `http://localhost:3000`

## 🎯 Test Scenarios

### 1. Dashboard
- Visit `http://localhost:3000`
- Should see:
  - 6 active migrations
  - Progress charts
  - Active phases list

### 2. View a High-Progress Phase
- Navigate: Projects → DeepDive → logs-archive
- Should see:
  - 95% progress
  - Chart with 5 data points
  - Forecast with ETA
  - Export button

### 3. Test CRUD Operations
- Create a new customer
- Create a project for that customer
- Create a phase for that project
- Edit and delete items

### 4. Test Search
- Search for "BigDataCo" in customers
- Search for "DeepDive" in projects
- Should find matching results

### 5. Test Reports
- Go to any phase progress page
- View data points table
- Check forecast calculation
- Try export (placeholder)

## 📊 Test Data Summary

| Customer | Project | Phase | Progress | Data Points |
|----------|---------|-------|----------|-------------|
| BigDataCo | DeepDive | logs-archive | 95% | 5 |
| BigDataCo | DeepDive | backups-2024 | 60% | 4 |
| BigDataCo | Archive2024 | media-files | 90% | 4 |
| Acme Corp | DataMigration | database-backups | 67% | 3 |
| TechStart | BackupRestore | excluded-data | 25% | 2 |
| MediaVault | ContentArchive | video-content | 0% | 0 |

## 🔍 Quick API Tests

```bash
# Dashboard stats
curl http://localhost:8080/api/dashboard/stats

# All customers
curl http://localhost:8080/api/customers

# Phase progress (logs-archive - 95% complete)
curl http://localhost:8080/api/reports/phases/phase-1/progress

# Forecast
curl http://localhost:8080/api/reports/phases/phase-1/forecast
```

## 🐛 Troubleshooting

**Backend won't start?**
- Check Java 21 is installed: `java -version`
- Check database exists: `ls resources/database/migrations.db`
- Check port 8080 is free

**Frontend won't start?**
- Run `npm install` first
- Check Node.js 18+ is installed: `node -v`
- Check port 3000 is free

**No data showing?**
- Verify test data loaded: `./scripts/load_test_data.sh`
- Check backend is running
- Check browser console for errors
- Verify API calls in Network tab

**API errors?**
- Check backend logs
- Verify CORS is configured
- Check database connection
- Verify endpoint URLs match

## 📝 Next Steps

1. ✅ Test all CRUD operations
2. ✅ View progress charts
3. ✅ Test search functionality
4. ✅ Check forecast calculations
5. ⚠️ Test export (needs implementation)
6. ⚠️ Test data gathering (needs PostgreSQL)

Enjoy testing! 🎉
