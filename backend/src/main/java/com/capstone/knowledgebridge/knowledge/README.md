# knowledge

Knowledge item CRUD and metadata — the `/api/knowledge-items` routes. Ingestion mechanics live in `ingestion`; this package only orchestrates them.

## Contents

- `KnowledgeItemController` — the routes below and their request/response records.
- `KnowledgeItemService` — creates items, hands them to `IngestionService`, and looks up each item's latest ingestion attempt.
- `KnowledgeItem` entity/repository for the `knowledge_item` table.

## Endpoints

All routes need `Authorization: Bearer <token>` from `POST /api/auth/login`. Management routes are **admin only**: no token gets `401`, a non-admin token gets `403`. Signed-in users can list completed items and read their content; administrators see all item statuses in the list.

| Method | Path | Body | Success |
| --- | --- | --- | --- |
| `GET` | `/api/knowledge-items` | — | `200`, array of items, newest first |
| `GET` | `/api/knowledge-items/{id}` | — | `200`, one item |
| `GET` | `/api/knowledge-items/{id}/manual` | — | `200`, manual note text and revision (admin only) |
| `PUT` | `/api/knowledge-items/{id}/manual` | JSON `{ "title", "type", "text", "revision" }` | `200`, saved and reindexed manual note (admin only) |
| `POST` | `/api/knowledge-items/manual` | JSON `{ "title", "type", "text" }` | `201`, the new item |
| `POST` | `/api/knowledge-items/upload` | multipart: `file` (`.md`, `.docx`, or `.pdf`), `title`, `type` | `201`, the new item |
| `POST` | `/api/knowledge-items/{id}/retry-ingestion` | — | `200`, the retried item |
| `POST` | `/api/knowledge-items/video` | multipart `file`, `title`, `type` | `202`, the queued video item |

Ingestion is synchronous: a create or retry returns after gbrain answers, so the returned `status` is already `COMPLETED` or `FAILED`. A `201` means the item was saved, not that ingestion succeeded — check `status`.

A `.docx` or `.pdf` upload is converted to Markdown by the `extraction` package before it is saved, then ingested exactly like a Markdown upload. Its item has `sourceKind` `DOCX` or `PDF` and keeps `originalFilename` and `sourceSha256`; the file itself is not stored. PDF conversion is best effort; see the `extraction` README.

Video ingestion is asynchronous and returns `202 PENDING`. The bounded worker extracts segmented audio with FFmpeg,
transcribes each segment through OpenAI, persists the joined transcript and metadata, deletes the source and derived
audio, and then sends the transcript through the same gbrain ingestion service. Interrupted `PENDING` or `PROCESSING`
video items are recovered at application startup.

### Item response

```json
{
    "id": 3,
    "title": "Expense policy",
    "type": "policy",
    "ownerId": 1,
    "status": "FAILED",
    "characterCount": 31,
    "externalEngineId": null,
    "lastErrorCode": "TIMEOUT",
    "lastErrorMessage": "gbrain did not respond in time",
    "createdAt": "2026-10-06T22:44:06.233Z",
    "updatedAt": "2026-10-06T22:44:06.445Z"
}
```

- `status` is one of `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`.
- `lastErrorCode` / `lastErrorMessage` describe the latest ingestion attempt. They are `null` unless that attempt failed, and a successful retry clears them. Codes come from the gbrain adapter (`GbrainErrorCode`, e.g. `TIMEOUT`, `VALIDATION`).
- `type` is free text. It is slugified only when sent to gbrain (`"Finance Policy"` → `finance-policy`).

### Error statuses

| Status | When |
| --- | --- |
| `400` | Blank `title`/`type`/`text`; `title` or `type` over 255 characters; empty upload; a `.docx` or `.pdf` filename over 255 characters; a file that can't be ingested, with a specific `code` and `message` (`FILE_UNREADABLE`, `FILE_PASSWORD_PROTECTED`, `FILE_HAS_NO_TEXT`, `PDF_NO_SELECTABLE_TEXT`, `PDF_MOSTLY_SCANNED`; see the `extraction` README) |
| `404` | `GET` or retry on an id that doesn't exist |
| `409` | Retry on an item that isn't `FAILED` |
| `413` | Body over 1 MB (gbrain's limit, `GbrainDocument.MAX_BODY_BYTES`), including a `.docx` or `.pdf` whose converted Markdown is over it; a raw `.docx` or `.pdf` over 10 MB — nothing is saved |
| `415` | Upload whose filename ends in none of `.md`, `.docx`, `.pdf` |

## Manual note edits

`ManualNoteEditor` atomically claims only completed/failed manual notes at the supplied revision. It advances the content revision and global cache revision before network I/O, then the controller reuses the ingestion pipeline. Stale edits, uploaded documents, and active ingestion return 409. A failed reindex keeps the new text and attempt history for retry. V11 adds `content_updated_at`, which changes on edits but stays stable across retries for engine digest consistency.

## Open concerns

- **gbrain timeouts.** A `TIMEOUT` marks the item `FAILED`, but the write may have landed in gbrain. Retrying is safe (gbrain upserts), but reconciling with `GbrainClient.documentState()` before marking `FAILED` would keep the status accurate.
- **Video polling.** Video clients should poll `GET /{id}` while status is `PENDING` or `PROCESSING`.
- **No pagination.** `GET /api/knowledge-items` returns every item; fine for the 50-document corpus.
- **Not built yet:** update, soft-delete, restore, and permanent delete.
- **Live gbrain.** Verified against `GBRAIN_MODE=in-memory`; not yet run end to end against a real gbrain.
