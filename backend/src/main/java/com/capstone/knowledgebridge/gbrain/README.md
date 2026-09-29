# gbrain

Adapter for the external [gbrain](https://github.com/garrytan/gbrain) knowledge engine. This is the **only** package that talks to gbrain over HTTP; nothing else in the backend (and never the frontend) calls it directly.

Expected contents:

- `GbrainClient` — HTTP client configured from `GBRAIN_BASE_URL`; sends content for indexing, runs search and synthesis queries, lists entities, and deletes content.
- Request/response records mirroring gbrain's API, kept separate from our own DTOs so engine changes stay contained here.
- `GbrainException` (or similar) translating transport and engine errors into something the service layer can handle.

Note: upstream currently exposes MCP over HTTP with OAuth at `/mcp` rather than the REST API assumed in the project brief; see `docker/README.md`.
