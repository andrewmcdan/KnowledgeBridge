# Backend

This folder contains the Spring Boot application. It should hold REST controllers, application services, persistence code, database migrations, security configuration, tests, and shared error handling.

Organize the Java code around the documented responsibilities: `auth`, `users`, `knowledge`, `ingestion`, `gbrain`, `query`, `cache`, `entities`, `usage`, `admin`, `common`, and `configuration`. All communication with the external gbrain service belongs behind the dedicated `gbrain` adapter.

## Development

Use Java 21 and the included Gradle wrapper:

```powershell
.\gradlew.bat test
.\gradlew.bat bootRun
```

Integration tests use Testcontainers with the `pgvector/pgvector:pg17` image, so Docker must be running. The initial Flyway migration enables the `vector` extension. For local application startup, Spring Boot can start the PostgreSQL/pgvector service declared in `compose.yaml`.
