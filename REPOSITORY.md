# Repository Structure

This is a local Git repository for the Migration Tracker project.

## Repository Status

✅ **Initialized as Git repository**
✅ **All source code committed**
✅ **Proper .gitignore configured**
✅ **Line ending normalization configured**

## Branch Structure

- `main` - Main development branch (current)

## What's Tracked

### Source Code
- ✅ Backend (Spring Boot REST API)
- ✅ Frontend (React + TypeScript)
- ✅ CLI scripts and utilities
- ✅ Configuration templates

### Documentation
- ✅ README files
- ✅ Setup guides
- ✅ API documentation
- ✅ Improvement documentation

### Build Artifacts (Ignored)
- ❌ `backend/target/` - Maven build output
- ❌ `frontend/node_modules/` - npm dependencies
- ❌ `frontend/dist/` - Frontend build output
- ❌ `*.log` - Log files

### Sensitive Files (Ignored)
- ❌ `tracker.yaml` - Local configuration (use `tracker.yaml.example`)
- ❌ `.env` - Environment variables

## Commands

### View Repository Status
```bash
git status
```

### View Commit History
```bash
git log --oneline
```

### Create a New Branch
```bash
git checkout -b feature/your-feature-name
```

### Commit Changes
```bash
git add .
git commit -m "Your commit message"
```

### View What's Ignored
```bash
git status --ignored
```

## Next Steps (When Ready to Push to GitHub)

1. Create a repository on GitHub
2. Add remote:
   ```bash
   git remote add origin https://github.com/yourusername/migration-tracker.git
   ```
3. Push to GitHub:
   ```bash
   git push -u origin main
   ```

## Current Status

Repository is **local only** - no remote configured. All commits are stored locally.
