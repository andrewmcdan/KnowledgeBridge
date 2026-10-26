# Integration and authentication contract

[`openapi.json`](./openapi.json) is the canonical request/response contract for the four knowledge-item endpoints and registration, login, logout, and current-user behavior. Import it into an OpenAPI-compatible client, or use the examples below. Backend contract tests compare its fields and lifecycle enums with application records; frontend tests consume its knowledge-item example.

## Permissions

| Operation | Anonymous | USER | ADMIN |
|---|---|---|---|
| Registration, login and health | Allowed | Allowed | Allowed |
| Current user and logout | 401 | Allowed | Allowed |
| Knowledge-item list | 401 | Completed items only | All ingestion statuses |
| Completed document content, queries, entity browsing | 401 | Allowed | Allowed |
| Manual creation, document/video upload, ingestion retry, entity extraction | 401 | 403 | Allowed |
| Item management metadata and `/api/admin/**` | 401 | 403 | Allowed |

The backend enforces these permissions. Ingestion writes are rejected in the security filter before JSON or uploaded content is parsed, and method-level authorization remains in place. Frontend navigation and route guards provide the corresponding user experience. A normal user's Knowledge Base can now call the list API; the server filters it to completed items. List ordering is descending creation time, then descending ID. There are no server-side list filters or pagination yet.

## Shared errors

Every application API error uses JSON with these two fields:

```json
{
    "code": "FORBIDDEN",
    "message": "You do not have permission to perform this operation."
}
```

| HTTP status | Common code | Meaning |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Invalid fields, malformed JSON, missing multipart fields, or unreadable document |
| 401 | `UNAUTHENTICATED` | Missing/invalid/expired bearer token, invalid login, or disabled login account |
| 403 | `FORBIDDEN` | Authenticated caller lacks the required role |
| 404 | `NOT_FOUND` | Resource does not exist or is not readable through that endpoint |
| 405 | `METHOD_NOT_ALLOWED` | Unsupported HTTP method |
| 409 | `CONFLICT`, `EMAIL_ALREADY_REGISTERED` | Item state conflict or an existing normalized email |
| 413 | `CONTENT_TOO_LARGE` | Upload or normalized text exceeds its limit |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Unsupported request content type or file extension |
| 500 | `INTERNAL_ERROR` | Unexpected server failure; internal details are not returned |

Query failures retain the existing engine categories such as `TIMEOUT`, `RATE_LIMITED`, and `UNAVAILABLE`, using the same `{code, message}` envelope. Clients must tolerate additional codes. The HTTP status is authoritative. Reverse-proxy/network failures may not carry application JSON; the frontend shows a safe fallback rather than raw HTML.

## Authentication

Visitors can choose **Create account** below **Sign in**, or open `/register`. Authenticated visitors opening `/register` go to Ask AI.

```http
POST /api/auth/register
Content-Type: application/json

{"displayName":"New Reader","email":"reader@example.test","password":"sample password"}
```

Success is `201` with the same `{token, expiresAt, user}` response as login. The backend exclusively assigns `USER` and `enabled=true`; extra client fields such as `role` or `enabled` are ignored. Registration grants immediate access to completed workspace knowledge, without email verification. The frontend stores the new token in session storage and opens Ask AI; reload restores that session, and logout clears it.

Names and emails are trimmed, emails lowercased, and both must be nonblank and at most 255 characters. Email must be valid. Passwords are preserved exactly, including spaces, and require at least eight Unicode code points with no mandatory character combinations. The installed BCrypt encoder accepts at most 72 UTF-8 bytes; the API rejects longer passwords before encoding. The UI confirms an exact password match; confirmation is not part of the API request.

Invalid fields return `400 VALIDATION_ERROR`. Existing normalized emails return `409 EMAIL_ALREADY_REGISTERED` with a sign-in suggestion, including disabled accounts. Atomic database uniqueness prevents concurrent registrations from creating duplicates or replacing an account.

```http
POST /api/auth/login
Content-Type: application/json

{"email":"admin@acme.example","password":"admin123"}
```

