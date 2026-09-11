# Docker

This folder contains supporting container assets. The root Compose configuration runs React, Spring Boot, gbrain, and one PostgreSQL/pgvector server containing separate `knowledgebridge` and `gbrain` databases.

Do not store API keys, passwords, local database files, or other secrets here. Provide safe example configuration where developers need to know required variables.

## gbrain

Fresh PostgreSQL volumes automatically run `postgres/init-gbrain.sh`. For an existing application database volume, provision gbrain once before starting it:

```powershell
docker compose up -d --wait postgres
docker compose exec -T postgres sh /docker-entrypoint-initdb.d/10-gbrain.sh
```

The setup is repeatable and preserves existing databases and passwords. Keep `POSTGRES_USER` and `POSTGRES_DB` distinct from `gbrain`. It installs extensions and the administrator-only event trigger required by upstream, and grants gbrain `BYPASSRLS` as required by its migrations, without granting superuser or database-creation privileges.

The local migration from the former `gbrain-postgres` service preserved all 70 tables (row counts verified). The retired `knowledgebridge_gbrain-postgres-data` volume and ignored `temp/gbrain-migration.dump` remain as rollback copies; the active stack uses only `postgres-data` for database storage. A clean-volume gbrain initialization and a restart against the migrated database were also verified.

`gbrain/Dockerfile` builds the unmodified [garrytan/gbrain upstream](https://github.com/garrytan/gbrain) at revision `a6be012a3bcfac42e279630aedec5cda4a450e29`, using Bun 1.3.13 and upstream's frozen dependency lockfile. This is our container packaging, not the sponsor-provided configuration mentioned in the brief.

From the repository root:

```powershell
docker compose up -d --build --wait gbrain
Invoke-RestMethod http://localhost:3131/health
docker compose ps
```

The full `docker compose up --build` command includes these services too. For local Spring/Vite development, run the gbrain command above alongside `scripts/start-dev.ps1` or `scripts/start-dev.sh`; use `-KeepDatabase` (PowerShell) or `--keep-database` (Bash) to keep the shared database available to gbrain when the launcher exits. Stop gbrain separately with `docker compose stop gbrain`.

The host port binds only to loopback. Set `GBRAIN_PORT` in a root `.env` file if 3131 is occupied. `GBRAIN_DB_PASSWORD` overrides the local-development password; use URL-safe characters because the value is also embedded in a connection URL. Changing it after database initialization requires changing the database role password as well. Both databases share the PostgreSQL service and its loopback-only host port (5432 by default).

The `gbrain-data` volume stores configuration under `/data/.gbrain`; `postgres-data` stores both databases, with gbrain using its own non-superuser role and database independently of Spring/Flyway. First startup initializes the engine with `--no-embedding`, so no provider credentials are needed. Subsequent starts apply upstream schema migrations. Back up both volumes before upgrading the pinned revision. Normal stop/down preserves them; `docker compose down -v` deletes them.

This supplies the engine service, not a completed application integration. Upstream exposes MCP over HTTP with OAuth at `/mcp`, rather than the brief's assumed REST ingestion/query API. The backend receives `GBRAIN_BASE_URL=http://gbrain:3131` for a future adapter, but no Java code consumes it yet. OAuth client provisioning and embedding/synthesis provider configuration remain separate work; semantic search is not enabled by this bootstrap. Admin bootstrap tokens are suppressed in container logs.
