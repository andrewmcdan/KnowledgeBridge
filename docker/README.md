# Docker

This folder contains supporting container assets such as Dockerfiles, initialization files, health-check helpers, and service-specific configuration. The root Docker Compose configuration will orchestrate the React frontend, Spring Boot backend, PostgreSQL with pgvector, and the externally supplied gbrain service.

Do not store API keys, passwords, local database files, or other secrets here. Provide safe example configuration where developers need to know required variables.

