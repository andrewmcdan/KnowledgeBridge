# Frontend

The KnowledgeBridge React and TypeScript application is built with Vite. It calls only the Spring Boot `/api` boundary; it must not call gbrain or AI providers directly.

## Workspace scaffold

The workspace provides collapsible navigation, global search, Search/Synthesis modes, entity and related-document panels, and ingestion status. It adapts to a drawer navigation and stacked panels on phones.

The Uploads page is available to administrators: it accepts UTF-8 Markdown (up to 1 MiB), Word `.docx` (up to 10 MiB, converted to Markdown by the backend), MP4/MOV/WebM video, or a manual note. Video size and duration limits remain server-configured (default 500 MiB and two hours); transcription may incur provider charges.

The Upload folder tab uses the browser's directory picker to enumerate nested files recursively. Only eligible extensions are sent, one file at a time: documents use `/api/knowledge-items/upload`, videos use `/api/knowledge-items/video`. Relative paths identify files in the preview, not server filesystem destinations. Upload failures are isolated per file; retrying a batch skips files already queued. Stop after current file leaves remaining files unsent. Keep the page open until uploading finishes; choosing the folder again creates a new batch and can upload duplicates. The preview shows the first 100 entries but processes all eligible files. Queued means accepted, not successfully indexed; the existing knowledge list polls ingestion status. Folder selection requires a browser with directory-picker support.

Search filters the currently loaded knowledge list. Usage and administration have explicit placeholder views. Run `node --test tests/*.test.mjs` for frontend regression checks, including nested folder enumeration, validation, batch processing, and document/video route selection.

Use Ctrl/Cmd+K to focus global search and Enter to view matching knowledge items. Escape closes document previews. No new frontend dependencies are required.

## Development

With the backend running on port 8080:

```powershell
npm install
npm run dev
```

Vite serves the application at `http://localhost:5173` and proxies `/api` requests to Spring Boot. Run `npm run lint` and `npm run build` before submitting frontend changes.
