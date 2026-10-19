# admin

Administrative summaries and management operations — the `/api/admin/**` routes, which `SecurityConfiguration` restricts to `ROLE_ADMIN`.

Implemented:

- `AdminController` — `GET /api/admin/cache` lists exact-match cache entries (hit counts, revision, and whether each is current) and `DELETE /api/admin/cache` clears them, delegating to `cache`. The class is also `@PreAuthorize("hasRole('ADMIN')")` so it stays admin-only even if the URL rule changes.

Expected contents:

- `AdminController` — usage summary and events (delegates to `usage`), knowledge summary by status/type (delegates to `knowledge`), user management (delegates to `users`), and a gbrain reachability check (delegates to `gbrain`).
- Response records such as `UsageSummaryResponse`, `KnowledgeSummaryResponse`.

This package is a thin aggregation layer: it composes other services and holds no business logic of its own.
