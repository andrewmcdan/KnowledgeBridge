# query

Search and synthesis orchestration — the `/api/query` routes behind the Ask AI page.

Implemented:

- `QueryController` — authenticated `POST /search` for ranked documents and `POST /synthesize` for an LLM answer with citations, gap signals, warnings, and usage.
- `QueryService` — validates through the application-owned gbrain request models, invokes the adapter, and resolves synthesis citations to local knowledge-item metadata when available.
- Response records carry an `aiGenerated` marker and `extractiveFallback` flag so the UI can distinguish an LLM answer from excerpts.
- Synthesis goes through the exact-match cache in `cache`; `cacheHit` reports whether the answer was reused.

Deferred:

- Query-event history, feedback, and durable cost accounting belong in `usage`.
