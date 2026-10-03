# Scripts

This folder contains repeatable developer utilities for environment setup, synthetic-data ingestion, validation, testing, and demonstration preparation.

Scripts should be non-interactive where practical, document prerequisites and parameters, fail clearly, and avoid embedding credentials or machine-specific paths.

## Local development launcher

The launchers start PostgreSQL/pgvector through Docker Compose while running Spring Boot and Vite directly on the host for fast reloads:

```powershell
.\scripts\start-dev.ps1
```

```bash
bash ./scripts/start-dev.sh
```

Press `Ctrl+C` to stop the application processes and database container. Use `-KeepDatabase` in PowerShell or `--keep-database` in Bash to leave PostgreSQL running. Local data remains in the named Docker volume either way.

## gbrain provisioning and smoke test

After PostgreSQL and gbrain have been initialized, provision the source-bound backend OAuth client once:

```powershell
.\scripts\provision-gbrain.ps1
```

The script stores the generated client id and one-time secret in the ignored root `.env`. When rotating credentials, it registers and saves the replacement before revoking the previous client id.

On Linux or macOS, use the Bash equivalents (they need `curl` and `jq`). `provision-gbrain.sh` also creates the database-only `knowledgebridge` source if it does not exist yet:

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
