# Scripts Directory

This directory contains utility scripts for the Migration Tracker application.

## Directory Structure

- **[deployment/](./deployment/)** - Deployment and update scripts
- **[diagnostics/](./diagnostics/)** - Diagnostic and troubleshooting scripts
- **[load_test_data.sh](./load_test_data.sh)** - Load test data into the database

## Core Scripts (in `bin/`)

The main application scripts are in the `bin/` directory:
- `bin/migration_tracker` - Main application executable
- `bin/utils.sh` - Utility functions used by the main script

## Usage

Most scripts are designed to be run from the project root directory. They typically:
- Check for required files/directories
- Provide helpful error messages
- Include usage instructions

## Script Locations

- **Core scripts**: `bin/` (part of application)
- **Deployment scripts**: `scripts/deployment/`
- **Diagnostic scripts**: `scripts/diagnostics/`
- **Test scripts**: `scripts/` (e.g., `load_test_data.sh`)
