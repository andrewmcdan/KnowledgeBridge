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