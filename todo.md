# KnowledgeBridge TODO

## Week of October 27-November 1, 2026

Reviewed October 26 against `main` at `f9a0c48` (through PR #37), the documents in `Docs`, application code, tests, and repository artifacts. Assignments below are proposed continuations of the existing team responsibilities. Historical weekly notes are preserved below.

**Goal:** close the remaining required content-management and cost gaps, record repeatable Milestone 2 acceptance evidence, and begin the Milestone 3 reliability baseline. Complete required workflows before adding more stretch features.

**Priority order:** first secure the repeatable 50-document end-to-end demonstration and fix blocking defects; then finish required per-item deletion and basic cost accounting; use the agreed live-test budget for the evaluation pilot. Do not delay the M2 acceptance record for optional UI features or the full M3 study.

### Current state compared with the plans

| Area                           | Current implementation                                                                                                                                                                    | Remaining work                                                                                                                                                                                                                                           |
| ------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Authentication and roles       | Login/logout, session restoration, backend ADMIN/USER enforcement, self-registration, and admin promotion are implemented.                                                                | Include both roles in the integrated acceptance run; update the SDS to reflect current JWT behavior and limitations.                                                                                                                                     |
| Ingestion and editing          | Manual notes and revision-checked editing/reindexing; Markdown/DOCX uploads; asynchronous video transcription; status, safe failures, and retry are implemented.                          | Record real-provider success/failure/retry evidence, including indexing readiness after a completed write.                                                                                                                                               |
| Upload usability               | Filename titles, guided AI titles, recursive folder uploads, and the redesigned single-file selector are implemented.                                                                     | Standalone multi-file selection and drag-and-drop from the old notes remain optional follow-up work. Folder upload still uses a shared manually entered document type.                                                                                   |
| Search, synthesis, and sources | Live API integration, Markdown results/answers, full-document buttons, clickable citations, and entity/document navigation are implemented. Gap/warning fields are displayed.             | Evaluate whether unsupported, ambiguous, conflicting, and stale-source questions produce appropriate answers or warnings; implemented UI is not a reliability result.                                                                                    |
| Cache and usage                | PostgreSQL exact-match synthesis cache, knowledge-revision invalidation, admin cache list/clear API, per-query token estimates, and persistent personal/workspace totals are implemented. | Add ingestion accounting and a basic estimated USD cost table with explicit assumptions. Query totals currently exclude embeddings, ingestion, transcription, and AI naming.                                                                             |
| Document lifecycle             | The adapter supports soft-delete/restore. A guarded script purges the entire local knowledge workspace.                                                                                   | Per-document soft-delete, restore, and permanent-delete application routes/UI are absent. The purge script is not a replacement for admin item management. Hard deletion of one document and its indexed content still needs a verified engine contract. |
| Corpus and evaluation          | Exactly 50 Markdown documents and 60 ground-truth cases exist; the corpus validator passes. `evaluation/` contains only its README.                                                       | Commit a repeatable corpus-loading workflow, a 50/50 ingestion/readiness manifest, and actual evaluation records. Existing fixtures and smoke tests do not establish full-corpus acceptance.                                                             |
| Documentation                  | Setup, integration guidance, OpenAPI, architecture diagrams, and an initial AI interaction design exist.                                                                                  | Reconcile planned versus implemented routes, refresh the SDS/test/user documentation, and add or locate the referenced cost workbook. Some design artifacts still describe the adapter/provider integration as pending.                                  |

Evidence: [sponsor brief](<Docs/07-KnowledgeBridge%20AI-Powered%20Business%20Knowledge%20Management%20Platform-IND%20(1).md>), [execution plan](Docs/KnowledgeBridge%20Capstone%20Project%20Execution%20Plan.md) sections 3, 7-11, [API inventory](Docs/API-Routes.md), [integration contract](Docs/Integration-and-authentication.md), [knowledge controller](backend/src/main/java/com/capstone/knowledgebridge/knowledge/KnowledgeItemController.java), [gbrain boundary](backend/src/main/java/com/capstone/knowledgebridge/gbrain/GbrainClient.java), [usage implementation](backend/src/main/java/com/capstone/knowledgebridge/usage/UsageService.java), and [corpus guide](synthetic-data/README.md).

### Tuesday: agree on contracts, dates, and test budget

- [ ] **Andrew + Dylan:** resolve the conflicting dates in the [project plan](<Docs/Project%20Plan%20Template%20(SCP-Form02)-2%20(3).docx>). Its deliverables list gives October 18 for M2 and November 15 for final delivery; its milestone table gives November 9 for M2, November 30 for M3, and December 7 for the final demo. Record the confirmed schedule with the team/sponsor rather than treating either set as approved here.
- [ ] **Andrew + Adrian + Ethan:** agree on per-document delete/restore/permanent-delete contracts, visibility rules, in-flight ingestion behavior, and retryable failure states before implementing either side. Keep React -> Spring `/api` -> backend gbrain adapter.
- [ ] **Dylan + Luis:** agree on the minimum cost-event/summary contract, dated provider/model pricing assumptions, and a funded ceiling for the live corpus/evaluation run. Search avoids synthesis but semantic query embeddings may still cost money.

### Engine lifecycle and integration

- [ ] Verify the pinned engine's supported per-document permanent-deletion operation in an isolated test workspace. Prove removal of the document, chunks/vectors, versions, and document-specific derived references; preserve entities shared by other documents. Add the operation behind the adapter with safe timeout/reconciliation behavior. Do not present soft-delete as permanent deletion or generalize the whole-workspace purge SQL into an unverified API.
- [ ] Pair with Adrian on deletion/cache correctness: hidden content must not reach search or synthesis, old cached answers must be invalidated, and an ambiguous engine failure must remain visible/retryable without claiming success. Raise any unsupported engine behavior with the sponsor immediately.
- [ ] Rehearse the real-gbrain integration flow with Luis on a second team machine, including fresh startup/provisioning, projection readiness, an engine outage, and recovery. Record commit/configuration, safe logs, and observed results without credentials or document bodies in logs.

**Acceptance:** a tested engine contract unblocks the lifecycle work, and the integrated demo uses real retrieval/synthesis. Mock checks remain clearly identified.

### Per-document content management

- [ ] Implement admin-only soft-delete, restore, and permanent-delete services/routes using the agreed adapter contract. Coordinate application metadata, retained video/audio files, ingestion attempts, entity associations, and caches. Distinguish retained data for restore from data that permanent deletion must remove.
- [ ] Make lifecycle transitions safe against active ingestion, note edits, retries, repeated requests, and partial engine/storage failures. Bump the knowledge revision at the appropriate visibility changes and prevent a background job from republishing deleted content.
- [ ] Add authorization, lifecycle, persistence, and failure-path tests; verify deletion removes source access and retrieval, while restore returns the intended version without duplicates. Preserve unrelated documents, users, and shared entity data.

**Acceptance:** one item can be hidden, restored, and permanently removed through Spring without resetting the workspace; USER writes return 403 and failures preserve an actionable state. Backend `check`, including its coverage gates, passes.

### Basic cost accounting and integration contracts

- [ ] Extend durable usage accounting to include ingestion counts/estimated embedding usage and persist the pricing/model basis needed for estimated USD totals. Keep observed provider counts distinct from estimates; cached synthesis adds zero new synthesis cost. Do not label missing accounting as free usage.
- [ ] Provide the minimum admin cost summary needed for a simple table, reusing `/api/usage` where practical. Preserve personal query totals for users and restrict workspace cost/management data to admins. Document whether transcription, AI naming, and extraction-related provider calls are included or explicitly excluded.
- [ ] Test estimates, cache hits, failures, migration preservation, and role scoping. Update OpenAPI, the integration guide, and the SDS with actual routes and limitations. In particular, `/api/admin/cache` already exists in code but is absent from OpenAPI and still described as planned in the API inventory.

**Acceptance:** an admin sees persisted ingestion/query counts and estimated USD totals with understandable assumptions. The API, UI contract, and documentation agree; no fixed benchmark price is silently treated as a current provider rate.

### Admin controls and demo usability

- [ ] Add per-item soft-delete, restore, and permanent-delete controls once the backend contract is agreed. Include named-document confirmation, pending/error/retry states, and a deleted-items view for restoration. Keep destructive controls admin-only and verify keyboard/mobile behavior.
- [ ] Connect the basic cost summary to the Usage page with clear estimate/coverage labels and loading, empty, forbidden, and failure states. Charts and an advanced dashboard are not required for this task.
- [ ] Exercise Search -> full document, Synthesis -> citation -> full document, and document -> entities -> related document against the live backend. Check gap/partial-answer messaging and distinguish provider outages from insufficient evidence. Fix failures found during the acceptance rehearsal and include browser evidence with UI PRs.

**Acceptance:** an admin can manage a single document and read basic costs from the UI; a normal user can query and open permitted sources without seeing management controls. Frontend tests, formatting, lint, TypeScript/build, and affected browser flows pass.

### Corpus acceptance and reliability baseline

- [ ] Build a repeatable loader/verification script through Spring's admin API for only `synthetic-data/documents/`. Use catalog IDs/hashes to record application IDs and safe rerun behavior. Never ingest `reference/`, answer keys, catalogs, or extra DOCX/video fixtures into the controlled 50-document benchmark.
- [ ] Validate and load all 50 documents into the designated test workspace. Record completed/failed counts, durations, retry outcomes, engine projection readiness, and searchable source IDs in a run manifest under `evaluation/`. A successful HTTP upload or `COMPLETED` alone does not prove semantic indexing is ready.
- [ ] Build the result recorder/scoring rubric around the existing 60 questions; do not recreate an answer key. Run a budget-bounded pilot covering factual, multi-document, policy, entity, gap, ambiguous, conflicting, and historical questions. Save actual answers/citations, source mappings, retrieval top-1/3/5, groundedness, completeness, gap honesty, latency, cache behavior, and estimated usage. Identify unrun cases explicitly; the full study remains M3 work.
- [ ] Add a requirement-to-test matrix and a repeatable demo checklist covering admin/user permissions, ingestion/retry, edit/reindex, exact cache hit and invalidation, source/entity navigation, deletion, and usage. Trace Mr. Khandke's earlier PR feedback to existing fixes or remaining issues without assuming the old note has been resolved.
- [ ] Add or locate the referenced cost workbook and document provider/model/rate dates, development budget, and 50/75/100-user assumptions with Dylan. Start reliability/cost/feasibility findings from measured examples, not placeholders or simulated successes.

**Acceptance:** another team member can reproduce the 50/50 ingestion run and review the pilot's real outputs, known failures, cost assumptions, and remaining evaluation cases from committed artifacts. No live-provider evaluation runs in routine CI.

### Sunday integration gate

- [ ] Demonstrate add -> ingest -> searchable index -> Search/Synthesis -> full source/citations, then edit -> reindex -> cache invalidation, using the verified corpus and both roles.
- [ ] Demonstrate soft-delete -> no retrieval/source access -> restore, then permanent-delete -> removal from application storage and engine, with failure/retry evidence.
- [ ] Show query token totals and the basic ingestion/cost table; document all exclusions and current limitations.
- [ ] Run frontend tests/format/lint/build, backend coverage-gated `check` with Docker, and Compose/development-script checks. No duplication checker is currently configured; record this explicitly. Record browser checks separately from unit/integration mocks.
- [ ] Update the SDS, test specification, user/admin guide, and architecture/AI-design status so planned features are not presented as completed. Attach acceptance/evaluation evidence and carry unresolved defects forward with owners.

### Deferred until the required workflows pass

- Standalone multi-file selection, drag-and-drop, and matching folder type inference to the single-file form.
- Advanced cost charts, interactive graph visualization, semantic caching, automatic query routing, multi-turn chat, and PDF ingestion. Folder/video ingestion already provide implemented stretch work; stabilize it before expanding scope.
- Dashboard, query history/feedback, and persistent bookmarks from the broader API/UI inventory unless needed for an agreed acceptance criterion. The Dashboard route remains a placeholder; API inventory rows are not proof of implementation.
- Full 60-question scored reliability study and final report/presentation completion, scheduled against the confirmed M3/final dates after this week's baseline.

## Historical weekly notes

The entries below are retained as the original assignment history, not a statement that every task is still open.

Week of October 13:
Andrew - Video ingestion and processing
Ethan - Frontend / video ingestion / Admin routing
Dylan - Authentication / SDS
Adrian - Entitiy extraction / Docx ingestion / Caching (naive exact-match cache by query string)
Luis - SDS


Notes:
Multifile upload
Drag and drop
Filename / title optional (use filename)


Turn Mr. Khandke's comments on the PR into issues and work on these




Week of September 29:
1. Member 1: integration and authentication Dylan
Focus on clearing cross-team dependencies rather than implementing everyone else’s features.
- Finalize request/response contracts for:
  - POST /api/knowledge-items/manual
  - POST /api/knowledge-items/upload
  - GET /api/knowledge-items
  - POST /api/knowledge-items/{id}/retry-ingestion
- Define one consistent API error response.
- Confirm login, logout, and /api/auth/me behavior with the frontend.
- Add admin authorization to ingestion endpoints.
- Refactor authentication toward the users repository if Member 2 creates it.
- Add or finish CI for frontend and backend checks.
- Integrate the other branches and resolve contract mismatches.
Acceptance criteria:
- Admin and user permissions are enforced by the backend.
- The frontend can determine the current user and role.
- All teams use the same committed API contracts.
- The complete stack starts through the documented development workflow.

2. Member 2: knowledge items and ingestion Adrian
Own the application database side of the workflow.
- Add migrations for knowledge_item, ingestion_attempt, and knowledge revision state.
- Implement entities and repositories.
- Implement manual note creation and Markdown upload.
- Validate:
  - .md extension
  - empty content
  - maximum file size
  - required title
- Implement lifecycle transitions:
  - PENDING
  - PROCESSING
  - COMPLETED
  - FAILED
- Record safe ingestion errors and attempt history.
- Implement retry.
- Increment the knowledge revision after successful content changes.
- Coordinate the gbrain call boundary with Member 3.
Acceptance criteria:
- Records remain consistent when gbrain succeeds, times out, or fails.
- Retrying creates a new attempt rather than destroying failure history.
- Unit and integration tests cover every allowed transition.

3. Member 3: gbrain adapter Andrew
Keep all gbrain-specific behavior isolated in the gbrain package.
- Define GbrainClient operations needed for ingestion.
- Implement the agreed MCP transport without leaking MCP types into the rest of the backend.
- Support:
  - submit/index content
  - delete indexed content, if available
  - health or reachability check
- Normalize upstream responses into application-owned records.
- Add timeouts and safe exception translation.
- Provide a deterministic mock implementation or mock server.
- Document actual gbrain request and response examples.
- Confirm whether an external engine ID is returned and how it should be stored.
Acceptance criteria:
- Member 2 can invoke the adapter without knowing MCP details.
- Tests cover success, malformed response, timeout, and upstream failure.
- Logs do not contain document bodies, API keys, or credentials.
- The application can run against a mock when real gbrain is unavailable.

4. Member 4: frontend ingestion workflow Ethan
Replace the relevant demo-only screens with live API behavior.
- Build the login page.
- Add authenticated and admin-only routes.
- Connect logout and current-user behavior.
- Implement:
  - manual note form
  - Markdown upload form
  - knowledge-item list
  - ingestion status badges
  - retry control
- Add loading, validation, empty, forbidden, and failure states.
- Hide admin controls from normal users while relying on backend enforcement for security.
- Keep the existing responsive sidebar and layout intact.
Acceptance criteria:
- An admin can complete the full workflow without manually calling the API.
- A normal user does not see admin navigation.
- Status changes can be refreshed or polled.
- Visible changes include screenshots in the pull request.
- Frontend format, lint, and build checks pass.

5. Member 5: QA, corpus loading, and evaluation preparation Luis
This should be a technical testing role, not just documentation.
- Create the ingestion acceptance-test matrix.
- Add tests for:
  - valid Markdown
  - empty files
  - incorrect extensions
  - oversized files
  - unauthorized and non-admin requests
  - gbrain timeout
  - failed ingestion
  - retry after failure
- Build a repeatable script or test fixture that submits the 50 synthetic documents.
- Verify the synthetic corpus before ingestion.
- Record ingestion duration, success count, and failures.
- Add an end-to-end happy-path test if time permits.
- Help Andrew wire the checks into CI.
Acceptance criteria:
- The entire corpus can be loaded repeatably without manual file-by-file work.
- Failure cases have automated coverage.
- Test results distinguish real-gbrain validation from mocked validation.
- The team has an initial baseline ready for later retrieval evaluation.
