# KnowledgeBridge

KnowledgeBridge is a capstone proof-of-concept for an AI-powered business knowledge management platform. It will ingest synthetic business knowledge, provide keyword and semantic search, synthesize answers with source citations, identify knowledge gaps, expose detected entities, and track estimated AI usage costs.

## Planned architecture

- `frontend/` - React web application for user Q&A and administration.
- `backend/` - Spring Boot REST API, application logic, persistence, authentication, and the gbrain adapter.
- `synthetic-data/` - Cohesive Markdown documents for the fictional company used in development and evaluation.
- `evaluation/` - Ground-truth questions, reliability results, and feasibility findings.
- `docker/` - Supporting container configuration used by the root Docker Compose stack.
- `scripts/` - Repeatable development, data-loading, and validation utilities.
- `Docs/` - Project brief and execution-plan source documents.

The gbrain knowledge engine is an external service boundary. Application code must access it through the backend adapter rather than modify or call its internals directly from the frontend.

## Getting started

Prerequisites are Docker Desktop with Docker Compose. Start the current application stack from the repository root:

```powershell
docker compose up --build
```

Open `http://localhost:3000`. The status page calls the Spring Boot health API and confirms PostgreSQL connectivity. Stop the services with `Ctrl+C`, then run `docker compose down`. Add `-v` to the down command only when you intentionally want to delete the local PostgreSQL volume.

For development with frontend hot-module replacement, start Spring Boot from `backend/` with `.\gradlew.bat bootRun`, then run `npm install` and `npm run dev` from `frontend/`. The Vite server is available at `http://localhost:5173` and proxies `/api` to port 8080.

Alternatively, start PostgreSQL in Docker and both application processes locally with one command:

```powershell
.\scripts\start-dev.ps1
```

On macOS, Linux, or WSL, use `bash ./scripts/start-dev.sh`. Both launchers stop PostgreSQL on exit unless their keep-database option is supplied.

## MVP scope

The MVP targets Markdown ingestion, admin and user roles, search and synthesis query modes, cited answers, exact-match caching, entity browsing, knowledge-gap messaging, content lifecycle management, and estimated usage reporting. Only synthetic business data should be used unless the sponsor approves another source.
