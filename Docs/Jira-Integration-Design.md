# Jira integration design

**Owner:** Adrian Edwards
**Last updated:** 2026-11-05

## Overview

KnowledgeBridge syncs Jira work items into gbrain as knowledge pages so Ask AI can search, synthesize, and cite them like any other document. A Jira View lets users browse synced work items and their details inside the app.

Two backend packages carry the work:

- `jira` talks to Jira.
- `jiraintegration` syncs work items into the knowledge base and serves the Jira View.

## Goals

- Ask AI answers questions about Jira work items with citations (for example, "per KB-42…").
- Admins choose which Jira projects to sync and trigger a sync from the app.
- Users browse synced work items without leaving the app.
- All Jira communication stays behind one backend adapter, matching the `gbrain` boundary.

## Non-goals

- Editing, creating, or transitioning Jira work items. Jira stays the source of truth.
- Mirroring Jira permissions per user.
- Real-time sync through webhooks.
- Agentic, multi-step Jira reasoning at query time (see [Future work](#future-work)).

## Approach

gbrain's `synthesize` tool accepts only `question`, `since`, and `until`. It gathers evidence from pages it already stores and cites only those pages, so Jira content must exist as gbrain pages before Ask AI can use it.

KnowledgeBridge therefore copies each work item into gbrain as a Markdown page through the existing ingestion pipeline. Synced rows also back the Jira View, so users browse exactly what the AI knows.

Jira is reached through Atlassian's Rovo MCP server (`https://mcp.atlassian.com/v2/mcp`) using HTTP Basic auth with an account email and a personal scoped API token. See [Stage 0 findings](#stage-0-findings) for its observed behavior.

## Architecture

```text
Frontend (Jira View, admin sync controls)
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
| `JiraClient` | Interface: search work items, get one work item, list projects, check connection |
| `McpJiraClient` | Real implementation over MCP; reuses the hand-built MCP transport pattern from `gbrain` (JSON-RPC, SSE framing, three error layers) instead of adding the MCP Java SDK |
| `InMemoryJiraClient` | Test and offline implementation, matching `InMemoryGbrainClient` |
| `JiraIssue`, `JiraProject`, `JiraComment` | Jira records returned by the client |
| `JiraProperties` | Connection settings |
| `JiraException`, `JiraErrorCode` | Typed failures, matching `GbrainException` |

### `jiraintegration` package

| Class | Responsibility |
|---|---|
| `JiraSyncService` | Pulls work items through `JiraClient`, creates or updates `KnowledgeItem` rows, and hands them to `IngestionService` |
| `JiraMarkdownConverter` | Builds the document from the Markdown returned by the MCP server |
| `JiraSyncRun`, `JiraSyncRunRepository` | Sync history and progress |
| `JiraIntegrationController` | Sync routes and Jira View routes |

`JiraSyncService` never calls gbrain directly. Going through `IngestionService` keeps revision bumps, query-cache invalidation, retries, and attempt history working without new code.

## Data model

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
- Jira items are read-only in KnowledgeBridge; manual-note editing already rejects non-manual items. Ingestion retry still applies.

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
- Named assignees and reporters give gbrain's person extractor material for PERSON entities.
- The knowledge item `title` is `KB-42: <summary>`, and `type` is the Jira work item type, slugified as usual (`story`, `bug`).
- All comments are included. Bodies stay under `GbrainDocument.MAX_BODY_BYTES` (1 MB); oldest comments are dropped first when a body would exceed it.

## Sync behavior

Syncs are manual; there is no scheduled sync.

1. An admin starts a sync for one project. The API creates a `jira_sync_run` and returns `202` with its id.
2. A background worker pages through `project = <KEY>`, ordered by `updated`.
3. For each work item:
   - No existing row: create a `KnowledgeItem` and ingest it.
   - Existing row with an unchanged `external_updated_at`: count it as unchanged and skip it.
   - Existing row with a newer `updated`: rebuild the body, bump the content revision, and reingest.
4. A failed item increments `failed_count`; the run continues.
5. The run ends `COMPLETED`, or `FAILED` if Jira becomes unreachable.

Only one run per project may be active at a time; a second request returns `409`.

## API

All routes are under `/api/integrations/jira` and require a bearer token.

| Method | Path | Role | Success |
|---|---|---|---|
| `GET` | `/status` | Admin | `200`, whether Jira is configured and reachable |
| `GET` | `/projects` | Admin | `200`, live Jira project list |
| `POST` | `/projects/{key}/sync` | Admin | `202`, the new sync run |
| `GET` | `/sync-runs` | Admin | `200`, sync history, newest first |
| `GET` | `/sync-runs/{id}` | Admin | `200`, one run, for polling |
| `GET` | `/issues` | User | `200`, synced work items, filterable by project, type, status, and assignee |
| `GET` | `/issues/{key}` | User | `200`, one synced work item |

Errors use the shared `{ "code", "message" }` shape. Jira failures map to `502` (`JIRA_UNAVAILABLE`) or `503` (`JIRA_NOT_CONFIGURED`).

## Frontend

- **Jira View:** a page listing synced work items with filters and a details panel with an "Open in Jira" link.
- **Admin sync controls:** connection status, a project picker, a "Sync now" button, and sync history.
- **Knowledge Base:** a source filter or badge so Jira items don't bury uploaded documents.
- **Ask AI:** citations to Jira items show the work item key and link to Jira.

## Configuration

Added to `.env` and `.env.example` with comments:

| Variable | Purpose |
|---|---|
| `JIRA_ENABLED` | Turns the integration on; defaults to `false` |
| `JIRA_MODE` | `mcp` or `in-memory`, matching `GBRAIN_MODE` |
| `JIRA_MCP_URL` | Atlassian MCP endpoint |
| `JIRA_EMAIL` | Atlassian account email for Basic auth |
| `JIRA_API_TOKEN` | Atlassian API token; never committed or logged |
| `JIRA_CLOUD_ID` | Jira site cloud id required by the MCP tools |
| `JIRA_SITE_URL` | `https://<site>.atlassian.net`, used to build browse links |

## Implementation stages

Each stage ends in a reviewable pull request and leaves the app working.

### Stage 0: Spike

- Create a free Jira Cloud site and an API token, and find the site's cloud id.
- Call `tools/list` on the Atlassian MCP server and record the tools and parameters.
- Confirm how `searchJiraIssuesUsingJql` paginates.
- Capture real work item responses, including ADF descriptions and comments, as test fixtures.
- Seed the site with work items that line up with the `synthetic-data` corpus.

**Done when:** the tool list, pagination behavior, and fixtures are recorded in this document.

#### Stage 0 findings

Verified against a live Jira Cloud site on 2026-11-09.

**Access**

- An organization admin must turn on API token authentication under Atlassian Administration → Connectors → Atlassian MCP server → Authentication.
- The token must be a personal API token with scopes, created at id.atlassian.com. Unscoped tokens fail with a missing-scope error, and a key beginning `ATCTT` was rejected as `invalid_token`.
- Required scopes: `search:jira:agent-interface` for search, and `read:jira:agent-interface` for work items, comments, and projects.
- `initialize` and `tools/list` succeed even when tool calls are refused, so a connection check must call a real tool.

**Protocol**

- Responses are SSE-framed. Results are a JSON string inside `content[0].text`, with the payload under `data`.
- The server issues an `Mcp-Session-Id` on `initialize` and requires it on later calls; without it the request fails with HTTP 400 (`-32600`). The gbrain transport does not send this header, so it must be added for Jira.
- Authentication failures are HTTP 401 with `{"error":"invalid_token"}`, not SSE. Permission and scope failures are HTTP 200 with `isError: true` and a `statusCode` inside the text.

**Tools used**

| Need | Call | Notes |
|---|---|---|
| Search | `searchJiraIssuesUsingJql` | Pages with `nextPageToken` until `isLast` is true; no total |
| One work item | `getJiraIssue` | `view: "evidence"` reports only the comment count; the tool embeds at most 20 comments |
| Comments | `listJiraIssueComments` | Pages with `startAt` until `isLast` is true; 100 per page at most |
| Projects | `executeRead` with `name: listJiraProjects` | Returns `values`, `isLast`, `total`, and `startAt` |

**Content**

- `responseContentFormat: "markdown"` returns descriptions and comments already converted from ADF.
- `searchJiraIssuesUsingJql` returns only a compact field set by default and silently omits `description`, `parent`, `comment`, and `issuelinks`, even when requested. `view: "full"` returns all of them.
- `view: "evidence"` returns descriptions and parents, but no comments or links in search.
- Raw scrubbed payloads are in `backend/src/test/resources/jira/mcp/`. The `rest/` directory holds raw ADF responses for reference.

**Seed data**

`synthetic-data/tools/seed_jira.py` needs an unscoped token with write access to the site's REST API, so it won't run with the read-only token above. The `ACME` project holds 22 seeded work items. Assignees and reporters are the site owner because the Acme people aren't Jira users; their names appear in descriptions and comments.

### Stage 1: `jira` adapter

- `JiraClient`, `InMemoryJiraClient`, `McpJiraClient`, `JiraProperties`, and typed errors.
- Configuration variables in `.env.example` and `application.properties`.
- `GET /status` and `GET /projects`.

**Done when:** both routes work against the real site and against `JIRA_MODE=in-memory`, with tests.

### Stage 2: Data model

- Migration adding the `external_*` columns, the unique index, and `jira_sync_run`.
- `JIRA` in `KnowledgeSourceKind`; entity fields on `KnowledgeItem`.

**Done when:** the migration applies cleanly to an existing database and existing tests pass.

### Stage 3: Converter

- `JiraMarkdownConverter`: builds the document from the Markdown the MCP server returns, adding the header block. No ADF parsing is needed.
- Size limiting by dropping the oldest comments.

**Done when:** unit tests pass against the Stage 0 fixtures.

### Stage 4: Sync

- `JiraSyncService`, the background worker, and sync run tracking.
- `POST /projects/{key}/sync`, `GET /sync-runs`, and `GET /sync-runs/{id}`.

**Done when:** syncing a seeded project creates completed knowledge items, a second sync reports them unchanged, and Ask AI cites a Jira item.

### Stage 5: Jira View API

- `GET /issues` with filters and `GET /issues/{key}`, read from synced rows.
- OpenAPI, `API-Routes.md`, and `Integration-and-authentication.md` updates.

**Done when:** both routes return synced data with tests and documentation.

### Stage 6: Frontend

- Admin sync controls.
- Jira View page and navigation entry.
- Knowledge Base source filter or badge, and Jira links on Ask AI citations.

**Done when:** each flow is verified in a browser against a synced project.

### Stage 7: Evaluation

- Add Jira-sourced questions to the LLM-as-judge set with the evaluation team.

**Done when:** Jira questions run in the evaluation suite.

## Open decisions

| Decision | Options | Needed by |
|---|---|---|
| Knowledge Base treatment | Filter, badge, or hide Jira items | Stage 6 |
| Navigation placement | New nav item, Uploads tab, or Admin | Stage 6 |
| Dashboard card | "Last synced / N items" card, coordinated with the dashboard owner | Stage 6 |

## Known limitations

- Every signed-in user can read synced work items, regardless of their Jira permissions.
- Answers reflect the last sync, not live Jira state.
- Synced items are read-only in KnowledgeBridge.
- Work items deleted in Jira remain in KnowledgeBridge and gbrain, because a sync can't detect them.

## Future work

- An agentic query layer (for example, a LangGraph service behind `/api`) that uses gbrain and Jira as MCP tools for multi-step reasoning. It lives outside gbrain, which is pinned to an upstream revision.
- Webhook-driven sync once the app has a public URL.
- Confluence or other sources reusing the `external_*` columns.
