# Scripts

This folder contains repeatable developer utilities for environment setup, synthetic-data ingestion, validation, testing, and demonstration preparation.

Scripts should be non-interactive where practical, document prerequisites and parameters, fail clearly, and avoid embedding credentials or machine-specific paths.

## Local development launcher

The PowerShell launcher starts PostgreSQL/pgvector and gbrain through Docker Compose while running Spring Boot and Vite directly on the host for fast frontend reloads. It reuses already-running infrastructure without recreating it.

```powershell
.\scripts\start-dev.ps1
```

If the full Docker application is running, switch modes with `docker compose stop backend frontend` first (not `docker compose down`). The frontend moves from port 3000 to <http://localhost:5173>; the backend stays on port 8080. Existing database and gbrain volumes are reused. In-flight video files in Docker's backend upload volume are not shared with the host; finish pending video ingestion before switching modes.

The root `.env` must contain both provisioned gbrain credentials. The launcher uses Compose's configuration parser (never executes `.env` as code), passes backend settings including OAuth, JWT, transcription, and video overrides to the host processes, and translates PostgreSQL/gbrain URLs to their published localhost ports. Video storage uses the root `.env` path relative to `backend`, or `backend/data/uploads` by default. Java 21, Docker Desktop, npm, and provisioned gbrain are required; host video ingestion also needs FFmpeg and ffprobe on PATH (or configured executable paths).

Empty or whitespace-only shell overrides for forwarded backend settings and Spring's two multipart size overrides are temporarily ignored so root `.env` values or application defaults can be used (PowerShell 7 can export empty strings). Nonempty shell overrides still take precedence. Empty multipart overrides otherwise prevent Spring Boot from binding upload limits. Original shell values are restored on exit, including with `-CheckOnly`; existing credentials do not need re-provisioning for this case.

Use `./scripts/start-dev.ps1 -CheckOnly` to validate configuration without starting or stopping services. The launcher waits for infrastructure health and application HTTP readiness, reports port conflicts, and writes process logs to a unique `knowledgebridge-dev-*` directory under the system temporary directory. It restores the caller's environment and stops local process trees on exit. It stops only infrastructure services it started, leaving pre-existing services running. Use `-KeepDatabase` (alias `-KeepServices`) to retain both launcher-started PostgreSQL and gbrain. No volumes are deleted.

Run `./scripts/test-start-dev.ps1` for dependency-free regression checks of the launcher's configuration mapping and service ownership rules. These checks do not launch applications or mutate Docker services.

Use `./scripts/start-dev.ps1 -VerifyStartup` for an actual startup check: it waits for both HTTP endpoints, then exits through normal cleanup. It temporarily starts missing infrastructure and may resume pending ingestion, just like a regular launch. Readiness checks use IPv4 loopback to avoid `localhost` IPv6 fallback delays with Vite. A failed child is reported by name with its exit code and log filenames.

The launcher passes `-PdevtoolsRestart=false` to `bootRun`. Disabling the DevTools restart thread before Java main ensures Spring startup failures produce a failing Gradle exit code instead of a misleading exit code 0. Vite still reloads frontend changes; restart the launcher after Java changes. Direct `gradlew bootRun` retains DevTools restart unless the same property is supplied.

### Legacy V7 query-cache history

A database that applied `V7__create_query_cache.sql` before the merge can fail validation because the identical SQL is now named `V10__create_query_cache.sql`. Keep the backend stopped, take a custom-format `pg_dump` backup outside the repository, and verify that backup before reconciling history. Do not delete the volume or run broad Flyway repair.

`reconcile-query-cache-history.sql` supports only the known V7 cache/V8 video checksums and cache table layout. It atomically updates the cache history entry's version and filename to V10, preserving its checksum, application rows, cached answers, and hit counts. It refuses unexpected histories/layouts and is safe to repeat after a successful reconciliation. The checksums are compared against real Flyway migrations in `LegacyCacheHistoryTests`.

After backup verification, run this from the repository root with PostgreSQL running (these names assume the default database/user):

