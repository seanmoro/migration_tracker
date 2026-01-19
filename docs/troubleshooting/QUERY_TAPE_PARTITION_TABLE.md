# Query tape.tape_partition Table

## Found Table
- Schema: `tape`
- Table: `tape_partition` (singular, not plural)

## Commands

### 1. Check Table Structure
```bash
# Describe the table
sudo -u postgres psql -d tapesystem -c "\d tape.tape_partition"

# Or get column info
sudo -u postgres psql -d tapesystem -c "
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'tape'
AND table_name = 'tape_partition'
ORDER BY ordinal_position;"
```

### 2. Query Partition Names
```bash
# Get all partitions (assuming there's a 'name' column)
sudo -u postgres psql -d tapesystem -c "
SELECT * FROM tape.tape_partition LIMIT 10;"

# Or if there's a name column
sudo -u postgres psql -d tapesystem -c "
SELECT DISTINCT name 
FROM tape.tape_partition 
WHERE name IS NOT NULL 
ORDER BY name;" 2>&1
```

### 3. Count Partitions
```bash
# Count total partitions
sudo -u postgres psql -d tapesystem -c "
SELECT COUNT(*) as total_partitions FROM tape.tape_partition;"
```
