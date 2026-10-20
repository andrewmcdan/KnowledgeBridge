# entities

Entity listing and entity-document linking — the `/api/entities` routes and `GET /api/knowledge-items/{id}/entities`.

Implemented components:

- `EntityController` — filtered/limited entity list, single entity, related documents for an entity, top entities for a document.
- `EntityService` — pulls entities from the knowledge engine through the `gbrain` adapter and maps engine ids back to our knowledge items.
- Response records (`EntitySummary`, `DocumentEntities`, `RelatedDocument`).

Automated extraction is noisy; return limited, filterable results and never fail a request because an entity has missing or odd data. Interactive graph visualization is a stretch goal and would build on this package.

Native `extract_entities` uses the dedicated `GBRAIN_EXTRACTION_OAUTH_CLIENT_ID` / `SECRET` credential. Provision it with `scripts/provision-gbrain.ps1 -Extraction` or `scripts/provision-gbrain.sh --extraction`. It has `read write` scope only in the `knowledgebridge` source and no slug-prefix fence; the document credential retains its `knowledgebridge/` fence. Both use the existing MCP transport, token cache, timeout, and error handling.

The adapter detects people and companies with gbrain's native heuristics. Names beginning with `Project ` or ending with ` Team` / ` Department` are classified for browsing. Explicit `project:`, `projects:`, `team:`, and `department:` document fields supplement project/team detections. These hints remain application projections; they do not create engine pages. All detections are presented as unverified.

Extraction follows successful ingestion for notes, Markdown, and video transcripts. A failed extraction leaves the document indexed, records a separate failure code, and can be retried by an admin using `POST /api/knowledge-items/{id}/extract-entities`. Existing completed documents can use that route for backfill. GET requests never trigger extraction or provider calls. Re-extraction atomically replaces document associations. Reads include only completed knowledge items with extraction results for their current revision.

`GET /api/entities` accepts `name`, `type` (`PERSON`, `COMPANY`, `PROJECT`, `TEAM`), and `limit` (1–100, default 25). Details and related-document routes use stable application entity IDs. `GET /api/knowledge-items/{id}/entities` reports extraction status, truncation, a safe error code, and detections (default 10). `GET /api/knowledge-items/{id}/content` exposes only completed document content to authenticated users and omits upload/management metadata.

Native extraction is mutating and is never automatically retried. The engine caps input at 200,000 characters and native candidates at 200; the adapter rejects larger documents with `VALIDATION` and caps the combined projection at 200, reporting truncation. Creating native stubs can incur embedding calls even though name detection itself makes no LLM calls. Metadata hints use a limited field grammar, not a general YAML parser.

The gbrain container explicitly sets `persistence.unbound_write=database_only` for this API-owned PostgreSQL deployment. The pinned engine otherwise refuses writes to the legacy unbound `knowledgebridge` source path with `owner_unavailable`. No canonical checkout is claimed or adopted. This policy belongs to this dedicated project brain and is separate from OAuth source and slug restrictions.
