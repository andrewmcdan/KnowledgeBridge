# Jira integration

**Owner:** Adrian Edwards
**Last updated:** 2026-11-09
**Status:** Sync, Ask AI, the Jira View API, and an admin sync panel are implemented. The Jira View page and Knowledge Base source filter are not built yet.

## Overview

KnowledgeBridge syncs Jira work items into gbrain as knowledge pages so Ask AI can search, synthesize, and cite them like any other document. Administrators start a sync from the Knowledge Base page. API routes serve synced work items for a future Jira View.

Two backend packages carry the work:

- `jira` talks to Jira.
- `jiraintegration` syncs work items into the knowledge base and serves the API routes.

The integration is off by default, so the application runs without a Jira site.

## Non-goals

- Editing, creating, or transitioning Jira work items. Jira stays the source of truth.
- Mirroring Jira permissions per user.
- Real-time sync through webhooks or scheduled sync.
- Agentic, multi-step Jira reasoning at query time (see [Future work](#future-work)).

## Approach

gbrain's `synthesize` tool accepts only `question`, `since`, and `until`. It gathers evidence from pages it already stores and cites only those pages, so Jira content must exist as gbrain pages before Ask AI can use it.

KnowledgeBridge therefore copies each work item into gbrain as a Markdown page through the existing ingestion pipeline. Jira is read through Atlassian's Rovo MCP server. See [Atlassian MCP behavior](#atlassian-mcp-behavior).

## Architecture

```text
Frontend (admin sync panel)
        │  /api/integrations/jira/**
        ▼
jiraintegration ──► ingestion.IngestionService ──► gbrain adapter ──► gbrain
        │
        ▼
jira (JiraClient) ──► Atlassian Rovo MCP server
```

### `jira` package

Talks to Jira and nothing else. It knows nothing about knowledge items or gbrain.

| Class | Responsibility |
|---|---|
| `JiraClient` | Interface: list projects, search work items, get one work item, list comments, report connection status |
| `McpJiraClient` | Implements `JiraClient` with Atlassian's MCP tools |
| `JiraMcpTransport` | MCP over HTTP: Basic auth, session handling, error classification, and retries. It is separate from the gbrain transport and shares no code with it |
| `JiraResponseMapper` | Converts Atlassian's JSON to application records |
| `InMemoryJiraClient` | Fixed sample data for development and tests |
| `JiraProperties`, `JiraConfiguration` | Validated settings and wiring |
| `JiraException`, `JiraErrorCode` | The adapter's only failure type |

### `jiraintegration` package

| Class | Responsibility |
|---|---|
| `JiraSyncService` | Runs background syncs and hands items to `IngestionService` |
| `JiraMarkdownConverter` | Builds the title and Markdown page for one work item |
| `JiraSyncRun` and repository | Sync history and progress |
| `JiraWorkItem`, repository, and `JiraWorkItemService` | Structured fields for filtering, and work item reads |
| `JiraIntegrationController` | The API routes |

`JiraSyncService` never calls gbrain directly. Going through `IngestionService` keeps revision bumps, query-cache invalidation, retries, and attempt history working without new code.

## Data model

Migrations `V16` and `V17` add the following.

### `knowledge_item` additions

Column names are source-agnostic so later integrations can reuse them.

| Column | Type | Purpose |
|---|---|---|
| `external_source` | `varchar(32)` | `JIRA`; null for uploads and manual notes |
| `external_key` | `varchar(64)` | Work item key, for example `KB-42` |
| `external_url` | `varchar(512)` | Browse link to the work item in Jira |
| `external_updated_at` | `timestamptz` | Jira's `updated` value, used to skip unchanged items |

- A unique index on (`external_source`, `external_key`) prevents duplicates on re-sync.
- `KnowledgeSourceKind` gains `JIRA`.

### `jira_sync_run`

| Column | Purpose |
|---|---|
| `id` | Primary key |
| `project_key` | Jira project synced |
| `status` | `RUNNING`, `COMPLETED`, `FAILED` |
| `started_at`, `finished_at` | Timing |
| `created_count`, `updated_count`, `unchanged_count`, `failed_count` | Results |
| `error_code`, `error_message` | Safe failure details; never credentials or document bodies |
| `triggered_by` | User id of the admin who started the run |

A partial unique index allows only one `RUNNING` run per project, even if two requests race.

### `jira_work_item`

One row per synced knowledge item, holding the fields the API filters on: key, summary, project, type, status, priority, assignee, reporter, parent, Jira timestamps, and browse link. Every sync refreshes it, including for items whose page is unchanged. It's removed with its knowledge item.

## Document format

Each work item becomes one knowledge item and one gbrain page:

```markdown
# KB-42: Migrate billing service to new queue

Projects: Billing Platform
Type: Story
Status: In Progress
Priority: High
Assignee: Jane Doe
Reporter: Sam Lee
Labels: migration, q4
Parent: KB-10 (Billing modernization epic)
Updated: 2026-11-04
Link: https://example.atlassian.net/browse/KB-42

## Description

…Markdown from the MCP server…

## Links

- blocks KB-43

## Comments

**Sam Lee, 2026-11-03:** …
```

- The `Projects:` line lets `EntityHints` create PROJECT entities.
- Header lines end with two spaces so they render as separate lines. Newlines in Jira values are collapsed so a value can't add a header field of its own.
- The knowledge item `title` is `KB-42: <summary>`, and `type` is the Jira work item type, or `work-item` when Jira omits it.
- The seed script's `kb-seed` and `kbseed-*` labels are left out of the page.
- All comments are included. Bodies stay under `GbrainDocument.MAX_BODY_BYTES` (1 MB); oldest comments are dropped first, and the page states how many.

## Sync behavior

Syncs are manual, and each project can have only one running at a time.

1. An admin starts a sync. The API creates a `jira_sync_run` and returns `202`.
2. A background thread pages through `project = "<KEY>" ORDER BY updated ASC`.
3. For each work item, `jira_work_item` is refreshed and then:

| Knowledge item state | Action | Counted as |
|---|---|---|
| None exists | Create and ingest | Created |
| Trashed or deactivated | Leave it alone | Unchanged |
| Completed, and Jira's `updated` matches | Skip | Unchanged |
| Otherwise, including a failed last ingestion | Rebuild the page, advance the revision, and reingest | Updated |

4. If the search returned only some comments, the sync fetches the complete list.
5. A problem with one item adds to `failed_count`, and the run continues. A failure reaching Jira ends the run as `FAILED`; running it again skips what's already current.
6. A run still active when the application restarts is marked `FAILED` with code `INTERRUPTED`.

Each ingested item also runs gbrain's entity extraction, so a first sync makes one LLM call per work item.

### Retries

Every tool call the adapter makes is read-only, so repeating one is safe.

- Rate limits and outages are retried up to `JIRA_RETRY_MAX_ATTEMPTS` times, honoring `Retry-After` and otherwise backing off from 500 ms.
- If Jira asks for a wait longer than `JIRA_RETRY_MAX_BACKOFF`, the call fails instead of blocking.
- Authentication failures and timeouts are not retried.

## API

All routes are under `/api/integrations/jira` and require a bearer token. They're in [`openapi.json`](./openapi.json).

| Method | Path | Role | Success |
|---|---|---|---|
| `GET` | `/status` | Admin | `200`, whether Jira is configured and reachable |
| `GET` | `/projects` | Admin | `200`, live Jira project list |
| `POST` | `/projects/{key}/sync` | Admin | `202`, the new sync run; `400` for an invalid key; `409` if already running |
| `GET` | `/sync-runs` | Admin | `200`, the 50 most recent runs |
| `GET` | `/sync-runs/{id}` | Admin | `200`, one run, for polling |
| `GET` | `/issues` | User | `200`, synced work items, with optional exact-match `project`, `type`, `status`, and `assignee` filters |
| `GET` | `/issues/{key}` | User | `200`, one work item and its Markdown page |

`/issues` routes read local rows, not live Jira, and show only indexed, active items. Errors use the shared `{ "code", "message" }` shape. Jira failures return `502` (`JIRA_UNAVAILABLE`) or `503` (`JIRA_NOT_CONFIGURED`), never `401`, so a Jira credential problem doesn't sign a user out.

## Frontend

Implemented:

- **Admin sync panel** on the Knowledge Base page (`JiraSyncPanel.tsx`, `jiraSyncApi.ts`): a project picker, a **Sync Jira** button, and the progress or last result. It's hidden from non-admins and when Jira isn't configured or reachable.
- **`JIRA` source kind** accepted by the knowledge item validator.
- **Jira row** in the Dashboard's Document Types panel.

Not built:

- The Jira View page.
- A Knowledge Base source filter or badge. Jira items are listed with other documents.
- Links from Ask AI citations to Jira. Citations open the stored page.

## Setup

1. An organization admin turns on API token authentication under Atlassian Administration → Connectors → Atlassian MCP server → Authentication.
2. Create a personal API token with scopes at id.atlassian.com, with `search:jira:agent-interface` and `read:jira:agent-interface`.
3. Set the variables below in the root `.env`. The site's cloud id is at `<site>/_edge/tenant_info`.

| Variable | Purpose | Default |
|---|---|---|
| `JIRA_ENABLED` | Turns the integration on | `false` |
| `JIRA_MODE` | `mcp`, or `in-memory` for fixed sample work items | `mcp` |
| `JIRA_MCP_URL` | Atlassian MCP endpoint | `https://mcp.atlassian.com/v2/mcp` |
| `JIRA_SITE_URL` | `https://<site>.atlassian.net`, used to build browse links | none |
| `JIRA_EMAIL` | Atlassian account email for Basic auth | none |
| `JIRA_API_TOKEN` | The scoped API token; never committed or logged | none |
| `JIRA_CLOUD_ID` | Jira site cloud id required by the MCP tools | none |
| `JIRA_RETRY_MAX_ATTEMPTS` | Attempts per call, including the first | `4` |
| `JIRA_RETRY_MAX_BACKOFF` | Longest wait allowed before giving up | `PT30S` |

`JIRA_CONNECT_TIMEOUT`, `JIRA_READ_TIMEOUT`, `JIRA_MAX_RESPONSE_SIZE`, and `JIRA_PAGE_SIZE` are optional for host-run backends and aren't passed through Compose.

### Test data

`synthetic-data/tools/seed_jira.py` creates 22 work items from `synthetic-data/jira/acme-jira-seed.json`, matching the people and projects in the synthetic corpus. The target project must already exist. The script needs an unscoped token with write access to the site's REST API, so it won't run with the read-only token above. Assignees and reporters are the site owner because the Acme people aren't Jira users; their names appear in descriptions and comments.

## Atlassian MCP behavior

Observed against a live Jira Cloud site on 2026-11-09.

**Access**

- Unscoped tokens fail with a missing-scope error, and an admin-console key beginning `ATCTT` was rejected as `invalid_token`.
- `initialize` and `tools/list` succeed even when tool calls are refused, so the connection check calls a real tool.

**Protocol**

- Responses are SSE-framed. Results are a JSON string inside `content[0].text`, with the payload under `data`.
- The server issues an `Mcp-Session-Id` on `initialize` and requires it on later calls; without it the request fails with HTTP 400 (`-32600`).
- Authentication failures are HTTP 401 with `{"error":"invalid_token"}`, not SSE. Permission and scope failures are HTTP 200 with `isError: true` and a `statusCode` inside the text.

**Tools used**

| Need | Call | Notes |
|---|---|---|
| Search | `searchJiraIssuesUsingJql` | Pages with `nextPageToken` until `isLast` is true; no total |
| One work item | `getJiraIssue` | `view: "evidence"` reports only the comment count; the tool embeds at most 20 comments |
| Comments | `listJiraIssueComments` | Pages with `startAt` until `isLast` is true; 100 per page at most |
| Projects | `executeRead` with `name: listJiraProjects` | Pages with `startAt`; returns `values` and `isLast` |

**Content**

- `responseContentFormat: "markdown"` returns descriptions and comments already converted from ADF.
- `searchJiraIssuesUsingJql` returns only a compact field set by default and silently omits `description`, `parent`, `comment`, and `issuelinks`, even when requested. The adapter passes `view: "full"`.
- Scrubbed payloads are in `backend/src/test/resources/jira/mcp/`.

## Verification

`JiraLiveTests` checks the adapter and routes against a real site and is excluded from `check`. From `backend`, with the root `.env` exported and Docker running:

```bash
JIRA_ENABLED=true ./gradlew liveTest --tests '*JiraLiveTests'
```

It requires the seeded `ACME` project.

## Known limitations

- Every signed-in user can read synced work items, regardless of their Jira permissions.
- Answers reflect the last sync, not live Jira state.
- Synced items are read-only in KnowledgeBridge.
- Work items deleted in Jira remain in KnowledgeBridge and gbrain, because a sync can't detect them.
- A work item permanently deleted in KnowledgeBridge returns on the next sync.
- Link-only edits in Jira may not change a work item's `updated` time, so its page can keep old links until another edit.
- gbrain's person extractor can mistake header values such as "In Progress" and "To Do" for people.
- `/issues` isn't paginated.
- Retry behavior has been tested only against a simulated rate limit.
- A stack with synced Jira items gives the corpus evaluation extra facts, so evaluations should run without them.

## Open decisions

| Decision | Options |
|---|---|
| Jira View placement | New nav item, Uploads tab, or Admin |
| Knowledge Base treatment | Filter, badge, or hide Jira items |
| Dashboard card | "Last synced / N items" card, coordinated with the dashboard owner |
| Evaluation | Add Jira-sourced questions to the LLM-as-judge set |

## Future work

- An agentic query layer (for example, a LangGraph service behind `/api`) that uses gbrain and Jira as MCP tools for multi-step reasoning. It would live outside gbrain, which is pinned to an upstream revision.
- Webhook-driven sync once the app has a public URL.
- Confluence or other sources reusing the `external_*` columns.
