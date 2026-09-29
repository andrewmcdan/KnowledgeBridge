# query

Search and synthesis orchestration — the `/api/query` routes behind the Ask AI page.

Expected contents:

- `QueryController` — `POST /search` (ranked documents, no LLM cost) and `POST /synthesize` (LLM answer with citations and a low-confidence/gap flag), plus answer feedback and per-user history.
- `QueryService` — checks the exact-match cache first, otherwise calls the `gbrain` adapter, then records a `query_event` with character counts and estimated tokens/cost.
- Response records carrying citations, `cacheHit`, and an `aiGenerated` marker so the UI can label AI output.

Cache storage and invalidation belong in a separate `cache` package; token/cost estimation in `usage`.
