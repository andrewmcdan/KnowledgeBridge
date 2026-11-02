# Frontend

The KnowledgeBridge React and TypeScript application is built with Vite. It calls only the Spring Boot `/api` boundary; it must not call gbrain or AI providers directly.

## Workspace scaffold

The Dashboard renders an interactive 3D entity graph using `react-force-graph-3d` (Three.js/WebGL) and `three-spritetext` labels. Entities are nodes; a single undirected link contains the documents shared by an entity pair. This represents document co-occurrence, not a verified semantic relationship. Select a node to explore its neighbors or a link to list its shared documents; source buttons open the existing full-document modal. Rotate, zoom, pan, drag nodes, fit the view, pause the layout, and toggle labels. List view exposes the same selections to keyboard users and remains available if WebGL cannot start. Expand View settings to adjust layout spacing (density), node size, link thickness/opacity, and label size; toggle entity labels or document-weighted node sizing. Spacing changes resume and refit the layout. Settings persist across filters, refreshes, and graph/list switches while this dashboard is open; Reset view settings restores defaults.

Graph loading reuses authenticated Spring entity/document endpoints, with at most four related-document requests in flight. The default snapshot includes up to 50 entities (select 25/50/100) and 100 documents per entity. Limit notices identify potentially incomplete views; name/type and minimum-shared-document filters apply to the loaded snapshot. Refresh manually after content changes; the existing entity-extraction event also reloads it. Failed or canceled loads never publish a partial graph. The 3D renderer is loaded only when needed on the Dashboard.

The workspace provides collapsible navigation, global search, Search/Synthesis modes, entity and related-document panels, and ingestion status. It adapts to a drawer navigation and stacked panels on phones.

The Uploads page is admin-only: it accepts UTF-8 Markdown (up to 1 MiB), Word `.docx` (up to 10 MiB, converted to Markdown by the backend), MP4/MOV/WebM video, or a manual note. Video size and duration limits remain server-configured (default 500 MiB and two hours); transcription may incur provider charges. Creation responses report the resulting ingestion status, including FAILED. The Knowledge Base is available to signed-in users; the backend limits normal users to completed items, while admins see all statuses. Search filters the loaded list. The Admin page lets existing admins find a user and confirm a grant of admin access. Disabled accounts cannot be promoted. Successful promotions update the list immediately; the promoted user signs out and signs in again to activate the new role.

Administrators can choose **Edit note** on a manual note in the Uploads knowledge list, change its title, type, or text, and **Save and reindex**. Stale or concurrent edits return a conflict and preserve the draft until the administrator reloads or cancels. Uploads and videos cannot be edited through this workflow. Failed reindexing keeps the saved text and exposes the existing retry action.

Ask AI renders synthesis answers as Markdown, including headings, tables, lists, quotes, and code blocks. It shares the document preview renderer: raw HTML is skipped, unsafe links are filtered, and images appear as links. Citations, warnings, and token usage remain below the formatted answer, including for cached answers and extractive fallbacks.

Click a citation title or an inline reference (`[1]` or `[knowledgebridge/123]`) to open its source in the document detail modal. Previews fetch the current document content through Spring, with the same metadata/entity disclosures and close controls as the Knowledge Base. Only citations with a valid application document ID can open a preview; unmatched references and code examples remain plain text.

Ask AI displays synthesis tokens per answer and refreshes persisted totals after a successful query. The Usage page provides the same totals: personal for users, workspace-wide for administrators. Reported engine counts take precedence; missing counts use a four-characters-per-token estimate based on the question, full unique cited document text, and answer. Actual provider prompts can differ. Cache hits and search-only requests add no synthesis tokens. Totals exclude failed/unreported calls, embedding, ingestion, and activity before tracking began; they are not a billing statement.

Upload titles default to a cleaned filename for documents and videos. Selecting another file refreshes an automatic title while preserving manually entered guidance. The optional AI naming checkbox uses document text or a video transcript and the entered title as guidance; final titles appear after processing. Folder uploads offer AI naming with optional batch guidance. AI naming failures can be retried, and successful titles are reused if indexing fails. Naming may incur provider charges and is excluded from query usage totals.

The Upload folder tab uses the browser's directory picker to enumerate nested files recursively. Only eligible extensions are sent, one file at a time: documents use `/api/knowledge-items/upload`, videos use `/api/knowledge-items/video`. Relative paths identify files in the preview, not server filesystem destinations. Upload failures are isolated per file; retrying a batch skips files already queued. Stop after current file leaves remaining files unsent. Keep the page open until uploading finishes; choosing the folder again creates a new batch and can upload duplicates. The preview shows the first 100 entries but processes all eligible files. Queued means accepted, not successfully indexed; the existing knowledge list polls ingestion status. Folder selection requires a browser with directory-picker support.

Authentication, ingestion, and error responses follow [the committed OpenAPI contract](../Docs/openapi.json). The landing page links to `/register`, where visitors create an enabled USER account and are signed in automatically with a session-storage token. Passwords require eight Unicode characters and fit within BCrypt's 72-byte UTF-8 limit; confirmation is checked before submission. Existing emails receive a sign-in suggestion. Authenticated visits to `/register` redirect to Ask AI. Stored sessions are restored through `/api/auth/me`; rejected sessions return to login. Logout calls the API and clears local credentials even when acknowledgement fails. JWTs are not revoked server-side.

Document previews use a nearly full-screen dialog with a persistent top-left close button; Escape also closes the preview. Markdown is rendered with `react-markdown` and `remark-gfm`, including tables, task lists, and code blocks. YAML front matter is parsed with `yaml` and shown in the metadata disclosure. Metadata and detected entities start collapsed; entities are grouped into people, companies, projects, and teams with summaries and verification labels. Raw HTML is skipped and images appear as links. Entity lookup failures leave document content readable.

Ask AI accepts one active submission at a time, including rapid repeated form submissions. Cancel request stops waiting and allows a new question; leaving Ask AI also aborts its client request. Canceled or late responses cannot replace a newer result or refresh its usage totals. Cancellation does not guarantee that server/provider processing or billing stops; usage totals are refreshed on the next successful query or a manual refresh.

Reference links from Search and Synthesis open the current full document with up to six matching passages highlighted and scroll to the best match. Previous/Next passage controls cycle through them. Search uses the clicked excerpt; synthesis compares paragraphs containing that source's citation markers (or an answer without inline markers) with the document's rendered text. Synthesis highlights are related text matches, not engine-provided quote offsets or proof of factual agreement. The submitted question is retained with its result to rank matches even after the input is edited. If the current document has no sufficiently strong match, the preview explicitly says so and remains readable. Regular Knowledge Base and dashboard previews remain unhighlighted.

Use Ctrl/Cmd+K to focus global search and Enter to view matching knowledge items.

## Development

With the backend running on port 8080:

```powershell
npm install
npm run dev
```

Vite serves the application at `http://localhost:5173` and proxies `/api` requests to Spring Boot. Run `npm run format:check`, `npm run lint`, `npm test`, and `npm run build` before submitting frontend changes.
