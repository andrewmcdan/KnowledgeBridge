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

Start gbrain and run the live protocol/embedding smoke test:

```powershell
docker compose up -d --build --wait gbrain
.\scripts\test-gbrain-mcp.ps1
```

The smoke test negotiates MCP, verifies source/scopes and required tools, writes a synthetic page, retrieves it using a semantic paraphrase, requires `vector_enabled: true`, synthesizes a cited answer, and soft-deletes the page. Pass `-KeepSmokePage` only when inspecting the page manually afterward.
