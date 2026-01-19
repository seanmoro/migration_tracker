# Bucket Selection Workflow

## Overview
The bucket selection feature allows you to choose specific buckets to track when gathering migration data, rather than tracking all buckets.

## Step-by-Step Workflow

### 1. Navigate to Gather Data Page
- Click "Gather Data" in the sidebar navigation

### 2. Fill Required Fields
- **Project**: Select a project from the dropdown (required)
- **Phase**: Select a phase from the dropdown (required, depends on selected project)
- **Date**: Enter the date for the data collection (required)

### 3. Show Bucket List (Optional)
- Scroll down to the "Select Buckets (Optional)" section
- Click the **"Show Buckets"** button to expand the bucket selection panel
- The system will attempt to load buckets from both BlackPearl and Rio databases

### 4. Bucket List States

#### ✅ **Buckets Loaded Successfully**
- You'll see a scrollable list of buckets
- Each bucket shows:
  - **Checkbox** (empty square = unselected, checked square = selected)
  - **Bucket name**
  - **Source badge** (Blue = BlackPearl, Green = Rio)
  - **Object count** (formatted number)
  - **Size** (formatted bytes: KB, MB, GB, etc.)

#### ⏳ **Loading**
- Shows a loading spinner with "Loading buckets..." message
- Wait for the query to complete

#### ❌ **No Buckets Found**
- Shows "No buckets found. Check database connections."
- This can happen if:
  - PostgreSQL databases are not connected
  - Database credentials are incorrect
  - Database schema doesn't match expected table/column names
  - Tables are empty

### 5. Search and Filter Buckets
- Use the **search box** at the top of the bucket list
- Type to filter buckets by name (case-insensitive)
- The selection counter updates: "X of Y selected"

### 6. Select Buckets

#### Individual Selection
- Click on any bucket row to toggle selection
- Selected buckets have:
  - Blue background highlight
  - Checked square icon
- Unselected buckets have:
  - White background
  - Empty square icon

#### Bulk Selection
- **Select All**: Selects all buckets currently visible (after filtering)
- **Deselect All**: Clears all selections

### 7. Gather Data

#### Option A: Track Selected Buckets Only
1. Select one or more buckets
2. Click **"Gather Data"** button
3. Only the selected buckets will be included in the data gathering

#### Option B: Track All Buckets (Default)
1. Leave bucket selection empty (no buckets selected)
2. Click **"Gather Data"** button
3. All buckets will be tracked (default behavior)

### 8. Reset Form
- Click **"Reset"** button to clear all selections
- This resets:
  - Project selection
  - Phase selection
  - Date (to today)
  - Bucket selections

## Troubleshooting

### No Buckets Found

If you see "No buckets found. Check database connections.":

1. **Check Backend Logs**
   ```bash
   tail -f log/migration_tracker_api.log
   ```
   Look for error messages about:
   - Connection failures
   - Table not found errors
   - Schema mismatches

2. **Verify Database Connections**
   - Check that environment variables are set:
     ```bash
     export MT_BLACKPEARL_HOST=localhost
     export MT_BLACKPEARL_PORT=5432
     export MT_BLACKPEARL_DATABASE=tapesystem
     export MT_BLACKPEARL_USERNAME=postgres
     export MT_BLACKPEARL_PASSWORD=your_password
     
     export MT_RIO_HOST=localhost
     export MT_RIO_PORT=5432
     export MT_RIO_DATABASE=rio_db
     export MT_RIO_USERNAME=postgres
     export MT_RIO_PASSWORD=your_password
     ```

3. **Check Database Schema**
   - The service tries multiple query patterns:
     - `SELECT name FROM buckets` (direct table)
     - `SELECT bucket_name FROM objects GROUP BY bucket_name` (aggregated)
   - If your schema is different, check the logs for available tables
   - You may need to update `BucketService.java` with the correct table/column names

4. **Verify Tables Exist**
   - Connect to your PostgreSQL databases and check:
     ```sql
     -- BlackPearl
     \c tapesystem
     \dt
     
     -- Rio
     \c rio_db
     \dt
     ```

### Database Connection Issues

If databases show as "Connected" but buckets don't load:

- The connection test might be passing, but the actual query is failing
- Check the backend logs for detailed error messages
- Verify that the database user has SELECT permissions on the bucket/object tables

## Visual Guide

```
┌─────────────────────────────────────────┐
│ Gather Migration Data                   │
├─────────────────────────────────────────┤
│ Project: [DataMigration ▼]              │
│ Phase:   [database-backups ▼]          │
│ Date:    [01/12/2026]                   │
│                                         │
│ ┌─────────────────────────────────────┐ │
│ │ Select Buckets (Optional)          │ │
│ │ [Show Buckets] button               │ │
│ │                                     │ │
│ │ ┌───────────────────────────────┐ │ │
│ │ │ 🔍 Search buckets...           │ │ │
│ │ │ 2 of 15 selected               │ │ │
│ │ │ [Select All] | [Deselect All] │ │ │
│ │ │                               │ │ │
│ │ │ ☑ backup-2024-01              │ │ │
│ │ │   [BlackPearl] 1,234 objects  │ │ │
│ │ │                               │ │ │
│ │ │ ☑ backup-2024-02              │ │ │
│ │ │   [Rio] 5,678 objects         │ │ │
│ │ │                               │ │ │
│ │ │ ☐ backup-2024-03              │ │ │
│ │ │   [BlackPearl] 9,012 objects  │ │ │
│ │ └───────────────────────────────┘ │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ Database Status:                        │
│ ✅ BlackPearl DB: Connected            │
│ ✅ Rio DB: Connected                   │
│                                         │
│              [Reset]  [Gather Data]     │
└─────────────────────────────────────────┘
```

## Best Practices

1. **Use Search for Large Lists**: If you have many buckets, use the search to quickly find the ones you need
2. **Select All When Appropriate**: If you want most buckets, select all and then deselect the few you don't need
3. **Verify Selection**: Check the counter before gathering data to ensure you have the right number selected
4. **Check Logs**: If buckets don't load, always check the backend logs for detailed error information

## Technical Notes

- Bucket selection is optional - if no buckets are selected, all buckets are tracked
- Selection state is maintained while on the page but resets when navigating away
- The service tries multiple SQL query patterns to accommodate different database schemas
- Errors are logged to the backend log file for debugging
