# knowledge

Knowledge item CRUD and metadata — the `/api/knowledge-items` routes.

Expected contents:

- `KnowledgeItemController` — list/get for users; manual create, upload, update, soft-delete, restore, permanent delete, and retry for admins (`@PreAuthorize("hasRole('ADMIN')")`).
- `KnowledgeItemService` — business rules, ownership, and the knowledge-revision bump on every write.
- `KnowledgeItem` entity/repository for the `knowledge_item` table (title, type, owner, status, `external_engine_id`, `soft_deleted_at`, ...).
- Request/response records for the routes above.

Ingestion mechanics live in `ingestion`; this package only orchestrates them.
