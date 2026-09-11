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
