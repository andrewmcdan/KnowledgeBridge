# Upload naming

`TitleGenerationService` names only uploads that explicitly request AI naming. `IngestionService` invokes it before indexing, after videos have a persisted transcript. Pending requests retain the supplied title as guidance. A successful title is saved and clears the request flag before contacting gbrain; indexing retries therefore do not repeat the AI call.

The client shares `OPENAI_API_KEY` with transcription. `OPENAI_TITLE_BASE_URL`, `OPENAI_TITLE_MODEL`, and `OPENAI_TITLE_TIMEOUT` configure the endpoint, model, and timeout. Defaults use OpenAI Chat Completions and `gpt-4o-mini`, with [structured JSON output](https://developers.openai.com/api/docs/guides/structured-outputs). No new SDK is required. All calls stay behind Spring `/api`; gbrain communication remains in the gbrain adapter.

Requests include at most 24,000 content characters and the title guidance. Long files contribute beginning, middle, and ending excerpts. Documents and guidance are untrusted input; there are no tools or retrieval calls. The response must finish successfully and contain a nonblank, single-line title within 255 characters. Provider errors and malformed/refused responses become safe, retryable `AI_TITLE_FAILED` ingestion failures. Calls are never retried automatically and are excluded from query-only usage totals.
