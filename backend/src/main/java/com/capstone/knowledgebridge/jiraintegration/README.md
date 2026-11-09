# jiraintegration

Connects the `jira` adapter to the rest of KnowledgeBridge. Jira communication stays in `jira`; gbrain communication stays behind `IngestionService`.

## Contents

- `JiraIntegrationController`: the `/api/integrations/jira` routes.
- `JiraMarkdownConverter`: builds the Markdown page gbrain stores for one work item.
- `JiraSyncService`: copies a project's work items into the knowledge base through `IngestionService`.
- `JiraSyncRun` and its repository: history and progress for each sync.

## Endpoints

All routes are admin only.

| Method | Path | Success |
| --- | --- | --- |
| `GET` | `/api/integrations/jira/status` | `200`, `{ "configured", "reachable", "failure" }` |
| `GET` | `/api/integrations/jira/projects` | `200`, array of `{ "id", "key", "name" }` |
| `POST` | `/api/integrations/jira/projects/{key}/sync` | `202`, the new run; `400` for an invalid key; `409` if the project is already syncing |
| `GET` | `/api/integrations/jira/sync-runs` | `200`, the 50 most recent runs, newest first |
| `GET` | `/api/integrations/jira/sync-runs/{id}` | `200`, one run, for polling |

Jira failures return `503 JIRA_NOT_CONFIGURED` or `502 JIRA_UNAVAILABLE`. `/status` reports them in its body instead.

## Sync behavior

- Each work item becomes one knowledge item, matched on later syncs by its key.
- An item is skipped when Jira's `updated` time hasn't changed, unless its last ingestion failed.
- Items trashed or deactivated in KnowledgeBridge stay that way.
- One bad work item adds to `failedCount` and the run continues. A Jira failure ends the run as `FAILED`; a later sync resumes by skipping what's already current.
- A run still active when the application restarts is marked `FAILED` with code `INTERRUPTED`.
- Each ingested item also runs gbrain's entity extraction, so a first sync makes one LLM call per work item.

See `Docs/Jira-Integration-Design.md` for the full design.
