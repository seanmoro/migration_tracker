# Frontend Quick Start

## ✅ Dependencies Installed!

You're ready to go. Since you're already in the `frontend` directory:

## Start the Frontend

```bash
npm run dev
```

This will:
1. Start the Vite dev server
2. Show you the URL (usually `http://localhost:3000`)
3. Keep running (don't close the terminal!)

## What You'll See

```
  VITE v5.x.x  ready in XXX ms

  ➜  Local:   http://localhost:3000/
  ➜  Network: use --host to expose
```

## Full Stack Setup

### Terminal 1: Backend
```bash
cd backend
mvn spring-boot:run
```
Wait for: `Started MigrationTrackerApiApplication`

### Terminal 2: Frontend (you're here!)
```bash
# You're already in frontend directory
npm run dev
```

### Browser
Open: `http://localhost:3000`

## Troubleshooting

### "vite: command not found"
- Run: `npm install` (you just did this! ✅)

### Port 3000 already in use
- Kill the process: `lsof -ti:3000 | xargs kill`
- Or change port in `vite.config.ts`

### Can't connect to backend
- Make sure backend is running on port 8080
- Check: `curl http://localhost:8080/api/dashboard/stats`
- Check browser console (F12) for errors

## Next Steps

1. ✅ Dependencies installed
2. ✅ Run `npm run dev`
3. ✅ Open `http://localhost:3000` in browser
4. ✅ Test the full stack!

Enjoy! 🎉
