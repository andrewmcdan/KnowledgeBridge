# cache

Naive exact-match response cache for Synthesis mode, stored in PostgreSQL (`query_cache` table, V7). Required by the brief to demonstrate bypassing redundant LLM calls, even though the hit rate for free-text questions is low.

Implemented:

- `QueryCacheService` — `lookup(queryText, mode, revision, responseType)` and `store(queryText, mode, revision, response)`. The key is the trimmed question text, the `QueryMode`, and the knowledge revision, so answers built on stale content are never returned. A hit increments `hit_count` and updates `last_accessed_at`.
- Responses are serialized with the injected `JsonMapper`. The cache is best-effort: a failed or unreadable lookup is a miss, and a failed store is only logged.
- Plain SQL through `JdbcTemplate` instead of a JPA entity: lookups must repeat the unique index's `md5(exact_query_text)` expression to use it (the hash keeps long questions under PostgreSQL's B-tree entry size limit), and inserts name the same expression in `ON CONFLICT`.
- The revision comes from `KnowledgeRevisionService` in `knowledge`, which `IngestionService` bumps on every successful write.
- `QueryService.synthesize()` checks the cache first. A hit returns the stored answer with `cacheHit: true` and zero tokens and cost (the model name is kept because the frontend requires it). A miss asks gbrain and stores the answer only when it is `aiGenerated`.

What is never cached:

- gbrain errors: the adapter throws before anything is stored.
- Extractive fallbacks (`aiGenerated: false`): gbrain could not compose an answer, so the next ask tries again.

Known limitation:

- gbrain can report a normal answer (`synthesis_status: ok`) together with warnings that mean something went wrong along the way, such as `QUESTION_EMBED_FAILED` (keyword-only retrieval) or `SALVAGED_ANSWER_FROM_MALFORMED_JSON`. Those answers are `aiGenerated` and are cached, so a degraded answer can be served until the knowledge revision changes or an admin clears the cache. Filtering on `warnings` was left out on purpose while gbrain's warning codes are still changing; revisit it once they settle.

Not built yet:

- Admin listing/clearing behind `GET`/`DELETE /api/admin/cache`.
