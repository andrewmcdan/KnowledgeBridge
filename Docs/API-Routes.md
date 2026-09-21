# KnowledgeBridge API Routes

Candidate REST routes between the React frontend and the Spring Boot backend. This expands the sketch in section 4.5 of the [execution plan](./KnowledgeBridge%20Capstone%20Project%20Execution%20Plan.md) with routes implied by the frontend scaffold and the project brief. Paths are prefixed with `/api`; the Vite dev server proxies `/api` to port 8080.

Only `GET /api/health` exists today. Everything else is a proposal to be agreed on before implementation.

**Roles:** `Public` = no login, `User` = any signed-in user, `Admin` = admin role only. Authorization is enforced in the backend, not just hidden in the UI.

## System

| Method | Route | Role | Description |
|---|---|---|---|
| GET | `/api/health` | Public | Reports backend and database status. Used by the frontend connection indicator. *(implemented)* |

## Authentication

| Method | Route | Role | Description |
|---|---|---|---|
| POST | `/api/auth/login` | Public | Authenticates with email and password; establishes a session or returns a token. |
| POST | `/api/auth/logout` | User | Ends the current session or invalidates the token. |
| GET | `/api/auth/me` | User | Returns the signed-in user's id, display name, and role so the UI can show/hide admin navigation. |

## Query (Ask AI)

| Method | Route | Role | Description |
|---|---|---|---|
| POST | `/api/query/search` | User | Search mode. Returns a ranked list of matching knowledge items with snippets; no LLM call. |
| POST | `/api/query/synthesize` | User | Synthesis mode. Returns an LLM-generated answer with citations, a confidence/gap flag, and whether it was a cache hit. Checks the exact-match cache first. |
| POST | `/api/query/{queryEventId}/feedback` | User | Records a helpful / not-helpful vote on a synthesized answer for the reliability evaluation. |
| GET | `/api/query/history` | User | Returns the caller's recent queries so they can be re-run from the Dashboard. |

## Knowledge items (Knowledge Base, Uploads)

| Method | Route | Role | Description |
|---|---|---|---|
| GET | `/api/knowledge-items` | User | Lists knowledge items with title, type, owner, date, and status. Supports search, status, and type filters plus paging; admins can include soft-deleted items. |
| GET | `/api/knowledge-items/{id}` | User | Returns one item's metadata and full text for the document detail view. |
| POST | `/api/knowledge-items/manual` | Admin | Creates an item from manually entered title and text, then queues ingestion. |
| POST | `/api/knowledge-items/upload` | Admin | Multipart upload of a single Markdown file; validates, stores, and queues ingestion. |
| PUT | `/api/knowledge-items/{id}` | Admin | Updates title or content; re-ingests and bumps the knowledge revision to invalidate cache. |
| DELETE | `/api/knowledge-items/{id}` | Admin | Soft-deletes the item so it is hidden from search but recoverable. |
| POST | `/api/knowledge-items/{id}/restore` | Admin | Restores a soft-deleted item. |
| DELETE | `/api/knowledge-items/{id}/permanent` | Admin | Permanently deletes the item and its embeddings in the knowledge engine. |
| POST | `/api/knowledge-items/{id}/retry-ingestion` | Admin | Re-queues ingestion for an item in `FAILED` status. |
| GET | `/api/knowledge-items/{id}/ingestion-attempts` | Admin | Lists ingestion attempts with status, timestamps, and safe error messages for the Uploads queue. |
| GET | `/api/knowledge-items/{id}/entities` | User | Returns the top entities detected in this document (entity–document linking). |

## Entities

| Method | Route | Role | Description |
|---|---|---|---|
| GET | `/api/entities` | User | Returns a filtered or limited list of detected entities (people, projects, teams) from the knowledge engine. Supports a name filter and limit. |
| GET | `/api/entities/{id}` | User | Returns one entity's name, type, and summary. |
| GET | `/api/entities/{id}/documents` | User | Lists knowledge items related to the entity. |

## Bookmarks

| Method | Route | Role | Description |
|---|---|---|---|
| GET | `/api/bookmarks` | User | Lists the caller's saved knowledge items. |
| PUT | `/api/bookmarks/{knowledgeItemId}` | User | Saves a knowledge item for the caller. Idempotent. |
| DELETE | `/api/bookmarks/{knowledgeItemId}` | User | Removes a saved knowledge item. |

## Usage and cost (Usage, Dashboard)

| Method | Route | Role | Description |
|---|---|---|---|
| GET | `/api/admin/usage/summary` | Admin | Totals for query count, ingestion count, cache hit rate, estimated tokens, and estimated cost; optional date range. Feeds the sidebar metrics. |
| GET | `/api/admin/usage/events` | Admin | Paged list of query and ingestion events with per-event token and cost estimates. |
| GET | `/api/admin/usage/by-operation` | Admin | Estimated cost broken down by operation type (embedding vs. synthesis). *(stretch)* |
| GET | `/api/admin/knowledge-summary` | Admin | Counts of knowledge items by status and type, plus the current knowledge revision. |

## Administration

| Method | Route | Role | Description |
|---|---|---|---|
| GET | `/api/admin/users` | Admin | Lists user accounts with role and enabled flag. |
| POST | `/api/admin/users` | Admin | Creates a user account with a role. |
| PATCH | `/api/admin/users/{id}` | Admin | Changes a user's role or enabled flag. |
| GET | `/api/admin/cache` | Admin | Lists cache entries with hit counts and knowledge revision for demoing the cache. |
| DELETE | `/api/admin/cache` | Admin | Clears the exact-match response cache. |
| GET | `/api/admin/engine/status` | Admin | Reports gbrain adapter reachability and configuration so admins can diagnose ingestion failures. |

## Notes

- The frontend never calls gbrain directly; all knowledge-engine traffic goes through the backend adapter behind these routes.
- Query responses should carry an `aiGenerated` marker and citations so the UI can label AI output and never present it as authoritative.
- Any write to knowledge items should increment `knowledge_revision`, which is part of the cache key.
