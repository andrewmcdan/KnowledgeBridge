# KnowledgeBridge TODO

## Week of October 27-November 1, 2026

Updated October 28 with the team's current assignments. Each item below is planned work; completion requires the implementation and verification described in its acceptance criteria.

### Andrew - Knowledge-item management and versioning

- [ ] Add admin controls and backend endpoints for soft-delete, restore, and permanent removal of individual knowledge items.
- [ ] Soft-deleted items must be hidden from user browsing, search, synthesis, and current cached answers while remaining available for admin restoration.
- [ ] Permanent removal must clean up the item's application records, retained files, indexed content/embeddings, and document-specific metadata without removing unrelated items or shared entities. Verify the supported gbrain operation through the backend adapter.
- [ ] Support updating content or uploading a new version of an existing item. Preserve the item's identity and version history, identify the active version, reindex the replacement, and invalidate stale answers. Prevent superseded versions from being treated as current evidence.
- [ ] Handle concurrent edits, in-flight ingestion, partial failures, and retries without republishing deleted content or losing the last accepted version. Add role and lifecycle regression tests.

**Acceptance:** an admin can update/version, soft-delete, restore, and permanently remove a single item through the application. Search, citations, and caches follow the item's active version and visibility; ordinary users cannot perform management writes.

### Andrew - Evaluate AI responses against the synthetic corpus

- [ ] Choose a repeatable evaluation runner: Python through Spring's API or the existing backend testing framework. Reuse the 50-document corpus and the existing 60 ground-truth questions rather than creating a separate answer key.
- [ ] Verify the controlled corpus is fully ingested and searchable. Keep catalogs and expected answers outside the indexed knowledge base; record corpus hashes and source-to-application-ID mappings.
- [ ] Run Search and Synthesis cases covering factual lookups, multi-document answers, policies, entities, unsupported questions, ambiguity, conflicts, and stale sources. Distinguish fresh synthesis from cache hits and real-provider results from mocks.
- [ ] Save actual answers, returned citations, retrieval results, latency, token/cost observations where available, and configuration/model details under `evaluation/`. Score groundedness, citation accuracy, completeness, retrieval success, and gap handling; use human review for whether claims are supported.
- [ ] Document failures and unrun cases, then rerun affected cases after fixes. Agree on a live-provider test budget and keep paid evaluation separate from routine CI.

**Acceptance:** another team member can rerun the evaluation and inspect a report containing measured results, representative successes/failures, and the status of all 60 cases.

### Adrian - Ambiguous, conflicting, and stale knowledge; coverage suggestions

- [ ] Handle ambiguous questions with a clarification request or clearly stated interpretation instead of silently choosing a meaning.
- [ ] Identify conflicting sources, explain the disagreement, and cite the relevant documents. Use source authority, effective dates, and supersession/version metadata when determining which information is current; upload recency alone is insufficient.
- [ ] Distinguish current facts from historical records and make stale or superseded evidence explicit. Coordinate with Andrew's versioning and cache changes so old versions cannot silently supply current answers.
- [ ] When evidence is missing or incomplete, explain the gap and suggest the types of documents or updates that would improve coverage, such as an approved policy, current project status, decision record, or missing procedure. Ground each suggestion in the missing information.
- [ ] Add regression cases from the synthetic corpus for ambiguity, disagreement, stale sources, partial answers, and unsupported questions. Keep provider failures distinct from knowledge gaps and integrate findings from Andrew's evaluation.

**Acceptance:** the application communicates uncertainty and conflicting/old evidence with useful citations and actionable coverage suggestions, without inventing missing facts or business decisions.

### Adrian - PDF ingestion

- [ ] Add PDF upload support through the existing Spring ingestion/extraction pipeline, including the file picker, validation, status display, retry behavior, and API documentation.
- [ ] Establish the first supported scope as text-based PDFs. Extract readable text while preserving useful headings, reading order, and page provenance for source inspection and citations.
- [ ] Define and test file/content limits and safe responses for malformed, password-protected, empty, scanned/image-only, and unsupported PDFs. State clearly when OCR is required; do not report a blank extraction as successful ingestion.
- [ ] Reuse filename/AI-title handling, entity extraction, indexing, and knowledge-item management where applicable. Test representative tables and multi-column documents and document extraction limitations.

