# jiraintegration

Connects the `jira` adapter to the rest of KnowledgeBridge. Jira communication stays in `jira`; gbrain communication stays behind `IngestionService`.

## Contents

- `JiraIntegrationController`: the `/api/integrations/jira` routes.
- `JiraMarkdownConverter`: builds the Markdown page gbrain stores for one work item.

## Endpoints

Both routes are admin only.

| Method | Path | Success |
| --- | --- | --- |
| `GET` | `/api/integrations/jira/status` | `200`, `{ "configured", "reachable", "failure" }` |
| `GET` | `/api/integrations/jira/projects` | `200`, array of `{ "id", "key", "name" }` |

Jira failures return `503 JIRA_NOT_CONFIGURED` or `502 JIRA_UNAVAILABLE`. `/status` reports them in its body instead.

See `Docs/Jira-Integration-Design.md` for the full design.
