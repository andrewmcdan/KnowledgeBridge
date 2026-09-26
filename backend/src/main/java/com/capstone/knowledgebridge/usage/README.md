# usage

Estimated LLM usage and cost tracking. gbrain may not report exact token counts, so estimates are derived from character counts.

Expected contents:

- `QueryEvent` entity/repository for the `query_event` table — user, query text, mode, `cache_hit`, input/output character counts, estimated tokens, estimated cost, success flag.
- `UsageService` — records an event per query and per ingestion; exposes totals, cache hit rate, and per-operation breakdown (embedding vs. synthesis) for the admin dashboard.
- `TokenEstimator` — characters → tokens → cost using the documented provider pricing assumptions.

Both `query` and `ingestion` call into this package; it never calls back into them.
