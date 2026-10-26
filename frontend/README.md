# Frontend

The KnowledgeBridge React and TypeScript application is built with Vite. It calls only the Spring Boot `/api` boundary; it must not call gbrain or AI providers directly.

## Workspace scaffold

The workspace provides collapsible navigation, global search, Search/Synthesis modes, entity and related-document panels, and ingestion status. It adapts to a drawer navigation and stacked panels on phones.

The Uploads page is admin-only: it accepts UTF-8 Markdown (up to 1 MiB), Word `.docx` (up to 10 MiB, converted to Markdown by the backend), MP4/MOV/WebM video, or a manual note. Video size and duration limits remain server-configured (default 500 MiB and two hours); transcription may incur provider charges. Creation responses report the resulting ingestion status, including FAILED. The Knowledge Base is available to signed-in users; the backend limits normal users to completed items, while admins see all statuses. Search filters the loaded list. Administration remains a placeholder.

Administrators can choose **Edit note** on a manual note in the Uploads knowledge list, change its title, type, or text, and **Save and reindex**. Stale or concurrent edits return a conflict and preserve the draft until the administrator reloads or cancels. Uploads and videos cannot be edited through this workflow. Failed reindexing keeps the saved text and exposes the existing retry action.

Ask AI displays synthesis tokens per answer and refreshes persisted totals after a successful query. The Usage page provides the same totals: personal for users, workspace-wide for administrators. Reported engine counts take precedence; missing counts use a four-characters-per-token estimate based on the question, full unique cited document text, and answer. Actual provider prompts can differ. Cache hits and search-only requests add no synthesis tokens. Totals exclude failed/unreported calls, embedding, ingestion, and activity before tracking began; they are not a billing statement.

The Upload folder tab uses the browser's directory picker to enumerate nested files recursively. Only eligible extensions are sent, one file at a time: documents use `/api/knowledge-items/upload`, videos use `/api/knowledge-items/video`. Relative paths identify files in the preview, not server filesystem destinations. Upload failures are isolated per file; retrying a batch skips files already queued. Stop after current file leaves remaining files unsent. Keep the page open until uploading finishes; choosing the folder again creates a new batch and can upload duplicates. The preview shows the first 100 entries but processes all eligible files. Queued means accepted, not successfully indexed; the existing knowledge list polls ingestion status. Folder selection requires a browser with directory-picker support.

Authentication, ingestion, and error responses follow [the committed OpenAPI contract](../Docs/openapi.json). Stored sessions are restored through `/api/auth/me`; rejected sessions return to login. Logout calls the API and clears local credentials even when acknowledgement fails. JWTs are not revoked server-side.

Use Ctrl/Cmd+K to focus global search and Enter to view matching knowledge items. Escape closes document previews. No new frontend dependencies are required.

## Development

With the backend running on port 8080:

```powershell
npm install
npm run dev
```

Vite serves the application at `http://localhost:5173` and proxies `/api` requests to Spring Boot. Run `npm run format:check`, `npm run lint`, `npm test`, and `npm run build` before submitting frontend changes.
