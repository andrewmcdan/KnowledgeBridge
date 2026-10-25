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

Prerequisites are Docker Desktop with its Linux engine running and Docker Compose. For local JVM/Vite development, also install Java 21 and Node.js 24 with npm. Java 25 alone does not satisfy the backend's Java 21 toolchain.

For a repeatable application startup without AI-provider credentials, use the existing in-memory engine adapter:

```powershell
docker compose -f compose.yaml -f compose.mock.yaml up --build
```

Open <http://localhost:3000> and sign in as `admin@acme.example` / `admin123` or `user@acme.example` / `user123`. The mock workflow supports authentication, item creation/listing, and permission checks. It does not provide semantic search, synthesis, or entity extraction, and its engine metadata is lost when the backend restarts. PostgreSQL application records remain in the volume. Stop with Ctrl+C, then `docker compose -f compose.yaml -f compose.mock.yaml down`; do not add `-v` unless you intend to delete this project's data.

For the real gbrain workflow, copy `.env.example` to `.env` on first setup, preserving any existing file. Configure `OPENAI_API_KEY` or `OPENROUTER_API_KEY`, then initialize and provision the engine:

```powershell
Copy-Item .env.example .env # First setup only.
docker compose up -d --build --wait gbrain
.\scripts\provision-gbrain.ps1
.\scripts\provision-gbrain.ps1 -Extraction
docker compose up --build
```

On an already initialized PostgreSQL volume, follow [the gbrain database setup](docker/README.md) if its database/role is missing. The engine has its own database and credentials inside the shared PostgreSQL/pgvector server. Its host health URL is <http://localhost:3131/health>. Real AI operations use the configured providers; routine tests use mocks.

Keep secrets only in the ignored root `.env`. Compose and the PowerShell launcher read it, with shell overrides taking precedence. Direct `gradlew bootRun` does not load it. Changing a password in `.env` does not change an already initialized PostgreSQL role password. See [the integration guide](Docs/Integration-and-authentication.md#integration-status-and-migration-history) before reusing a database from a branch with a different V8 migration.

After live engine provisioning, start host Spring Boot and Vite with hot reload:

```powershell
.\scripts\start-dev.ps1
```

If containerized backend/frontend services are running, first run `docker compose stop backend frontend`. The launcher serves Vite at <http://localhost:5173> and Spring at <http://localhost:8080>. It preserves pre-existing infrastructure and stops only infrastructure it started, unless `-KeepServices` is passed. Use `-CheckOnly` to check configuration. The older Bash launcher is not equivalent: configure gbrain and the backend environment separately as explained in [scripts/README.md](scripts/README.md).

## API contracts and verification

Use [Docs/openapi.json](Docs/openapi.json) and the [integration/authentication guide](Docs/Integration-and-authentication.md) for the agreed ingestion, list, retry, authentication, and error contracts. Normal users can list completed knowledge; ingestion and management remain admin-only. Logout clears the frontend token and acknowledges it through the API; stateless JWTs are not revoked server-side.

Before submitting changes:

```powershell
cd frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
cd ../backend
.\gradlew.bat check
cd ..
.\scripts\test-start-dev.ps1
docker compose config --quiet
docker compose -f compose.yaml -f compose.mock.yaml config --quiet
```

The backend checks require Java 21 and Docker/Testcontainers. They include formatting and the existing 100% line/branch coverage gate. `.github/workflows/ci.yml` runs these checks on pull requests and pushes to `main`/`dylan-auth`. Paid live-engine checks remain separate from routine CI.

## MVP scope

The MVP targets Markdown ingestion, admin and user roles, search and synthesis query modes, cited answers, exact-match caching, entity browsing, knowledge-gap messaging, content lifecycle management, and estimated usage reporting. Only synthetic business data should be used unless the sponsor approves another source.
