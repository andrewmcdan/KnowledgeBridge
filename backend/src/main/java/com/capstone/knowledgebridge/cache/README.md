# cache

Naive exact-match response cache for Synthesis mode, stored in PostgreSQL (`query_cache` table). Required by the brief to demonstrate bypassing redundant LLM calls, even though the hit rate for free-text questions is low.

Expected contents:

- `QueryCache` entity/repository — `exact_query_text`, `query_mode`, `knowledge_revision`, `response_json`, `hit_count`, timestamps.
- `QueryCacheService` — `lookup(queryText, mode, revision)` and `store(...)`; the cache key includes the current knowledge revision so answers built on stale content are never returned.
- `SystemState` / knowledge-revision accessor (or leave that in `knowledge` and read it from here) — the revision increments on every knowledge-item write.
- Admin listing/clearing behind `GET`/`DELETE /api/admin/cache`.

Serialize `response_json` with the injected `ObjectMapper`, not a fresh one.
