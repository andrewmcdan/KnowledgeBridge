# Frontend

The KnowledgeBridge React and TypeScript application is built with Vite. It calls only the Spring Boot `/api` boundary; it must not call gbrain or AI providers directly.

## Workspace scaffold

The initial Ask AI screen follows the supplied September 11 design reference: collapsible navigation, global search, Search/Synthesis modes, answer citations, entities, related documents, and an ingestion queue. It adapts to a drawer navigation and stacked panels on phones.

Documents, answers, metrics, and queue statuses are labeled sample data. Search filters the sample documents; citation/entity buttons open previews. Bookmarks and answer feedback last only for the current page session. Query submission explains that live AI is not connected, rather than returning the sample as a generated answer. Uploads, usage, and administration have explicit placeholder views. Only `/api/health` uses the backend today.

Use Ctrl/Cmd+K to focus global search and Enter to view matching sample documents. Escape closes document previews. No new frontend dependencies are required.

## Development

With the backend running on port 8080:

```powershell
npm install
npm run dev
```

Vite serves the application at `http://localhost:5173` and proxies `/api` requests to Spring Boot. Run `npm run lint` and `npm run build` before submitting frontend changes.
