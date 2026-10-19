# admin

Administrative summaries and management operations — the `/api/admin/**` routes, which `SecurityConfiguration` restricts to `ROLE_ADMIN`.

## Implemented

- `AdminController` — `GET /api/admin/cache` lists exact-match cache entries (hit counts, revision, and whether each is current) and `DELETE /api/admin/cache` clears them, delegating to `cache`. The class is also `@PreAuthorize("hasRole('ADMIN')")` so it stays admin-only even if the URL rule changes.

## Endpoints

All routes need `Authorization: Bearer <token>` from `POST /api/auth/login` and are **admin only**: no token gets `401`, a non-admin token gets `403`.

| Method | Path | Body | Success |
| --- | --- | --- | --- |
| `GET` | `/api/admin/cache` | — | `200`, array of cache entries, most recently used first |
| `DELETE` | `/api/admin/cache` | — | `200`, `{ "deleted": 3 }` |

### Cache entry response

```json
{
    "id": 9,
    "queryText": "What is an employee's first week like?",
    "mode": "SYNTHESIS",
    "knowledgeRevision": 8,
    "hitCount": 2,
    "createdAt": "2026-10-19T16:03:41.096Z",
    "lastAccessedAt": "2026-10-19T16:03:56.512Z",
    "current": true
}
```

- `hitCount` is how many times this entry answered a question without an LLM call. It is `0` until the question is asked again.
- `current` is `false` once a knowledge write has moved the revision past this entry. Stale entries can never be returned again and stay listed until the cache is cleared.
- The stored answer itself is not included.
- An empty cache returns `[]`.

### Frontend notes

- A cache screen could show the table above with a "Clear cache" button that calls `DELETE` and then refreshes the list.
- `POST /api/query/synthesize` (see `query`) now returns `cacheHit`. When it is `true`, the answer came from this cache and `usage` reports `0` tokens and `0` cost, so the Ask AI page can show a "Cached" badge.

## Planned

- `AdminController` — usage summary and events (delegates to `usage`), knowledge summary by status/type (delegates to `knowledge`), user management (delegates to `users`), and a gbrain reachability check (delegates to `gbrain`).
- Response records such as `UsageSummaryResponse`, `KnowledgeSummaryResponse`.

This package is a thin aggregation layer: it composes other services and holds no business logic of its own.
