# Frontend

The KnowledgeBridge React and TypeScript application is built with Vite. It calls only the Spring Boot `/api` boundary; it must not call gbrain or AI providers directly.

## Workspace scaffold

The workspace provides collapsible navigation, global search, Search/Synthesis modes, entity and related-document panels, and ingestion status. It adapts to a drawer navigation and stacked panels on phones.

The Uploads page is admin-only: it accepts one UTF-8 Markdown file (up to 1 MiB), one Word `.docx` file (up to 10 MiB, converted to Markdown by the backend), or a manual note. Creation responses report the resulting ingestion status, including FAILED. The Knowledge Base is available to signed-in users; the backend limits normal users to completed items, while admins see all statuses. Search filters the loaded list. Usage and administration have explicit placeholder views.

Authentication, ingestion, and error responses follow [the committed OpenAPI contract](../Docs/openapi.json). Stored sessions are restored through `/api/auth/me`; rejected sessions return to login. Logout calls the API and clears local credentials even when acknowledgement fails. JWTs are not revoked server-side.

Use Ctrl/Cmd+K to focus global search and Enter to view matching knowledge items. Escape closes document previews. No new frontend dependencies are required.

## Development

With the backend running on port 8080:

```powershell
npm install
npm run dev
```

Vite serves the application at `http://localhost:5173` and proxies `/api` requests to Spring Boot. Run `npm run format:check`, `npm run lint`, `npm test`, and `npm run build` before submitting frontend changes.
