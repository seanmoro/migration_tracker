# ⚠️ Backend Needs Restart

The backend is still running with the **old code** that has the date parsing bug.

## 🔄 How to Restart

### Step 1: Stop the Backend
In the terminal where the backend is running, press:
```
Ctrl + C
```

### Step 2: Restart the Backend
```bash
cd backend
mvn spring-boot:run
```

Wait for: `Started MigrationTrackerApiApplication in X seconds`

### Step 3: Test
```bash
curl http://localhost:8080/api/customers
```

Should return JSON with customer data (not a 500 error).

### Step 4: Refresh Frontend
Refresh your browser at `http://localhost:3000`

## ✅ What Was Fixed

The code was updated to handle SQLite date strings properly:
- Changed from `rs.getDate()` to `rs.getString()` 
- Added `LocalDate.parse()` to convert date strings
- Fixed in all repositories (Customer, Project, Phase, MigrationData)

But the backend needs to be **restarted** for the changes to take effect!
