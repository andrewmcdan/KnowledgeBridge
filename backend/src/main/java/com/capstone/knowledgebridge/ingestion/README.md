# ingestion

Turns an uploaded file or manually entered text into an indexed knowledge item.

Expected contents:

- `IngestionService` — validates input (single Markdown/text format for MVP), extracts text, records `character_count`, sends content to the engine through the `gbrain` adapter, and moves the item through `PENDING → PROCESSING → COMPLETED | FAILED`.
- `IngestionAttempt` entity/repository backing the `ingestion_attempt` table: attempt number, timestamps, error code, and a safe error message for the Uploads queue.
- Retry logic for `POST /api/knowledge-items/{id}/retry-ingestion`.

Status changes here should bump the knowledge revision so the query cache is invalidated.