Success is `200` with `{token, expiresAt, user}`. The user object contains numeric `id`, `email`, `displayName`, and `role` (`ADMIN` or `USER`). The email is normalized before lookup; password hashes are never returned. Unknown email, wrong password, and disabled account have the same error response.

Development accounts are `admin@acme.example` / `admin123` and `user@acme.example` / `user123`. These are synthetic local accounts seeded by migration V2.

Send the token as `Authorization: Bearer <token>`. Tokens expire after eight hours by default. There is no refresh-token flow. Remember Me selects local storage instead of session storage; it does not extend token lifetime.

`GET /api/auth/me` returns the profile from verified JWT claims and restores a stored frontend session on reload. Roles/account changes in the database do not alter an already issued token; another login is needed to obtain updated claims. A 401 on a feature request clears the rejected frontend session and returns the application to login.

Unauthorized responses are associated with the bearer token sent on that request. A delayed 401 from a previous session cannot clear a newer login. Logout failures also leave a later login intact.

`POST /api/auth/logout` requires the current bearer token and returns an empty `204`. The frontend calls it and always removes its local token, even if the acknowledgement fails. The backend is stateless: logout does not revoke a captured JWT. That token remains usable until expiry. Server-side revocation, account disabling, and demotion are not added in this change.

The `users` package still has no repository. Authentication therefore retains its current JDBC lookup and registration insert; the conditional repository refactor remains pending Member 2's implementation.

## Granting admin access

Existing admins can open **Admin**, search accounts by name or email, select **Grant admin**, and review the named account before choosing **Confirm grant admin**. Cancel or Escape closes the confirmation without changing the account. Disabled accounts cannot be promoted.

`GET /api/admin/users` returns account summaries `{id,email,displayName,role,enabled}`, sorted by lowercase display name then ID. `POST /api/admin/users/{id}/promote` takes no body and returns `200` with the same summary and `role: ADMIN`. Both routes require an existing admin JWT; anonymous callers receive `401` and USER sessions receive `403`, regardless of the UI. The controller also enforces method authorization.

Promotion locks the account row within a transaction, sets only its role and update timestamp, and preserves credentials, enabled status, IDs and knowledge ownership. Repeated/concurrent requests are safe. Missing accounts return `404 NOT_FOUND`; disabled accounts return `409 CONFLICT`. Actual promotions log the acting and target user IDs. The target must sign out and sign in again to receive an ADMIN JWT; an existing USER token keeps its original permissions until replaced or expired. Account disabling and demotion remain outside this interface.

## Ingestion requests and responses

Manual note:

```http
POST /api/knowledge-items/manual
Authorization: Bearer <admin-token>
Content-Type: application/json

{"title":"Procurement policy","type":"Policy","text":"Purchases need approval."}
```

All three fields are required and must not be whitespace-only. Title and type are limited to 255 characters. Normalized UTF-8 text, including its final newline, must fit 1 MiB. Title/type are trimmed before storage.

Document upload:

```bash
curl -X POST http://localhost:8080/api/knowledge-items/upload \
  -H "Authorization: Bearer <admin-token>" \
  -F "title=Procurement policy" -F "type=Policy" -F "file=@policy.md"
```

Send exactly one `file` with `title` and `type`. Extensions are case-insensitive. Markdown input is limited to 1 MiB; DOCX input to 10 MiB. Converted/normalized document text still must fit 1 MiB. Empty content is rejected. The server chooses the multipart boundary; browser clients must not set a bare multipart Content-Type header themselves.

Both endpoints perform text ingestion synchronously and return `201` after the application record is created. The same knowledge-item schema is returned by creation, list entries, and retry. It includes status/stage/source, latest error fields, timestamps, and nullable source/transcription metadata. The exact fields and nullability are in OpenAPI. Document text is fetched separately through `/api/knowledge-items/{id}/content` for completed documents.

An engine outage can produce **HTTP 201 with `status: FAILED`**. Creation succeeded, but ingestion failed; inspect `status`, `lastErrorCode`, and `lastErrorMessage`. Do not interpret every 201 as successfully indexed content. On a successful retry, the error fields become null. `COMPLETED` means the engine stored the document; its asynchronous semantic-search projection may still be catching up.