```powershell
Get-Content -Raw scripts/reconcile-query-cache-history.sql |
    docker compose exec -T postgres psql -U knowledgebridge -d knowledgebridge -v ON_ERROR_STOP=1
.\scripts\start-dev.ps1 -VerifyStartup
```

Normal startup then validates the existing migrations and applies V11. Databases with a different collision, checksum, or schema require separate investigation.

The older Bash launcher starts PostgreSQL and host processes only; it does not yet provide the PowerShell launcher's gbrain/environment/lifecycle behavior:

```bash
bash ./scripts/start-dev.sh
```

For Bash, `--keep-database` leaves PostgreSQL running. Do not use that launcher as a substitute for the updated PowerShell workflow without configuring gbrain and the host backend environment separately.

## Purge all workspace content

`purge-documents.ps1` resets the dedicated local Compose deployment to an empty knowledge workspace. It includes **all knowledge items** (manual notes, uploaded documents, and video transcripts), failed/pending items, ingestion attempts, entity detections and associations, cached answers, and query usage history. It also hard-deletes gbrain pages, entity stubs, chunks/embeddings, versions, queued jobs, request/recovery payloads and derived content metadata. Retained videos, orphan audio and other files in the configured upload storage are removed.

Preview from the repository root; the default does not stop services or delete data:

```powershell
.\scripts\purge-documents.ps1
```

Before applying, stop host launchers/backends/workers and database clients. PostgreSQL must already be running. Take any backup you need outside the repository and selected storage roots. Review the project, databases, directories, volumes and counts printed by the preview, then run:

```powershell
.\scripts\purge-documents.ps1 -Apply
```

`-Apply` requests confirmation through PowerShell. For an explicitly reviewed unattended reset, use `-Apply -Confirm:$false`. `-Apply -WhatIf` previews without deleting or stopping services.

The script stops Compose's backend, frontend and gbrain, verifies that no other database clients remain, and leaves application services stopped. Restart your normal Compose stack or `scripts/start-dev.ps1` afterwards. This also clears the backend's in-memory adapter/cache. Application accounts/passwords/roles, engine OAuth credentials, source/model configuration, database migrations and ID sequences survive; knowledge revision counters increase. The engine's local configuration and writer credentials survive while derived files and audit/usage logs in its volume are removed. Existing credentials do not need reprovisioning.

It resolves `.env` and shell settings with Compose, without printing credentials. Host storage defaults to `backend/data/uploads`; a relative `KNOWLEDGEBRIDGE_VIDEO_STORAGE_DIRECTORY` is relative to `backend`, matching `start-dev.ps1`. Docker's `/data/uploads` named volume is cleaned independently. Use `-HostUploadDirectory` to select a different dedicated host storage folder, and `-AdditionalUploadDirectory` for old dedicated storage roots after configuration changes. A retained source path outside the selected roots blocks the purge. Project/system roots, protected repository folders, symlinks and junctions are rejected.

Use `-ComposeFile` (an array for overrides) and `-ProjectName` to select another **dedicated** deployment. The running containers must match the resolved database and volume configuration. This script supports the current application content schema (V11–V13) and the pinned gbrain migration 196. It rejects unknown tables/versions, shared or canonical-checkout gbrain sources, engine-managed object-storage attachments, and bind-mounted engine/upload storage. It does not reset other deployments, user-selected original files, exports, external logs, backups, or PostgreSQL WAL/backups; this is logical deletion, not secure disk erasure.

Each database cleanup is transactional; the combined database/filesystem operation is not atomic. A failure returns an error and leaves services stopped. Fix the reported condition and rerun; missing files and already-empty content are safe. Do not resume ingestion after a partial failure until the purge succeeds.

Verification:

```powershell
.\scripts\test-purge-documents.ps1
.\scripts\test-purge-documents.ps1 -Integration
```

The first command needs PowerShell 7 and Node, and checks path/storage safety without touching Docker. The optional integration run needs Docker, the built `knowledgebridge-gbrain` image, and the PostgreSQL image. It creates a separate disposable Compose project with no published ports, applies real application migrations, starts the pinned gbrain, seeds synthetic content, checks preview/WhatIf and refusal cases, purges twice, then verifies engine restart and authentication with the same OAuth credential. It makes no AI provider calls and removes only its own test project/volumes afterwards.

