# Customer-Specific Database Restore

## Current Behavior

The application currently restores to a **single database per type**:
- **BlackPearl** → `tapesystem` database
- **Rio** → `rio_db` database

**No customer identification** - all customers share the same database.

## Use Cases for Customer-Specific Restores

If you need to restore databases for specific customers, you have several options:

### Option 1: Customer-Specific Database Names

Configure different database names per customer:
- `tapesystem_customer1`
- `tapesystem_customer2`
- `rio_db_customer1`
- etc.

**Implementation:**
- Add customer dropdown to restore UI
- Use customer ID/name to construct database name
- Restore to customer-specific database

### Option 2: Separate Database Connections

Configure separate PostgreSQL servers/instances per customer:
- Customer 1: `blackpearl-cust1.example.com`
- Customer 2: `blackpearl-cust2.example.com`

**Implementation:**
- Add customer selection to restore UI
- Store customer → database connection mapping
- Restore to customer's specific database server

### Option 3: Schema-Based Separation

Use schemas within a single database:
- `tapesystem.customer1` schema
- `tapesystem.customer2` schema

**Implementation:**
- Add customer selection to restore UI
- Restore to customer-specific schema
- Requires schema-aware restore commands

## Recommended Approach

For most use cases, **Option 1 (Customer-Specific Database Names)** is recommended:
- Simple to implement
- Easy to manage
- Clear separation
- Standard PostgreSQL approach

## Implementation Plan

If you want to add customer selection:

1. **Update UI** (`DatabaseUpload.tsx`):
   - Add customer dropdown
   - Fetch customers from API
   - Pass customer ID to restore endpoint

2. **Update API** (`DatabaseController.java`):
   - Accept `customerId` parameter
   - Construct database name: `tapesystem_${customerId}` or use customer name

3. **Update Service** (`PostgreSQLRestoreService.java`):
   - Use customer-specific database name
   - Create database if it doesn't exist

4. **Update Configuration**:
   - Store customer → database name mapping
   - Or derive from customer ID/name

## Current Workaround

Until customer selection is implemented, you can:

1. **Manually specify database name** via environment variable:
   ```bash
   export MT_BLACKPEARL_DATABASE=tapesystem_customer1
   ```

2. **Restore manually** using command line:
   ```bash
   zstd -d backup.tar.zst
   tar -xvf backup.tar
   pg_restore -d tapesystem_customer1 backup.dump
   ```

3. **Use different database connections** per customer in configuration

## Questions to Consider

1. **Do you need customer-specific databases?** Or is one shared database sufficient?
2. **How many customers?** This affects database management approach
3. **Do customers have separate PostgreSQL servers?** Or same server, different databases?
4. **Do you need to switch between customers?** Or always work with one at a time?

Let me know your requirements and I can implement customer-specific restore functionality!
