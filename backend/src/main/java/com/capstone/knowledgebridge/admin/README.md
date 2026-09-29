# admin

Administrative summaries and management operations — the `/api/admin/**` routes, which `SecurityConfiguration` restricts to `ROLE_ADMIN`.

Expected contents:

- `AdminController` — usage summary and events (delegates to `usage`), knowledge summary by status/type (delegates to `knowledge`), cache listing/clear (delegates to `cache`), user management (delegates to `users`), and a gbrain reachability check (delegates to `gbrain`).
- Response records such as `UsageSummaryResponse`, `KnowledgeSummaryResponse`.

This package is a thin aggregation layer: it composes other services and holds no business logic of its own.
