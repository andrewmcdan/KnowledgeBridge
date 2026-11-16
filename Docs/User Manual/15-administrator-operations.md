# 15. Administrator setup and operations

This section is for the person operating the local deployment. Ordinary users should obtain their address and account from that operator. Run commands below from the repository root unless a step states otherwise. These are repository-maintained local-development procedures, not a complete production hosting or identity-management runbook.

## Choose the deployment mode

The container stack needs Docker Desktop with its Linux engine running and Docker Compose. Host development also needs Java 21 and Node.js 24/npm. Java 25 by itself does not satisfy the backend's Java 21 toolchain.

For a mock demonstration without AI-provider credentials:

```powershell
docker compose -f compose.yaml -f compose.mock.yaml up --build
```

Open `http://localhost:3000`. The synthetic development accounts are listed in [Getting started](02-getting-started.md). Mock mode supports authentication, item creation/listing, and permission checks; it does not demonstrate real semantic search, synthesis, or entity extraction. Its in-memory engine metadata is lost on backend restart, while application records can remain in PostgreSQL.

For a real knowledge-engine deployment, first create root `.env` from `.env.example` **only if no `.env` already exists**. Preserve existing values; do not overwrite working credentials with an example file. Configure an approved AI-provider key and a deployment-appropriate JWT signing secret.

```powershell
docker compose up -d --build --wait gbrain
.\scripts\provision-gbrain.ps1
.\scripts\provision-gbrain.ps1 -Extraction
docker compose up --build
```

The provisioning scripts create the backend's separate engine access credentials. Do not substitute administrative bootstrap tokens for them. For an existing database volume whose separate gbrain database/role is missing, follow `docker/README.md` before provisioning. An existing role's password is not changed merely by editing `.env`.

## Host development and service checks

With the real engine provisioned, the PowerShell launcher can run Spring and Vite with hot reload:

```powershell
docker compose stop backend frontend
.\scripts\start-dev.ps1 -CheckOnly
.\scripts\start-dev.ps1
```

The host UI is normally `http://localhost:5173`, with the API on port 8080. The launcher reads root `.env`; direct `gradlew bootRun` does not. Existing shell environment values can override `.env`. The Bash launcher has different setup requirements; consult `scripts/README.md` instead of assuming equivalent behavior.

Useful read-only checks include:

```powershell
docker compose ps
Invoke-RestMethod http://localhost:8080/api/health
Invoke-RestMethod http://localhost:3131/health
docker compose config --quiet
```

Core API/database health is separate from provider credentials and actual query success. Test Search, Synthesis, entity extraction, and optional services individually with approved synthetic material. Paid live-engine checks are separate from routine automated tests. Avoid printing a fully resolved Compose configuration into a shared report because it can contain credentials.

## Configure optional services

| Capability | Important settings or prerequisites |
| --- | --- |
| Sign-in lifetime | `KNOWLEDGEBRIDGE_JWT_TTL`, default `PT8H`; signing uses `KNOWLEDGEBRIDGE_JWT_SECRET` |
| Engine query access | Backend gbrain URL and provisioned OAuth client ID/secret; separate extraction credentials |
| Video transcription | `OPENAI_API_KEY`, FFmpeg/FFprobe, writable upload storage, and transcription settings |
| AI file naming | `OPENAI_API_KEY` and optional `OPENAI_TITLE_*` overrides; separate from query synthesis |
| Video limits | `KNOWLEDGEBRIDGE_VIDEO_MAX_UPLOAD_SIZE`, `KNOWLEDGEBRIDGE_VIDEO_MAX_REQUEST_SIZE`, and `KNOWLEDGEBRIDGE_VIDEO_MAX_DURATION` |
| Jira | Enabled connection, site URL, email, scoped token, cloud ID, and the MCP endpoint |

For Jira, set `JIRA_ENABLED=true`, `JIRA_MODE=mcp`, `JIRA_SITE_URL`, `JIRA_EMAIL`, `JIRA_API_TOKEN`, and `JIRA_CLOUD_ID` in the deployment environment. The integration design documents the required Atlassian account configuration and read/search scopes. Never put a token in a user manual, screenshot, issue, or committed source file. `JIRA_MAX_RESPONSE_SIZE` and `JIRA_PAGE_SIZE` control bounded responses and search pages; use the checked-out `.env.example` and Compose configuration for supported overrides.

Changing an environment file does not change an already running process. Recreate or restart the relevant service using your deployment procedure, then verify its behavior. Some settings require coordinated changes across the engine and application; in particular, do not change embedding models or dimensions casually against existing indexed data.

## Stop without deleting data

For the Compose stack, use:

```powershell
docker compose stop
```

With a host launcher, Ctrl+C stops its managed processes. It preserves infrastructure that was already running, and its `-KeepServices` option retains infrastructure it started. Stop remaining project services explicitly if a complete shutdown is required. Do not stop unrelated applications or containers merely because their port resembles a project port.

Do not add a volume-removal option when the intent is a normal shutdown. Database records, engine data, and retained uploads are separate persistence concerns. Back up both application/engine databases, engine configuration/data, retained uploads, and required configuration securely before upgrades or destructive maintenance. Keep originals of uploaded media separately. Verify that a backup can be restored with the intended version; application lifecycle Restore is not a database-backup restore feature.

## Boundaries and operator references

The current interface does not provide a general backup/restore console, cache-management screen, audit-log browser, or complete account-management console. Advanced API and maintenance operations belong in an approved operator runbook. Old route inventories can include planned endpoints; confirm implemented contracts in `Docs/openapi.json`, current controllers, and the maintained integration documentation before scripting against them.

Preserve existing data when resolving a migration problem. Do not delete volumes, edit Flyway history, or run a repair blindly. Follow the migration guidance in the root README and `scripts/README.md` with a verified backup. Logs can help diagnose categories and stages, but share only the minimum safe diagnostic information.