**Acceptance:** an admin can upload a supported PDF, see its processing outcome, retrieve an answer grounded in it, and open readable source content. Unsupported cases receive clear errors, and existing Markdown/DOCX/video workflows continue to pass.

### Ethan - Dashboard with an interactive entity graph

- [ ] Replace the Dashboard placeholder with an interactive node-link view of entities and their relationships using live data through Spring's `/api` boundary.
- [ ] Confirm the available relationship contract with the backend. Distinguish actual extracted relationships from document co-occurrence; label inferred links clearly rather than presenting them as verified facts.
- [ ] Support selecting a node or edge to inspect entity/relationship details and navigate to supporting documents. Include a usable legend, search/filter controls, and pan/zoom as appropriate.
- [ ] Bound the amount of data rendered and provide loading, empty, error, and noisy/unverified-data states. Verify keyboard access and responsive behavior, with a readable list alternative where needed.

**Acceptance:** the Dashboard displays a usable graph backed by actual workspace data, and users can follow an entity or relationship to its supporting knowledge without fabricated connections.

### Owner TBD - Highlight supporting passages in the document view

- [ ] Assign an owner and agree on the frontend/backend source-locator contract. The work spans the answer/citation UI and the evidence returned for each document.
- [ ] After an answer is returned, let a user open a cited document and automatically scroll to and highlight the passage or passages supporting that answer. Highlight source evidence rather than searching for the synthesized answer verbatim.
- [ ] Preserve the existing Markdown rendering, metadata/entities disclosures, and full-document reading. Support more than one relevant passage and a clear way to move between highlights or clear them.
- [ ] Associate locators with the cited document version. If the document changed or a reliable passage location is unavailable, show the document with a clear message instead of highlighting unrelated text.
- [ ] Test multiple citations, paraphrased answers, repeated text, Markdown tables/lists, missing locators, and changed document versions. Coordinate PDF page provenance with Adrian and versioning with Andrew.

**Acceptance:** opening a citation takes the user to the correct supporting passage in the appropriate source, with accessible, safe highlighting and an honest fallback when an exact location cannot be resolved.

### Andrew - Deploy to AWS

- [ ] Choose and document an AWS deployment layout for the React frontend, Spring backend, PostgreSQL/pgvector, and gbrain. Set an initial budget and identify persistent database/upload storage requirements.
- [ ] Configure deployment-time secrets, service networking, HTTPS, health checks, logs, and backups. Keep provider keys and database credentials outside the repository and browser; the frontend continues to call only Spring's API.
- [ ] Document repeatable build/deployment, database migrations, configuration, backup/restore, and rollback procedures. Preserve data across application/container restarts.
- [ ] Deploy a demonstration environment and verify login/roles, document ingestion, Search, Synthesis, source citations, entity browsing, and failure recovery against the deployed services.

**Acceptance:** the team can access the AWS demo over HTTPS, execute the core workflow against persistent data, inspect operational logs, and reproduce or roll back the deployment from documented steps.

### End-of-week integration and verification

- [ ] Confirm the owner for answer highlighting and record cross-feature API dependencies before parallel implementation.
- [ ] Demonstrate knowledge-item lifecycle/versioning, PDF ingestion, source-quality/gap behavior, the graph Dashboard, and answer highlighting for completed work. Record remaining blockers and owners explicitly.
- [ ] Run the repeatable AI evaluation against the agreed corpus/version and preserve its results with the demonstrated build. Record local and AWS verification separately.
- [ ] Run relevant frontend tests, formatting, lint, TypeScript/build, backend coverage-gated `check` with Docker, and deployment/configuration checks for changed components. No duplication checker is currently configured. Verify affected UI flows in a browser.
- [ ] Update API/setup/user documentation with delivered behavior and limitations. Carry other open GitHub issues and unfinished work forward without marking them complete solely because related features were added.

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
