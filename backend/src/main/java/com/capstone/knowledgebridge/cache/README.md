# cache

Naive exact-match response cache for Synthesis mode, stored in PostgreSQL (`query_cache` table, V7). Required by the brief to demonstrate bypassing redundant LLM calls, even though the hit rate for free-text questions is low.

Implemented:

- `QueryCacheService` — `lookup(queryText, mode, revision, responseType)` and `store(queryText, mode, revision, response)`. The key is the trimmed question text, the `QueryMode`, and the knowledge revision, so answers built on stale content are never returned. A hit increments `hit_count` and updates `last_accessed_at`.
- Responses are serialized with the injected `JsonMapper`. The cache is best-effort: a failed or unreadable lookup is a miss, and a failed store is only logged.
- Plain SQL through `JdbcTemplate` instead of a JPA entity: lookups must repeat the unique index's `md5(exact_query_text)` expression to use it (the hash keeps long questions under PostgreSQL's B-tree entry size limit), and inserts name the same expression in `ON CONFLICT`.
- The revision comes from `KnowledgeRevisionService` in `knowledge`, which `IngestionService` bumps on every successful write.

Not built yet:

- Wiring into `QueryService.synthesize()` and a `cacheHit` flag on the response.
- Admin listing/clearing behind `GET`/`DELETE /api/admin/cache`.
