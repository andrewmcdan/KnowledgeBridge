# jira

The adapter between KnowledgeBridge and Jira Cloud. It knows nothing about knowledge items or gbrain. Sync and the HTTP routes live in `jiraintegration`.

## Contents

- `JiraClient`: the interface the rest of the application uses.
- `McpJiraClient` and `JiraMcpTransport`: the implementation over Atlassian's MCP server.
- `JiraResponseMapper`: converts Atlassian's JSON to the records in `model`.
- `InMemoryJiraClient`: fixed sample data, used when `JIRA_MODE=in-memory`.
- `JiraProperties` and `JiraConfiguration`: settings and wiring.
- `JiraException` and `JiraErrorCode`: the adapter's only failure type.

## Setup

Set `JIRA_ENABLED=true` and the other `JIRA_*` variables described in `.env.example`. With Jira disabled or unconfigured, the application still starts and calls fail with `NOT_CONFIGURED`.

## Behavior to preserve

- Search must pass `view: "full"`; otherwise Atlassian silently omits descriptions, parents, comments, and links.
- `initialize` and `tools/list` succeed for under-scoped tokens, so `status()` calls a real tool.
- Comments embedded in a work item can be incomplete. Compare `commentTotal` with `comments` and call `listComments` when they differ.
- There are no automatic retries yet; a rate limit or outage surfaces as a `JiraException`.

See `Docs/Jira-Integration-Design.md` for the protocol findings.

## Live check

`JiraLiveTests` calls the real site and is excluded from `check`. From `backend`, with the root `.env` exported and Docker running:

```bash
JIRA_ENABLED=true ./gradlew liveTest --tests '*JiraLiveTests'
```