## gbrain provisioning and smoke test

After PostgreSQL and gbrain have been initialized, provision the source-bound backend OAuth client once:

```powershell
.\scripts\provision-gbrain.ps1
```

The script stores the generated client id and one-time secret in the ignored root `.env`. When rotating credentials, it registers and saves the replacement before revoking the previous client id.

Native entity extraction uses a second credential:

```powershell
.\scripts\provision-gbrain.ps1 -Extraction
```

The Bash equivalent is `bash ./scripts/provision-gbrain.sh --extraction`. This credential keeps the `knowledgebridge` source restriction but omits the slug-prefix fence so gbrain can create `people/` and `companies/` stubs within that source. Its separate `GBRAIN_EXTRACTION_OAUTH_CLIENT_ID` and `GBRAIN_EXTRACTION_OAUTH_CLIENT_SECRET` are stored only in `.env`. The document credential keeps its existing prefix restriction. Recreate the backend after provisioning so Compose passes the new credential to the adapter. See `backend/src/main/java/com/capstone/knowledgebridge/entities/README.md` for behavior and extraction limits.

On Linux or macOS, use the Bash equivalents (they need `curl` and `jq`). `test-gbrain-mcp.sh` reads `GBRAIN_OAUTH_*` and `GBRAIN_PORT` from the shell before `.env`, so it can target another instance. `provision-gbrain.sh` also creates the database-only `knowledgebridge` source if it does not exist yet:

```bash
docker compose up -d --build --wait gbrain
bash ./scripts/provision-gbrain.sh
bash ./scripts/test-gbrain-mcp.sh                     # --keep-smoke-page, --capture-fixtures
```

If another project already uses host port 5432, set `POSTGRES_PORT` (for example `5433`) in `.env`; gbrain reaches PostgreSQL over the Compose network either way.

Start gbrain and run the live protocol/embedding smoke test:

```powershell
docker compose up -d --build --wait gbrain
.\scripts\test-gbrain-mcp.ps1
```

The smoke test negotiates MCP, verifies source/scopes and required tools, writes a synthetic page, retrieves it using a semantic paraphrase, requires `vector_enabled: true`, and synthesizes a cited answer. It then exercises the page lifecycle the backend adapter depends on: `get_page`, `delete_page` (twice), `get_page` of the deleted page, `restore_page` (twice), and `get_page` of a missing slug. Finally it soft-deletes the page. Pass `-KeepSmokePage` only when inspecting the page manually afterward.

To refresh the backend test fixtures from the live server, add `-CaptureFixtures` (`--capture-fixtures` in Bash):

```powershell
.\scripts\test-gbrain-mcp.ps1 -CaptureFixtures
```

This writes the lifecycle responses to `backend/src/test/resources/gbrain/fixtures/`. Values named `instructions`, `path`, `source_path`, and `source_uri` are replaced with `<sanitized>`. Review the diff before committing; the fixtures contain only the synthetic smoke page.

## Disposable live adapter test

`test-gbrain-live.sh` checks the backend Java adapter end to end without leaving test data on this machine. It starts PostgreSQL and gbrain as a separate Compose project (`kb-gbrain-live`, gbrain on port `3132`, PostgreSQL not published), provisions a source-bound client inside that instance, and runs `gradlew liveTest`. It then always runs `docker compose down --volumes`, so the pages, logs, caches, and OAuth client are deleted with the instance. Your dev stack and root `.env` are not touched. Only the built image is kept, and it holds no test data.

```bash
bash ./scripts/test-gbrain-live.sh
```

Provider keys and model overrides come from `.env`, exactly as for the dev stack: `OPENROUTER_API_KEY` or `OPENAI_API_KEY`, with OpenRouter used when both are set. The test sends synthetic text to the embedding provider and makes one paid synthesis call. Copies held by the provider are outside what teardown can remove.
