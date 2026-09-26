# entities

Entity listing and entity-document linking — the `/api/entities` routes and `GET /api/knowledge-items/{id}/entities`.

Expected contents:

- `EntityController` — filtered/limited entity list, single entity, related documents for an entity, top entities for a document.
- `EntityService` — pulls entities from the knowledge engine through the `gbrain` adapter and maps engine ids back to our knowledge items.
- Response records (`EntitySummary`, `EntityDetail`, ...).

Automated extraction is noisy; return limited, filterable results and never fail a request because an entity has missing or odd data. Interactive graph visualization is a stretch goal and would build on this package.
