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

The older Bash launcher starts PostgreSQL and host processes only; it does not yet provide the PowerShell launcher's gbrain/environment/lifecycle behavior:

```bash
bash ./scripts/start-dev.sh
```

For Bash, `--keep-database` leaves PostgreSQL running. Do not use that launcher as a substitute for the updated PowerShell workflow without configuring gbrain and the host backend environment separately.

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
