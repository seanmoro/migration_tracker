# 🚀 Start Here - Quick Setup for macOS

## ✅ Maven Installed!

Maven has been successfully installed via Homebrew. You're ready to go!

## Quick Start (3 Commands)

### 1. Load Test Data
```bash
./scripts/load_test_data.sh
```

### 2. Start Backend
```bash
cd backend
mvn spring-boot:run
```

The API will start at: `http://localhost:8080/api`

### 3. Start Frontend (in a new terminal)
```bash
cd frontend
npm install  # First time only
npm run dev
```

The UI will start at: `http://localhost:3000`

## 🎯 What You'll See

1. **Dashboard** - Overview with 6 active migrations
2. **Customers** - 4 test customers
3. **Projects** - 5 test projects
4. **Phases** - 6 phases with varying progress (0% to 95%)
5. **Charts** - Progress visualization
6. **Forecasts** - ETA calculations

## 📝 Alternative: Use Helper Script

Instead of `mvn spring-boot:run`, you can use:

```bash
cd backend
./run.sh
```

This script checks for Maven and Java automatically.

## 🔍 Verify Installation

```bash
# Check Maven
mvn --version

# Check Java
java -version

# Check Node (for frontend)
node -v
npm -v
```

## 🐛 Troubleshooting

**Backend won't start?**
- Make sure port 8080 is free: `lsof -i :8080`
- Check database exists: `ls resources/database/migrations.db`
- Check logs in `log/migration_tracker_api.log`

**Frontend won't start?**
- Run `npm install` first
- Check Node.js is installed: `node -v` (needs 18+)
- Check port 3000 is free

**No data showing?**
- Load test data: `./scripts/load_test_data.sh`
- Check backend is running
- Open browser console (F12) to see errors

## 📚 More Help

- **macOS Setup**: See `macOS_SETUP.md`
- **Test Data**: See `TEST_DATA.md`
- **Quick Start**: See `QUICK_START.md`
- **Backend Docs**: See `backend/README.md`
- **Frontend Docs**: See `frontend/README.md`

## 🎉 You're Ready!

Everything is set up. Just run the 3 commands above and you'll have the full stack running!
