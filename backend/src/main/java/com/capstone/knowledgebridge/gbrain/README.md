# gbrain adapter

This package is the only KnowledgeBridge code allowed to communicate with the external [gbrain](https://github.com/garrytan/gbrain) knowledge engine. Controllers, ingestion, query, entities, and administration services depend on application-owned interfaces in this package; they must not know about MCP, JSON-RPC, OAuth, or gbrain response shapes.

The repository currently builds unmodified gbrain revision `a6be012a3bcfac42e279630aedec5cda4a450e29`. That pinned revision, rather than the current upstream main branch, is the adapter contract until the container revision is deliberately upgraded and retested.

## Current constraints

- gbrain exposes Streamable HTTP MCP at `POST /mcp`, not the REST ingestion and query API assumed by the original project brief.
- `GET /health` is unauthenticated and confirms service/database health only. It does not prove that authentication, embeddings, or individual tools work.
- MCP calls require a bearer credential. The Phase 1 spike showed that legacy tokens cannot be source-bound, so the backend uses an operator-provisioned OAuth client-credentials grant and short-lived access tokens.
- The container now initializes with an explicit OpenRouter embedding model and dimension. Existing installations still require a deliberate migration when their stored vector width differs.
- The Phase 2 transport consumes `knowledgebridge.gbrain.*` (including `GBRAIN_BASE_URL`), but no application service calls it yet.
- Remote `put_page` calls save and chunk content, but upstream deliberately skips automatic link and timeline extraction for untrusted MCP writers.
- gbrain page deletion is soft deletion. The pinned source shows gbrain's autopilot purge phase hard-deletes soft-deleted pages after a 72-hour recovery window, so restore is possible only within that window. KnowledgeBridge must not claim that a permanent-delete operation physically erased engine data until that purge is verified against the running deployment.
- `put_page` on a soft-deleted page clears `deleted_at`, so writing a deleted item revives it in search.

## Required deployment changes

Semantic and hybrid search require embeddings to be enabled before the adapter is considered ready.

For this project, use the existing `OPENROUTER_API_KEY` with an explicitly pinned embedding model:

```text
openrouter:openai/text-embedding-3-small
dimensions: 1536
```

The model and dimensions are part of the stored vector schema and must not change implicitly. A provider or dimension change requires gbrain's supported embedding migration process, not a normal configuration edit.

Deployment work must:

1. Pass `OPENROUTER_API_KEY` to the gbrain container, not to the browser. The Spring backend does not need the provider key when gbrain owns embedding and synthesis calls.
2. Replace the clean-install `--no-embedding` initialization with explicit OpenRouter embedding configuration.
3. For an existing gbrain volume, run the upstream migration/status workflow. Preview migration cost and scope before approving a re-embedding operation.
4. Run the provider smoke test and verify the returned vector width before ingesting the corpus.
5. Backfill any pages created while embeddings were disabled.
6. Keep the gbrain revision, model id, and vector dimensions documented together so upgrades cannot silently mix incompatible embedding spaces.

The deployment spike should validate the equivalent of these upstream commands inside the container:

```sh
gbrain providers test --model openrouter:openai/text-embedding-3-small
gbrain init --force --embedding-model openrouter:openai/text-embedding-3-small --embedding-dimensions 1536
```

For a non-empty existing brain, use the migration workflow instead of forcing initialization:

```sh
gbrain migrate embeddings --to openrouter:openai/text-embedding-3-small --dim 1536 --dry-run
gbrain migrate embeddings --to openrouter:openai/text-embedding-3-small --dim 1536 --yes
```

The first two commands describe clean-install/provider verification. The migration commands are the safe path once indexed content exists; do not run both initialization and migration blindly against the same volume.

Embedding readiness must be checked independently of `/health`. The release smoke test must write a unique page, search for a semantic paraphrase, confirm that vector retrieval was enabled, and remove the test page.

## Authentication and source isolation

Provision a dedicated gbrain source named `knowledgebridge` and a dedicated OAuth client for the backend. Provisioning is an operator/setup responsibility, not an application startup side effect. Run `scripts/provision-gbrain.ps1` after the source exists; it writes the one-time client secret only to the ignored root `.env`.

The OAuth client should:

- be bound to the `knowledgebridge` source;
- have only `read` and `write` scopes;
- omit `admin`, `agent`, and `sources_admin` unless a separately reviewed feature requires them;
- use the client-credentials grant and obtain short-lived access tokens from `/token`;
- store `GBRAIN_OAUTH_CLIENT_ID` and `GBRAIN_OAUTH_CLIENT_SECRET` only in `.env` or a deployment secret store;
- be rotatable without rebuilding the application; and
- never appear in logs, exception messages, actuator output, or API responses.

Administrative diagnostics should use a separate credential rather than expanding the normal application token. The adapter should call `whoami` during a deployment smoke test to verify the effective source and scopes.

## Package design

Use Spring's synchronous `RestClient`, which is already available through the WebMVC stack. Do not add an MCP SDK unless direct JSON-RPC proves insufficient; the subset needed by KnowledgeBridge is small and keeping it explicit reduces dependency and protocol complexity.

```text
gbrain/
  GbrainClient.java                  application-facing interface
  McpGbrainClient.java               GbrainClient over MCP: put/get/delete/restore page mappings
  InMemoryGbrainClient.java          GbrainClient stand-in for GBRAIN_MODE=in-memory
  GbrainPages.java                   slug mapping and deterministic Markdown/frontmatter rendering
  GbrainConfiguration.java           bean wiring; nothing contacts gbrain at startup
  GbrainProperties.java              validated URL, credential, size, retry, and timeout settings
  GbrainTokenProvider.java           cached OAuth client-credentials tokens
  GbrainMcpClient.java               MCP/JSON-RPC transport, capability discovery, retry policy
  GbrainHealthClient.java            unauthenticated health probe
  GbrainHttpSupport.java             timeouts, bounded bodies, HTTP/transport error classification
  GbrainTool.java                    required tool names with retry and timeout policy
  GbrainException.java               single adapter exception carrying a GbrainErrorCode
  GbrainErrorCode.java               UNAVAILABLE, UNAUTHORIZED, RATE_LIMITED, ...
  model/                              application-owned adapter results
  mcp/                                JSON-RPC and MCP wire records and the SSE response reader
```

`GbrainClient` exposes intent-oriented operations rather than a generic public `callTool` method. The ingestion operations exist now; search and synthesis are added in Phase 4:

```java
GbrainWriteResult upsertDocument(GbrainDocument document);
Optional<GbrainStoredDocument> getDocument(String itemKey);
GbrainDocumentState documentState(GbrainDocument document);
GbrainDeleteResult deleteDocument(String itemKey);
GbrainRestoreResult restoreDocument(String itemKey);
// Phase 4
List<GbrainSearchHit> search(GbrainSearchRequest request);
GbrainSynthesisResult synthesize(GbrainSynthesisRequest request);
```

Capability discovery stays on `GbrainMcpClient.discoverCapabilities()` because it describes the MCP deployment, not the document contract.

Keep the low-level generic tool call private to `GbrainMcpClient`. This prevents gbrain tool names and arbitrary JSON from spreading into service code.

## MCP transport behavior

The transport obtains an access token from `GBRAIN_OAUTH_TOKEN_URL`, then communicates with `${GBRAIN_BASE_URL}/mcp` using JSON-RPC 2.0 and `Authorization: Bearer ...`. The MCP request must accept both `application/json` and `text/event-stream`; the pinned server returns SSE-framed JSON-RPC responses.

Implementation requirements:

1. Perform `initialize` using the protocol version supported by the pinned server and validate the returned server name/version.
2. Send `notifications/initialized` when required by the negotiated flow.
3. Call `tools/list` during a startup smoke test or explicit capability refresh. Do not run it before every request.
4. Invoke tools through `tools/call` with a unique JSON-RPC id.
5. Treat HTTP success and MCP success separately. A `200` response may still contain a JSON-RPC error or a tool result with `isError: true`.
6. Preserve response metadata needed to prove whether vector retrieval, query expansion, or fallback behavior occurred.
7. Set bounded connection and request timeouts. Do not retry writes automatically after an ambiguous timeout.
8. Retry only clearly safe reads and explicit `429`/transient failures, with a small capped backoff and respect for `Retry-After`.
9. Limit response sizes and reject malformed or unexpected payloads.
10. Generate a correlation id for logs without logging document content, questions, answers, tokens, or authorization headers.

The adapter should fail closed if required tools are absent. Capability discovery is especially important during a gbrain upgrade because the published tool surface is filtered by token scope and server configuration.

## Tool mapping

The first implementation should map only the tools needed for the MVP:

| KnowledgeBridge operation | gbrain tool | Notes |
|---|---|---|
| Create or replace indexed content | `put_page` | Sends a complete Markdown document; replacement is not a patch. |
| Read indexed content | `get_page` | Use exact slugs and request canonical content only when needed. |
| Search mode | `search` | Cheap hybrid retrieval without LLM query expansion. |
| Broader retrieval | `query` | Optional later path when expansion and additional retrieval controls are justified. |
| Synthesis mode | `synthesize` | Expensive LLM-backed operation; preserve citations and gap/confidence information. |
| Soft-delete indexed content | `delete_page` | Verify source scoping and idempotency. |
| Restore content | `restore_page` | Needed if KnowledgeBridge exposes restore after soft deletion. |
| Runtime identity check | `whoami` | Deployment smoke test, not a per-request call. |
| Engine diagnostics | `get_health` | Optional admin credential; distinct from unauthenticated `/health`. |

Do not use `log_ingest` as the ingestion operation; it records an ingestion event but does not index the document.

Entity support needs a separate spike. The pinned tool catalog provides extraction and entity-related operations, but it does not directly match the proposed KnowledgeBridge entity-list/detail API. The spike must determine whether entity pages, `extract_entities`, search filters, or graph operations provide a stable mapping before entity endpoints are promised.

## Document identity and content mapping

Use a deterministic gbrain slug derived from the immutable KnowledgeBridge knowledge-item UUID, for example:

```text
knowledgebridge/<knowledge-item-uuid>
```

Store the slug, source id, and pinned engine revision with the knowledge item. Do not use the editable title or uploaded filename as identity.

Render a complete Markdown payload for `put_page` with controlled YAML frontmatter containing only approved metadata, such as:

- KnowledgeBridge item UUID;
- title;
- document type;
- owner identifier;
- application revision/version;
- original creation and update timestamps; and
- soft-deletion state when relevant.

Escape frontmatter values safely and reject user-supplied fields that could override identity, visibility, source, or engine control metadata. The body must be normalized deterministically so retries send the same content.

As implemented, `GbrainPages.render` writes exactly these frontmatter keys: `title`, `type`, `knowledgebridge_id`, `knowledgebridge_owner`, `knowledgebridge_revision`, `knowledgebridge_created_at`, `knowledgebridge_updated_at`, and `knowledgebridge_digest`. All strings are YAML double-quoted, and `GbrainDocument` rejects control and line-separator characters in them. The key `id` is deliberately never written because gbrain deduplicates writes on `frontmatter.id`. Any frontmatter inside an uploaded file stays in the body as text, so it cannot set `id`, `visibility`, quarantine, or embedding controls.

The page `type` is the application document type with a `knowledgebridge_` prefix, for example `knowledgebridge_policy`. gbrain page types are not inert. In the pinned base schema, `person` and `company` are entities with expert routing, `note` and `meeting` are facts-extractable, `meeting` changes link verbs, and `person` gets a surname boost in retrieval. Namespacing keeps an application type such as `meeting` from selecting that behavior. gbrain stores undeclared types literally and only records an advisory `put_page:unknown_type` schema event. Phase 4 type filters must use the prefixed value. `getDocument` strips the prefix again.

`knowledgebridge_digest` is the SHA-256 of every indexed field. `getDocument` reads it back, and `documentState` compares it with the expected document to report `CURRENT`, `STALE`, `MISSING`, or `DELETED`.

## Error and retry contract

Translate transport and MCP failures into a small application-owned taxonomy:

- `CONFIGURATION` — missing/invalid URL, token, model, or required capability;
- `UNAUTHORIZED` — invalid token or insufficient scope;
- `NOT_FOUND` — missing page when the operation requires one;
- `VALIDATION` — rejected slug, content, or tool arguments;
- `RATE_LIMITED` — HTTP 429 or equivalent tool error;
- `TIMEOUT` — connection or response timeout with an unknown write outcome;
- `UNAVAILABLE` — health failure, connection refusal, or transient upstream failure;
- `PROTOCOL` — malformed JSON-RPC/MCP response or unsupported contract; and
- `ENGINE` — a valid gbrain error not represented above.

Ingestion owns workflow state. The adapter returns results or throws typed exceptions; it does not update `knowledge_item` or `ingestion_attempt` directly.

Write retries require special care because a timeout can occur after gbrain committed the page. `put_page` is replacement-oriented and uses a deterministic slug, so the ingestion service may reconcile with `get_page` and retry deliberately. The HTTP client itself must not blindly retry it. Delete and restore should follow the same reconcile-before-retry rule.

## Configuration

Add validated backend properties with environment overrides:

```properties
knowledgebridge.gbrain.base-url=${GBRAIN_BASE_URL:http://localhost:3131}
knowledgebridge.gbrain.oauth-client-id=${GBRAIN_OAUTH_CLIENT_ID:}
knowledgebridge.gbrain.oauth-client-secret=${GBRAIN_OAUTH_CLIENT_SECRET:}
knowledgebridge.gbrain.oauth-token-url=${GBRAIN_OAUTH_TOKEN_URL:http://localhost:3131/token}
knowledgebridge.gbrain.connect-timeout=${GBRAIN_CONNECT_TIMEOUT:PT2S}
knowledgebridge.gbrain.read-timeout=${GBRAIN_READ_TIMEOUT:PT30S}
knowledgebridge.gbrain.synthesis-timeout=${GBRAIN_SYNTHESIS_TIMEOUT:PT120S}
knowledgebridge.gbrain.max-response-size=${GBRAIN_MAX_RESPONSE_SIZE:4MB}
knowledgebridge.gbrain.retry-max-attempts=${GBRAIN_RETRY_MAX_ATTEMPTS:3}
knowledgebridge.gbrain.retry-max-backoff=${GBRAIN_RETRY_MAX_BACKOFF:PT5S}
knowledgebridge.gbrain.enabled=${GBRAIN_ENABLED:true}
```

Long-running synthesis receives a separate, larger timeout rather than increasing every adapter call. Startup should not fail solely because gbrain is temporarily unavailable, but readiness and admin diagnostics must report the degraded state accurately.

## Testing strategy

### Unit tests

- configuration validation and URL construction;
- bearer header insertion without secret leakage;
- JSON-RPC id correlation;
- success, JSON-RPC error, and `isError` tool envelopes;
- malformed and oversized responses;
- error classification for 401, 403, 404, 413, 429, 5xx, and timeouts;
- document-to-Markdown/frontmatter rendering;
- stable UUID-to-slug mapping; and
- citation and search-hit normalization.

### Component tests

Use a local mock HTTP server to exercise the real `RestClient` transport:

- initialize and capability discovery;
- tool visibility under insufficient scopes;
- successful `put_page`, `search`, `synthesize`, and delete calls;
- delayed responses and ambiguous write timeouts;
- rate limiting with `Retry-After`; and
- prevention of automatic write retries.

### Live integration tests

Keep live gbrain tests separately tagged because they require Docker and provider credentials. They must:

1. start the pinned gbrain image and PostgreSQL;
2. verify the scoped runtime identity;
3. confirm the configured embedding provider and dimension;
4. write a uniquely named synthetic page;
5. retrieve it with a semantic paraphrase rather than an exact phrase;
6. verify citations/source identifiers;
7. update the same page and confirm replacement behavior;
8. delete and restore it; and
9. clean up without touching unrelated pages.

Mock tests may prove adapter behavior, but only the live test may be reported as evidence that gbrain and embeddings work end to end.

## Delivery sequence

### Phase 1: deployment and protocol spike

- [x] Enable OpenRouter embeddings at 1536 dimensions and verify a paraphrased semantic search with `vector_enabled: true`.
- [x] Provision the isolated `knowledgebridge` source and a source-/slug-bound OAuth client with `read write` scopes.
- [x] Capture sanitized `initialize`, `tools/list`, `put_page`, `search`, and `synthesize` fixtures from the pinned server.
- [x] Add repeatable provisioning and live MCP smoke-test scripts.
- [x] Resolve the permanent-deletion and entity-API gaps for MVP planning.

Exit criterion met on September 30, 2026: `scripts/test-gbrain-mcp.ps1` wrote a synthetic page, retrieved it with a semantic paraphrase, synthesized a cited answer, and soft-deleted the page.

Phase 1 findings:

- The pinned server negotiates MCP protocol `2025-03-26` and returns JSON-RPC inside `text/event-stream` responses.
- The live scoped client saw 83 tools. The required ingestion/search/synthesis/delete/restore tools were present.
- `search` returned retrieval metadata with `vector_enabled: true`, `expansion_applied: false`, and no degraded reasons.
- `synthesize` requires an explicit `models.think` route. OpenRouter embeddings alone do not override its Anthropic-direct fallback; the deployment pins `openrouter:anthropic/claude-haiku-4.5`.
- `delete_page` is recoverable soft deletion and `restore_page` reverses it. The scoped runtime surface has no safe per-page physical purge; gbrain's autopilot purges all soft-deleted pages after 72 hours, which is not per-item and has not been verified live (Phase 3 source review). Therefore the proposed permanent-delete API is excluded from the MVP unless an operator-reviewed retention/purge design is added.
- The slug-bound client cannot call `extract_entities`, and the published read tools do not provide the proposed general entity list/detail contract. Entity endpoints are excluded from the first adapter increment; a later design may use application-owned entities or a separately scoped extraction workflow.

### Phase 2: transport foundation

- [x] Implement properties, JSON-RPC records, `RestClient` transport, capability discovery, typed errors, timeouts, and logging.
- [x] Add unit and component tests before connecting application services.

Exit criterion met on October 1, 2026: transport behavior is covered deterministically by the Phase 1 fixtures and a local JDK `HttpServer` mock of gbrain (`MockGbrainServer`), with 100% line and branch coverage. No live gbrain call was made in this phase.

Phase 2 decisions:

- `GbrainMcpClient` initializes lazily, validates protocol `2025-03-26`, server name `gbrain`, and pinned server version `0.50.0.0` (a different version fails with `CONFIGURATION` until the upgrade is retested), then sends `notifications/initialized`. The pinned server is stateless, so no `Mcp-Session-Id` handling is needed.
- `tools/list` (with cursor pagination) runs on the first tool call or an explicit `discoverCapabilities()` and is cached. A tool missing from the discovered surface fails closed with `CONFIGURATION` before any request is sent.
- The generic `callTool` is package-private and accepts only the `GbrainTool` enum, so tool names and raw JSON stay inside the package.
- Retry policy: an HTTP 401 refreshes the token once for any call because gbrain rejects it before dispatch. Otherwise only `whoami`, `get_page`, `search`, `initialize`, and `tools/list` retry `RATE_LIMITED`, `UNAVAILABLE`, and `TIMEOUT`, with 250 ms doubling backoff, `Retry-After` delta-seconds, and a cap; a longer `Retry-After` fails immediately. Writes and `synthesize` are never retried by the transport.
- The JDK `HttpClient` is used under `RestClient` because it never silently re-sends a POST; redirects are disabled so bearer tokens cannot be forwarded.
- HTTP 404 from `/mcp` is `CONFIGURATION` (wrong URL or deployment), not `NOT_FOUND`. Tool `isError` envelopes are classified from gbrain's `error` field (`page_not_found` → `NOT_FOUND`, `invalid_params` → `VALIDATION`, `permission_denied` → `UNAUTHORIZED`, and so on); unknown codes are `ENGINE`.
- Exceptions and logs never contain tokens, the client secret, request or response bodies, or server-supplied error messages. `GbrainException.upstreamCode()` exposes only gbrain's machine-readable error code.
- Readiness/actuator integration of `GbrainHealthClient` is deferred to the admin diagnostics work.

### Phase 3: ingestion adapter

- [x] Implement deterministic document rendering and `put_page`/read/delete/restore mappings.
- [x] Provide the `GbrainClient` ingestion contract, reconciliation support, and an in-memory stand-in so ingestion can be built and tested without gbrain.
- [x] Verify the page lifecycle against the live pinned server and test against the captured fixtures.

Exit criterion met on October 1, 2026: the adapter exposes a stable ingestion contract, keeps status transitions and retry decisions outside the adapter, and its `put_page`/`get_page`/`delete_page`/`restore_page` mappings are verified live.

### Ingestion service integration (knowledge-items/ingestion work)

This work was split out of Phase 3 because it belongs to the knowledge-items/ingestion owners and depends on their `knowledge_item` schema. It is not complete.

- [ ] Connect `IngestionService` to `GbrainClient`, following the hand-off below.
- [ ] Store the external slug only after a confirmed or reconciled write.

Exit criterion: manual creation and Markdown upload reach gbrain, with visible completed/failed status and safe retry behavior.

Adapter status (October 1, 2026): the `GbrainClient` ingestion contract, `McpGbrainClient`, and `InMemoryGbrainClient` are complete and covered by mock-server tests. `put_page` responses come from the Phase 1 live fixture. The `get_page`, `delete_page`, and `restore_page` responses were captured live on October 1, 2026 against the pinned server with `scripts/test-gbrain-mcp.sh --capture-fixtures`. That run passed with 83 tools, `vector_enabled: true`, an `ok` cited synthesis, and every lifecycle status. `McpGbrainClientTests` uses these fixtures: `get-page-response`, `get-page-deleted-response`, `get-page-not-found-response`, `delete-page-response`, `delete-page-already-deleted-response`, `restore-page-response`, and `restore-page-already-active-response`. The capture confirmed the source-derived shapes, with one detail: gbrain lifts `title` and `type` out of `frontmatter` into top-level fields. A NOT_FOUND from delete or restore was not captured separately; it uses the same `page_not_found` envelope as `get_page`.

Ingestion hand-off:

1. Build a `GbrainDocument` from the knowledge item. `itemKey` must be immutable, unique across database resets, and 1–64 lower-case letters, digits, or hyphens. A UUID column on `knowledge_item` is recommended over the `BIGSERIAL` id: a recreated database would reuse ids and overwrite pages that the persisted gbrain volume still holds.
2. Call `upsertDocument`. On success, store `GbrainWriteResult.externalId()` and mark the attempt completed. `UNCHANGED` is also a success.
3. On `GbrainException`, record `code()` (and `upstreamCode()` when present) as the attempt's safe error. `VALIDATION` is permanent until the content changes. `RATE_LIMITED`, `UNAVAILABLE`, and `UNAUTHORIZED` can be retried later.
4. On `TIMEOUT` the write may have committed. Call `documentState(document)` before any retry. `CURRENT` means the write succeeded: store the slug and do not write again. `STALE` or `MISSING` means a deliberate retry is needed.
5. Never upsert a soft-deleted item. gbrain would revive it. Restore it first with `restoreDocument`, and treat `NOT_FOUND` from restore as "purged; re-ingest".
6. Set `GBRAIN_MODE=in-memory` to run the application without gbrain. Writes then succeed locally, but nothing becomes searchable.

### Phase 4: retrieval and synthesis

- Implement `search` normalization first.
- Add `synthesize` with citations, AI-generated labeling, gap signals, cost/usage hooks, and its own timeout.
- Add `query` only if measured retrieval quality justifies its extra expansion cost.

Exit criterion: the synthetic evaluation questions produce inspectable ranked results and cited answers through the Spring API.

### Phase 5: hardening

- Add live Docker integration coverage and upgrade compatibility fixtures.
- Validate token rotation, provider outage, rate limiting, malformed responses, and embedding degradation.
- Document the pinned contract and rerun the compatibility suite before every gbrain revision update.

## Definition of done

The adapter is complete for the MVP when:

- no package outside `gbrain` imports MCP wire types or calls gbrain directly;
- semantic retrieval is demonstrably enabled, not inferred from `/health`;
- runtime access is source-bound and least-privileged;
- ingestion, search, synthesis, delete, and restore have stable application-owned contracts;
- write timeouts cannot cause uncontrolled duplicate retries;
- citations and source identity survive normalization;
- secrets and document content are absent from logs;
- mock tests cover every error class;
- a tagged live test passes against the pinned container revision; and
- known gaps, especially physical deletion and entity mapping, are resolved or explicitly excluded from the MVP.
