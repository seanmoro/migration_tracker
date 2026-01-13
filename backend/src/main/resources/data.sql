-- Test data for Migration Tracker
-- This file will be executed on application startup if spring.sql.init.mode=always

-- Customers
INSERT OR IGNORE INTO customer (id, name, created_at, last_updated, active) VALUES
('customer-1', 'BigDataCo', '2024-01-15', '2024-01-15', 1),
('customer-2', 'Acme Corporation', '2024-02-01', '2024-02-01', 1),
('customer-3', 'TechStart Inc', '2024-02-20', '2024-02-20', 1),
('customer-4', 'MediaVault Systems', '2024-03-10', '2024-03-10', 1);

-- Projects
INSERT OR IGNORE INTO migration_project (id, name, customer_id, type, created_at, last_updated, active) VALUES
('project-1', 'DeepDive', 'customer-1', 'IOM_BUCKET', '2024-01-20', '2024-01-20', 1),
('project-2', 'Archive2024', 'customer-1', 'IOM_BUCKET', '2024-02-05', '2024-02-05', 1),
('project-3', 'DataMigration', 'customer-2', 'RIO_CRUISE_DIVA', '2024-02-15', '2024-02-15', 1),
('project-4', 'BackupRestore', 'customer-3', 'IOM_EXCLUSION', '2024-03-01', '2024-03-01', 1),
('project-5', 'ContentArchive', 'customer-4', 'IOM_BUCKET', '2024-03-15', '2024-03-15', 1);

-- Phases
INSERT OR IGNORE INTO migration_phase (id, name, type, migration_id, source, target, created_at, last_updated, target_tape_partition) VALUES
('phase-1', 'logs-archive', 'IOM_BUCKET', 'project-1', 'hot-storage', 'tape-vault', '2024-01-25', '2024-01-25', 'TAPE-01'),
('phase-2', 'backups-2024', 'IOM_BUCKET', 'project-1', 'disk', 'tape', '2024-01-25', '2024-01-25', 'TAPE-02'),
('phase-3', 'media-files', 'IOM_BUCKET', 'project-2', 'primary-storage', 'archive-storage', '2024-02-10', '2024-02-10', NULL),
('phase-4', 'database-backups', 'RIO_CRUISE', 'project-3', 'source-broker', 'target-broker', '2024-02-20', '2024-02-20', NULL),
('phase-5', 'excluded-data', 'IOM_EXCLUSION', 'project-4', 'source-domain', 'target-domain', '2024-03-05', '2024-03-05', NULL),
('phase-6', 'video-content', 'IOM_BUCKET', 'project-5', 'fast-storage', 'cold-storage', '2024-03-20', '2024-03-20', 'TAPE-03');

-- Reference Data Points (baseline)
INSERT OR IGNORE INTO migration_data (id, created_at, last_updated, timestamp, migration_phase_id, user_id, source_objects, source_size, target_objects, target_size, type, target_scratch_tapes) VALUES
('ref-1', '2024-01-25', '2024-01-25', '2024-01-25', 'phase-1', NULL, 1000, 1073741824000, 0, 0, 'REFERENCE', NULL),
('ref-2', '2024-01-25', '2024-01-25', '2024-01-25', 'phase-2', NULL, 500, 536870912000, 0, 0, 'REFERENCE', NULL),
('ref-3', '2024-02-10', '2024-02-10', '2024-02-10', 'phase-3', NULL, 2000, 2147483648000, 0, 0, 'REFERENCE', NULL),
('ref-4', '2024-02-20', '2024-02-20', '2024-02-20', 'phase-4', NULL, 750, 805306368000, 0, 0, 'REFERENCE', NULL),
('ref-5', '2024-03-05', '2024-03-05', '2024-03-05', 'phase-5', NULL, 300, 322122547200, 0, 0, 'REFERENCE', NULL),
('ref-6', '2024-03-20', '2024-03-20', '2024-03-20', 'phase-6', NULL, 1500, 1610612736000, 0, 0, 'REFERENCE', NULL);

