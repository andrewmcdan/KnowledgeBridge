# knowledge

Knowledge item CRUD and metadata — the `/api/knowledge-items` routes. Ingestion mechanics live in `ingestion`; this package only orchestrates them.

## Contents

- `KnowledgeItemController` — the routes below and their request/response records.
- `KnowledgeItemService` — creates items, hands them to `IngestionService`, and looks up each item's latest ingestion attempt.
- `KnowledgeItem` entity/repository for the `knowledge_item` table.

## Endpoints

All routes need `Authorization: Bearer <token>` from `POST /api/auth/login`. Every route below is currently **admin only**: no token gets `401`, a non-admin token gets `403`.

| Method | Path | Body | Success |
| --- | --- | --- | --- |
| `GET` | `/api/knowledge-items` | — | `200`, array of items, newest first |
| `GET` | `/api/knowledge-items/{id}` | — | `200`, one item |
| `POST` | `/api/knowledge-items/manual` | JSON `{ "title", "type", "text" }` | `201`, the new item |
| `POST` | `/api/knowledge-items/upload` | multipart: `file` (`.md`), `title`, `type` | `201`, the new item |
| `POST` | `/api/knowledge-items/{id}/retry-ingestion` | — | `200`, the retried item |

Ingestion is synchronous: a create or retry returns after gbrain answers, so the returned `status` is already `COMPLETED` or `FAILED`. A `201` means the item was saved, not that ingestion succeeded — check `status`.

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
| `400` | Blank `title`/`type`/`text`; `title` or `type` over 255 characters; empty or unreadable upload |
| `404` | `GET` or retry on an id that doesn't exist |
| `409` | Retry on an item that isn't `FAILED` |
| `413` | Body over 1 MB (gbrain's limit, `GbrainDocument.MAX_BODY_BYTES`) — nothing is saved |
| `415` | Upload whose filename doesn't end in `.md` |

## Open concerns

- **Error bodies.** `400`/`413`/`415` responses have no body, and running locally with devtools a validation `400` includes a stack trace. These should switch to the team's shared API error format once it's agreed.
- **List/get permissions.** The original plan gave all users list/get. They're admin only for now because they expose ingestion errors. Decide before the frontend relies on either behaviour.
- **gbrain timeouts.** A `TIMEOUT` marks the item `FAILED`, but the write may have landed in gbrain. Retrying is safe (gbrain upserts), but reconciling with `GbrainClient.documentState()` before marking `FAILED` would keep the status accurate.
- **No polling target yet.** Because ingestion is synchronous, the frontend never sees `PENDING`/`PROCESSING`. Polling `GET /{id}` only becomes useful if ingestion moves to the background.
- **No pagination.** `GET /api/knowledge-items` returns every item; fine for the 50-document corpus.
- **Not built yet:** update, soft-delete, restore, and permanent delete.
- **Live gbrain.** Verified against `GBRAIN_MODE=in-memory`; not yet run end to end against a real gbrain.