`POST /api/knowledge-items/{id}/retry-ingestion` takes no body. Missing item returns 404; any status other than FAILED returns 409. Successful invocation returns `200` with the updated item and preserves prior attempt history. Text/DOCX retries are synchronous. Existing video retries can return PENDING while the background job runs; the video-upload endpoint itself continues returning 202.

## Manual note editing and query usage

Admin-only `GET /api/knowledge-items/{id}/manual` returns `{id,title,type,text,revision}` for manual notes, including notes whose ingestion failed. `PUT` to the same route accepts `{title,type,text,revision}` with the creation field and UTF-8 size limits. Only completed or failed manual notes are editable. Missing notes return 404; uploads, active ingestion, or stale revisions return 409 without overwriting content.

An edit atomically claims the note, increments its content revision, and invalidates cached answers before contacting the engine. The existing ingestion pipeline replaces the same engine item and records a new attempt. HTTP 200 with `status: FAILED` means the edit was saved but indexing failed; the latest text remains editable and retryable. Content timestamps stay stable on retries. Two editors cannot both claim the same revision.

Successful `/api/query/search` and `/api/query/synthesize` requests record usage in PostgreSQL. Synthesis returns `usage.estimated` alongside input/output counts, and cache hits return zero new tokens. Engine-reported counts are preferred. Missing counts use four UTF-16 characters per token, rounded up: the question plus unique full cited document bodies for input, the answer for output. This is a proxy for a prompt the backend does not observe, not exact provider billing.

`GET /api/usage` returns `{scope,queryCount,inputTokens,outputTokens,totalTokens,cacheHits,estimatedQueryCount}`. Users see their own records; administrators see workspace totals. Scope comes from the verified JWT, never a supplied user id. Tracking starts at V11. Failed/unreported provider calls, ingestion, embedding, and earlier activity are excluded. An accounting database failure is logged without failing an already completed query; totals may then be incomplete.

## Integration status and migration history

At the start of this change, remote `main` and `dylan-auth` pointed at `4d0b7f7`. `Adrian-dev` at `5b78f0f` and `frontend/ethan-ui` at `a0c03b4` were already ancestors of `dylan-auth`; neither contained additional commits to merge. No artificial merge commit is needed. Recheck remote tips before a later integration.

The merged tree had two Flyway V8 migrations. Integration preserves `V8__add_video_ingestion_metadata.sql` and moves the cache migration to `V10__create_query_cache.sql`. V9 entity tables do not depend on the cache table. The cache SQL itself is unchanged. V11 adds stable content timestamps and durable query usage; existing content timestamps are backfilled from creation time.

V12 adds a unique index on `lower(btrim(email))`, preserving existing account IDs, stored email spelling, hashes, and knowledge ownership. If existing accounts collide after normalization, the migration fails clearly and transactionally without merging or deleting accounts. An administrator must resolve those collisions explicitly before retrying; do not delete accounts or reset migration history automatically. Login uses the same normalized lookup.

Fresh databases apply these versions in order. An integration test upgrades an existing video-V8 database through V10 and verifies that notes, attempt history, and the global knowledge revision are preserved. Existing developer databases that recorded V8 as `create_query_cache` require their migration history and schema to be reconciled against a backup before using this sequence. Do not delete volumes or run Flyway repair blindly. Existing databases whose V8 is video metadata retain that migration's original version and checksum.

For the known history with query cache at V7 and video metadata at V8, use the guarded `scripts/reconcile-query-cache-history.sql` only after a verified backup and with the application stopped. It validates checksums and the cache schema before changing only the V7 history version/filename to V10. The SQL checksum and application/cache data are preserved. See the [launcher guide](../scripts/README.md#legacy-v7-query-cache-history) for execution and verification. Other legacy histories are deliberately rejected.

## Checks and CI

`.github/workflows/ci.yml` runs frontend install/format/lint/tests/build, Java 21 backend `check` with Docker/Testcontainers, and development Compose/launcher validation. Backend `check` retains the project's 100% line and branch coverage thresholds. Paid live-provider tests are excluded from routine CI.

Before review, run the commands in the root README. There is no configured duplication-check command. Account disabling, demotion, and document deletion remain outside these workflows.
