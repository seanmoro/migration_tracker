# Customer, Project, and Phase Relationship

## Overview

The Migration Tracker uses a hierarchical structure to organize migration data:

```
Customer
  └── Project
       └── Phase
            └── Migration Data
```

## Structure Details

### Customer
- **Purpose**: Represents a client or organization
- **Examples**: "AMC Networks", "NBC Universal", "ITV"
- **Properties**:
  - `id`: Unique identifier (UUID)
  - `name`: Customer name
  - `active`: Whether the customer is active
  - `createdAt`, `lastUpdated`: Timestamps

### Project
- **Purpose**: Represents a migration project for a customer
- **Relationship**: Belongs to one Customer (`customerId`)
- **Examples**: "amc sgl migration", "nbc la", "itv"
- **Properties**:
  - `id`: Unique identifier (UUID)
  - `name`: Project name
  - `customerId`: Reference to parent Customer
  - `type`: Project type (IOM_BUCKET, IOM_EXCLUSION, RIO_CRUISE_DIVA, RIO_CRUISE_SGL, RIO_CRUISE_OTHER)
  - `active`: Whether the project is active
  - `createdAt`, `lastUpdated`: Timestamps

**Note**: Projects are typically created manually through the UI. Each customer can have multiple projects.

### Phase
- **Purpose**: Represents a specific migration phase within a project
- **Relationship**: Belongs to one Project (`migration_id` = `project_id`)
- **Examples**: "logs-archive", "backups-2024", "media-files"
- **Properties**:
  - `id`: Unique identifier (UUID)
  - `name`: Phase name
  - `migration_id`: Reference to parent Project
  - `type`: Phase type (IOM_BUCKET, IOM_EXCLUSION, RIO_CRUISE)
  - `source`: Source storage domain or broker
  - `target`: Target storage domain or broker
  - `target_tape_partition`: Optional tape partition identifier
  - `createdAt`, `lastUpdated`: Timestamps

**Note**: Phases can be created manually or through the "Create New Phase" option in the Gather Data workflow.

### Migration Data
- **Purpose**: Stores actual migration statistics for a phase
- **Relationship**: Belongs to one Phase (`migration_phase_id`)
- **Properties**:
  - `id`: Unique identifier (UUID)
  - `migration_phase_id`: Reference to parent Phase
  - `timestamp`: Date/time of the data point
  - `source_objects`: Number of objects in source
  - `source_size`: Size in bytes in source
  - `target_objects`: Number of objects in target
  - `target_size`: Size in bytes in target
  - `target_scratch_tapes`: Number of scratch tapes (optional)
  - `type`: Data type (REFERENCE or DATA)

## Workflow

### Typical Workflow

1. **Create Customer** (if not exists)
   - Navigate to Customers page
   - Click "Add Customer"
   - Enter customer name

2. **Create Project** (if not exists)
   - Navigate to Projects page
   - Click "Add Project"
   - Select customer
   - Enter project name and type

3. **Restore Database**
   - Navigate to PostgreSQL Restore page
   - **Select Customer** (required)
   - Select database type (BlackPearl or Rio)
   - Upload backup file (.zst, .tar, .dump, etc.)
   - System creates customer-specific database: `tapesystem_customer_name` or `rio_db_customer_name`
   - After restore, automatically navigates to Gather Data page with customer pre-selected

4. **Gather Migration Data**
   - Navigate to Gather Data page (or arrive from restore)
   - **Select Project** (filtered by customer if customer was pre-selected)
   - **Select Phase** (or click "Create New Phase" to add one)
   - Select date
   - Select buckets (optional)
   - Click "Gather Data"

### Database Naming Convention

When restoring a PostgreSQL database:
- **BlackPearl**: `tapesystem_<customer_name>`
  - Example: `tapesystem_amc_networks`
- **Rio**: `rio_db_<customer_name>`
  - Example: `rio_db_nbc_universal`

Customer names are sanitized (lowercase, special characters replaced with underscores) for database names.

## Common Questions

### Why are Projects separate from Customers?

Projects allow a single customer to have multiple migration initiatives. For example:
- Customer: "AMC Networks"
  - Project 1: "amc sgl migration" (RIO_CRUISE_SGL)
  - Project 2: "amc archive" (IOM_BUCKET)

### Why are Phases under Projects?

Phases represent different stages or aspects of a migration project. For example:
- Project: "amc sgl migration"
  - Phase 1: "logs-archive" (IOM_BUCKET)
  - Phase 2: "backups-2024" (IOM_BUCKET)

### Can I skip creating a Project?

No. Projects are required because:
1. They provide organization and context for phases
2. They link phases to customers
3. They allow multiple migration initiatives per customer

### Can I create a Phase without a Project?

No. Phases must belong to a project. If you need a phase:
1. First create or select a project
2. Then create the phase (manually or via "Create New Phase" in Gather Data)

### How do I know which database to restore for a customer?

The restore workflow requires selecting a customer. The system automatically:
1. Creates a customer-specific database name
2. Restores to that database
3. Navigates to Gather Data with the customer pre-selected

This ensures data is properly isolated per customer.

## Database Schema

```sql
-- Customers
CREATE TABLE customer (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  created_at TEXT,
  last_updated TEXT,
  active INTEGER DEFAULT 1
);

-- Projects (linked to customers)
CREATE TABLE migration_project (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  customer_id TEXT NOT NULL,
  type TEXT NOT NULL,
  created_at TEXT,
  last_updated TEXT,
  active INTEGER DEFAULT 1,
  FOREIGN KEY (customer_id) REFERENCES customer(id)
);

-- Phases (linked to projects)
CREATE TABLE migration_phase (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  type TEXT NOT NULL,
  migration_id TEXT NOT NULL,  -- This is the project_id
  source TEXT NOT NULL,
  target TEXT NOT NULL,
  target_tape_partition TEXT,
  created_at TEXT,
  last_updated TEXT,
  FOREIGN KEY (migration_id) REFERENCES migration_project(id)
);

-- Migration Data (linked to phases)
CREATE TABLE migration_data (
  id TEXT PRIMARY KEY,
  migration_phase_id TEXT NOT NULL,
  timestamp TEXT NOT NULL,
  source_objects INTEGER,
  source_size INTEGER,
  target_objects INTEGER,
  target_size INTEGER,
  target_scratch_tapes INTEGER,
  type TEXT NOT NULL,
  created_at TEXT,
  last_updated TEXT,
  FOREIGN KEY (migration_phase_id) REFERENCES migration_phase(id)
);
```

## Best Practices

1. **Customer Naming**: Use clear, consistent customer names (e.g., "AMC Networks" not "amc" or "AMC")
2. **Project Naming**: Use descriptive project names that indicate the migration type (e.g., "amc sgl migration")
3. **Phase Naming**: Use specific phase names that describe what is being migrated (e.g., "logs-archive", "backups-2024")
4. **Database Isolation**: Each customer gets their own PostgreSQL database to ensure data isolation
5. **Project Creation**: Create projects before restoring databases, or create them as needed during the workflow

## Future Improvements

Potential enhancements to consider:
- Auto-create project from customer name during restore
- Project templates for common migration types
- Bulk phase creation
- Customer-specific database connection configuration
- Project-level reporting and analytics