-- Migration Data Points (progress over time)
-- Phase 1: logs-archive (1000 objects, progressing well)
INSERT OR IGNORE INTO migration_data (id, created_at, last_updated, timestamp, migration_phase_id, user_id, source_objects, source_size, target_objects, target_size, type, target_scratch_tapes) VALUES
('data-1-1', '2024-02-01', '2024-02-01', '2024-02-01', 'phase-1', NULL, 1000, 1073741824000, 200, 214748364800, 'DATA', 2),
('data-1-2', '2024-02-15', '2024-02-15', '2024-02-15', 'phase-1', NULL, 1000, 1073741824000, 450, 483183820800, 'DATA', 5),
('data-1-3', '2024-03-01', '2024-03-01', '2024-03-01', 'phase-1', NULL, 1000, 1073741824000, 680, 730144440320, 'DATA', 8),
('data-1-4', '2024-03-15', '2024-03-15', '2024-03-15', 'phase-1', NULL, 1000, 1073741824000, 850, 912680550400, 'DATA', 10),
('data-1-5', '2024-04-01', '2024-04-01', '2024-04-01', 'phase-1', NULL, 1000, 1073741824000, 950, 1019374540800, 'DATA', 12);

-- Phase 2: backups-2024 (500 objects, slower progress)
INSERT OR IGNORE INTO migration_data (id, created_at, last_updated, timestamp, migration_phase_id, user_id, source_objects, source_size, target_objects, target_size, type, target_scratch_tapes) VALUES
('data-2-1', '2024-02-01', '2024-02-01', '2024-02-01', 'phase-2', NULL, 500, 536870912000, 50, 53687091200, 'DATA', 1),
('data-2-2', '2024-02-20', '2024-02-20', '2024-02-20', 'phase-2', NULL, 500, 536870912000, 120, 128849018880, 'DATA', 2),
('data-2-3', '2024-03-10', '2024-03-10', '2024-03-10', 'phase-2', NULL, 500, 536870912000, 220, 236223201280, 'DATA', 3),
('data-2-4', '2024-04-01', '2024-04-01', '2024-04-01', 'phase-2', NULL, 500, 536870912000, 300, 322122547200, 'DATA', 4);

-- Phase 3: media-files (2000 objects, good progress)
INSERT OR IGNORE INTO migration_data (id, created_at, last_updated, timestamp, migration_phase_id, user_id, source_objects, source_size, target_objects, target_size, type, target_scratch_tapes) VALUES
('data-3-1', '2024-02-20', '2024-02-20', '2024-02-20', 'phase-3', NULL, 2000, 2147483648000, 400, 429496729600, 'DATA', NULL),
('data-3-2', '2024-03-05', '2024-03-05', '2024-03-05', 'phase-3', NULL, 2000, 2147483648000, 900, 966367641600, 'DATA', NULL),
('data-3-3', '2024-03-20', '2024-03-20', '2024-03-20', 'phase-3', NULL, 2000, 2147483648000, 1400, 1503238553600, 'DATA', NULL),
('data-3-4', '2024-04-05', '2024-04-05', '2024-04-05', 'phase-3', NULL, 2000, 2147483648000, 1800, 1932735283200, 'DATA', NULL);

-- Phase 4: database-backups (750 objects, steady progress)
INSERT OR IGNORE INTO migration_data (id, created_at, last_updated, timestamp, migration_phase_id, user_id, source_objects, source_size, target_objects, target_size, type, target_scratch_tapes) VALUES
('data-4-1', '2024-03-01', '2024-03-01', '2024-03-01', 'phase-4', NULL, 750, 805306368000, 150, 161061273600, 'DATA', NULL),
('data-4-2', '2024-03-15', '2024-03-15', '2024-03-15', 'phase-4', NULL, 750, 805306368000, 320, 343597383680, 'DATA', NULL),
('data-4-3', '2024-04-01', '2024-04-01', '2024-04-01', 'phase-4', NULL, 750, 805306368000, 500, 536870912000, 'DATA', NULL);

-- Phase 5: excluded-data (300 objects, just started)
INSERT OR IGNORE INTO migration_data (id, created_at, last_updated, timestamp, migration_phase_id, user_id, source_objects, source_size, target_objects, target_size, type, target_scratch_tapes) VALUES
('data-5-1', '2024-03-15', '2024-03-15', '2024-03-15', 'phase-5', NULL, 300, 322122547200, 30, 32212254720, 'DATA', NULL),
('data-5-2', '2024-04-01', '2024-04-01', '2024-04-01', 'phase-5', NULL, 300, 322122547200, 75, 80530636800, 'DATA', NULL);

-- Phase 6: video-content (1500 objects, no data yet - just reference)
-- Only reference data exists for this phase
