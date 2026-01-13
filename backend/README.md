# Migration Tracker REST API Backend

Spring Boot REST API backend for the Migration Tracker application.

## Features

- ✅ RESTful API endpoints for all entities
- ✅ SQLite database integration
- ✅ CORS configuration for frontend
- ✅ Dashboard statistics
- ✅ Progress reporting and forecasting
- ✅ Export functionality (placeholder)

## Tech Stack

- **Spring Boot 3.2.0**
- **Java 21**
- **SQLite JDBC**
- **Maven**
- **SpringDoc OpenAPI** (Swagger)

## Building

### Prerequisites

- Java 21+
- Maven 3.8+

### Build

```bash
cd backend
mvn clean package
```

### Run

```bash
mvn spring-boot:run
```

Or run the JAR:

```bash
java -jar target/migration-tracker-api-1.0.0.jar
```

The API will be available at `http://localhost:8080/api`

## API Endpoints

### Customers
- `GET /api/customers` - List all customers
- `GET /api/customers/{id}` - Get customer by ID
- `GET /api/customers/search?name={name}` - Search customers
- `POST /api/customers` - Create customer
- `PUT /api/customers/{id}` - Update customer
- `DELETE /api/customers/{id}` - Delete customer

### Projects
- `GET /api/projects` - List all projects
- `GET /api/projects?customerId={id}` - List projects by customer
- `GET /api/projects/{id}` - Get project by ID
- `GET /api/projects/search?name={name}` - Search projects
- `POST /api/projects` - Create project
- `PUT /api/projects/{id}` - Update project
- `DELETE /api/projects/{id}` - Delete project

### Phases
- `GET /api/phases?projectId={id}` - List phases by project
- `GET /api/phases/{id}` - Get phase by ID
- `GET /api/phases/search?projectId={id}&name={name}` - Search phases
- `POST /api/phases` - Create phase
- `PUT /api/phases/{id}` - Update phase
- `DELETE /api/phases/{id}` - Delete phase

### Reports
- `GET /api/reports/phases/{id}/progress` - Get phase progress
- `GET /api/reports/phases/{id}/data` - Get phase data points
- `GET /api/reports/phases/{id}/forecast` - Get forecast
- `POST /api/reports/phases/{id}/export` - Export report

### Migration
- `POST /api/migration/gather-data` - Gather migration data
- `GET /api/migration/data?phaseId={id}` - Get migration data

### Dashboard
- `GET /api/dashboard/stats` - Get dashboard statistics
- `GET /api/dashboard/active-phases` - Get active phases
- `GET /api/dashboard/recent-activity` - Get recent activity

## API Documentation

Swagger UI is available at:
- `http://localhost:8080/api/swagger-ui.html`
- `http://localhost:8080/api/api-docs`

## Configuration

Database path is configured in `application.yml`. The default path is:
```
jdbc:sqlite:../resources/database/migrations.db
```

## Development

### Project Structure

```
backend/
├── src/main/java/com/spectralogic/migrationtracker/
│   ├── api/              # REST controllers
│   ├── model/            # Entity models
│   ├── repository/       # Data access layer
│   ├── service/          # Business logic
│   └── config/           # Configuration classes
└── src/main/resources/
    └── application.yml   # Application configuration
```

## Notes

- The `gather-data` endpoint currently creates placeholder data. 
  Full implementation requires PostgreSQL database connections.
- Export functionality is a placeholder and needs implementation.
- CORS is configured to allow requests from `http://localhost:*`

## Next Steps

1. Implement PostgreSQL database connections for data gathering
2. Complete export functionality (PDF, Excel, CSV)
3. Add authentication/authorization
4. Add input validation
5. Add error handling and logging improvements
