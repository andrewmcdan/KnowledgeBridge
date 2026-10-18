# ingestion

Turns an uploaded file or manually entered text into an indexed knowledge item.

Expected contents:

- `IngestionService` — validates text, records `character_count`, sends content to the engine through the `gbrain` adapter, and moves the item through `PENDING → PROCESSING → COMPLETED | FAILED`.
- The sibling `video` package owns safe temporary storage, FFmpeg/Jaffree audio extraction, OpenAI transcription, cleanup, and background-job recovery before delegating the persisted transcript here.
- `IngestionAttempt` entity/repository backing the `ingestion_attempt` table: attempt number, timestamps, error code, and a safe error message for the Uploads queue.
- Retry logic for `POST /api/knowledge-items/{id}/retry-ingestion`.

Status changes here should bump the knowledge revision so the query cache is invalidated.
