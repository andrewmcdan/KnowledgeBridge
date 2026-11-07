# Knowledge lifecycle

Administrators manage documents on **Knowledge Base** using the Active, Inactive, Trash and All states filters. Standard users see only active, completed documents and have no management controls. Every lifecycle write is also protected by backend `ADMIN` authorization; hiding buttons is not the access control.

| Action | Effect |
| --- | --- |
| Deactivate | Retain the document and metadata; exclude its engine page, search results, answers and graph relationships. |
| Activate | Reindex completed content from the retained application body. Failed content still needs an ingestion retry. |
| Move to Trash | Retain content, upload and metadata for restoration; exclude it from retrieval and remember its previous active/inactive state. |
| Restore | Return to the previous state. Restoring an active, completed document reindexes from the retained body even after the engine's recovery window expires. |
| Permanently delete | Available only in Trash. Remove the application item, ingestion attempts, entity extraction/membership metadata, orphaned associated entities and retained source file. Shared entity records remain. Clear cached answers, which may contain document text. |

Permanent deletion requires a confirmation dialog and typing `DELETE`. As selected for this deployment, the engine page is excluded immediately and physical engine cleanup follows gbrain's retention/autopilot process. This endpoint does not claim immediate physical erasure from the engine or backups. Operators must run/configure that retention process; the engine's local purge CLI remains the route for immediate physical removal. Application deletion cannot be undone through Restore.

Each successful mutation advances the global knowledge revision, invalidating prior cached answers. Search excludes unavailable application items; synthesis rejects stale/unknown citations rather than returning answers based on inactive, trashed or permanently removed documents. Normal users cannot read their document content or entity metadata directly either. Administrators retain read-only preview access while content is inactive or in Trash.

Lifecycle changes take a per-item database write lock while the bounded engine request runs. This deliberately serializes these infrequent administrative operations with edits and retries. Pending/processing ingestion returns 409; the UI disables management buttons until ingestion finishes. Edits and retries require active state, preventing stale requests from reviving excluded content. Engine failures return 503 without reporting a completed state transition; database work rolls back. Engine writes and file removal cannot be rolled back across stores, so an uncertain/failed request should be reconciled by refreshing the list before retrying. Requests are not automatically replayed.

The five admin-only endpoints are documented in [OpenAPI](./openapi.json): `POST /{id}/deactivate`, `POST /{id}/activate`, `DELETE /{id}`, `POST /{id}/restore`, and `DELETE /{id}/permanent`, under `/api/knowledge-items`.
