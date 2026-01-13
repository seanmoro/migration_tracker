# Starting the Backend - Quick Guide

## ✅ Good News!

From the logs, I can see the backend **actually started successfully**! The message you saw was just during initialization. Look for this in the logs:

```
Started MigrationTrackerApiApplication in 1.161 seconds
Tomcat started on port(s): 8080 (http) with context path '/api'
```

## 🚀 How to Start

### Simple Method
```bash
cd backend
mvn spring-boot:run
```

**Wait for this message:**
```
Started MigrationTrackerApiApplication in X seconds
```

The backend is running when you see that message. It will keep running until you press `Ctrl+C`.

### What to Expect

1. **First 5-10 seconds:** Maven downloads dependencies (first time only)
2. **Next 5 seconds:** Spring Boot starts up
3. **You'll see:** "Started MigrationTrackerApiApplication"
4. **Then:** It keeps running (don't close the terminal!)

### Verify It's Working

**In a NEW terminal window:**
```bash
# Test the API
curl http://localhost:8080/api/dashboard/stats

# Should return JSON like:
# {"activeMigrations":38,"totalObjectsMigrated":202751794,...}
```

## ⚠️ Common Confusion

**The backend doesn't exit when it's ready** - it keeps running! That's normal. You need to:

1. **Terminal 1:** Keep `mvn spring-boot:run` running
2. **Terminal 2:** Start the frontend with `npm run dev`
3. **Browser:** Visit `http://localhost:3000`

## 🐛 If It Fails

Check the logs:
```bash
tail -50 log/migration_tracker_api.log
```

Look for:
- ✅ "Started MigrationTrackerApiApplication" = Success!
- ❌ "Failed to start" = Error (check the error message)

## 📝 Quick Test

Once backend is running, test it:

```bash
# Get dashboard stats
curl http://localhost:8080/api/dashboard/stats

# Get customers
curl http://localhost:8080/api/customers

# Get a specific phase progress
curl http://localhost:8080/api/reports/phases/phase-1/progress
```

If these return JSON, the backend is working! 🎉
