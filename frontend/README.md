# Migration Tracker Frontend

Modern React + TypeScript frontend for the Migration Tracker application.

## Features

- 📊 **Dashboard** - Overview of all migration activities
- 👥 **Customer Management** - Create and manage customers
- 📁 **Project Management** - Organize migrations by project
- 🔄 **Phase Management** - Track individual migration phases
- 📈 **Progress Tracking** - Visual progress charts and forecasting
- 📤 **Export Reports** - Export in PDF, Excel, CSV, JSON, HTML formats
- 📥 **Data Gathering** - Collect migration statistics from databases

## Tech Stack

- **React 18** - UI framework
- **TypeScript** - Type safety
- **Vite** - Build tool
- **React Router** - Routing
- **TanStack Query** - Data fetching and caching
- **Recharts** - Chart library
- **Tailwind CSS** - Styling
- **Axios** - HTTP client

## Getting Started

### Prerequisites

- Node.js 18+ 
- npm or yarn

### Installation

```bash
cd frontend
npm install
```

### Development

```bash
npm run dev
```

The app will be available at `http://localhost:3000`

### Build

```bash
npm run build
```

### Preview Production Build

```bash
npm run preview
```

## Project Structure

```
frontend/
├── src/
│   ├── api/           # API client functions
│   ├── components/    # Reusable UI components
│   ├── views/         # Page components
│   ├── types/         # TypeScript type definitions
│   ├── utils/         # Utility functions
│   ├── App.tsx        # Main app component
│   └── main.tsx       # Entry point
├── public/            # Static assets
└── package.json       # Dependencies
```

## API Integration

The frontend expects a REST API running on `http://localhost:8080/api` (configurable via `VITE_API_URL`).

### Required API Endpoints

- `GET /api/customers` - List customers
- `POST /api/customers` - Create customer
- `GET /api/projects` - List projects
- `POST /api/projects` - Create project
- `GET /api/phases` - List phases
- `POST /api/phases` - Create phase
- `GET /api/reports/phases/:id/progress` - Get phase progress
- `POST /api/reports/phases/:id/export` - Export phase report
- `POST /api/migration/gather-data` - Gather migration data

## Environment Variables

Create a `.env` file:

```env
VITE_API_URL=http://localhost:8080/api
```

## Features in Detail

### Dashboard
- Summary statistics cards
- Active phases list
- Progress overview chart

### Customer Management
- List/search customers
- Create/edit/delete customers
- View customer projects

### Project Management
- List/search projects
- Create/edit/delete projects
- Navigate to project phases

### Phase Management
- List phases for a project
- Create/edit/delete phases
- View phase progress

### Progress Tracking
- Progress percentage and charts
- Forecast with ETA
- Data point history
- Export functionality

### Export Functionality
- Multiple formats (PDF, Excel, CSV, JSON, HTML)
- Customizable options
- Download directly from browser

## Development Notes

- The app uses React Query for data fetching and caching
- All API calls go through the `apiClient` with interceptors
- Components are organized by feature
- Tailwind CSS is used for styling
- TypeScript ensures type safety throughout

## Next Steps

1. Connect to backend API
2. Add authentication
3. Implement real-time updates
4. Add more chart types
5. Enhance export templates
6. Add mobile responsiveness improvements
