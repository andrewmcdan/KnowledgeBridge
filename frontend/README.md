# Frontend

The KnowledgeBridge React and TypeScript application is built with Vite. It calls only the Spring Boot `/api` boundary; it must not call gbrain or AI providers directly.

## Workspace scaffold

The workspace provides collapsible navigation, global search, Search/Synthesis modes, entity and related-document panels, and ingestion status. It adapts to a drawer navigation and stacked panels on phones.

The Uploads page is live for signed-in users: it accepts one UTF-8 Markdown file (up to 5 MiB) or a manual note and loads the persisted knowledge list. Search filters the currently loaded knowledge list. Ingestion, Ask AI retrieval and synthesis, entity extraction, and usage metrics will appear when their live API integrations are connected. Usage and administration have explicit placeholder views.

Use Ctrl/Cmd+K to focus global search and Enter to view matching knowledge items. Escape closes document previews. No new frontend dependencies are required.

## Development

With the backend running on port 8080:

```powershell
npm install
npm run dev
```

Vite serves the application at `http://localhost:5173` and proxies `/api` requests to Spring Boot. Run `npm run lint` and `npm run build` before submitting frontend changes.
