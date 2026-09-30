# gbrain adapter

This package is the only KnowledgeBridge code allowed to communicate with the external [gbrain](https://github.com/garrytan/gbrain) knowledge engine. Controllers, ingestion, query, entities, and administration services depend on application-owned interfaces in this package; they must not know about MCP, JSON-RPC, OAuth, or gbrain response shapes.

The repository currently builds unmodified gbrain revision `a6be012a3bcfac42e279630aedec5cda4a450e29`. That pinned revision, rather than the current upstream main branch, is the adapter contract until the container revision is deliberately upgraded and retested.

## Current constraints

- gbrain exposes Streamable HTTP MCP at `POST /mcp`, not the REST ingestion and query API assumed by the original project brief.
- `GET /health` is unauthenticated and confirms service/database health only. It does not prove that authentication, embeddings, or individual tools work.
- MCP calls require a bearer credential. gbrain supports OAuth 2.1, but a scoped, operator-provisioned bearer token is the simpler server-to-server mechanism for the MVP.
- The current container initializes with `--no-embedding`. This permits keyless startup but provides keyword-only retrieval. It is not sufficient for the KnowledgeBridge semantic-search requirement.
- The backend receives `GBRAIN_BASE_URL`, but no Java code currently consumes it.
- Remote `put_page` calls save and chunk content, but upstream deliberately skips automatic link and timeline extraction for untrusted MCP writers.
- gbrain page deletion is soft deletion. KnowledgeBridge must not claim that a permanent-delete operation physically erased engine data until that behavior is verified against the pinned revision.

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

Provision a dedicated gbrain source named `knowledgebridge` and a dedicated bearer token for the backend. Provisioning is an operator/setup responsibility, not an application startup side effect.

The runtime token should:

- be bound to the `knowledgebridge` source;
- have only `read` and `write` scopes;
- omit `admin`, `agent`, and `sources_admin` unless a separately reviewed feature requires them;
- be stored in `GBRAIN_ACCESS_TOKEN` and passed only to the backend;
- be rotatable without rebuilding the application; and
- never appear in logs, exception messages, actuator output, or API responses.

Administrative diagnostics should use a separate credential rather than expanding the normal application token. The adapter should call `whoami` during a deployment smoke test to verify the effective source and scopes.

## Proposed package design

Use Spring's synchronous `RestClient`, which is already available through the WebMVC stack. Do not add an MCP SDK unless direct JSON-RPC proves insufficient; the subset needed by KnowledgeBridge is small and keeping it explicit reduces dependency and protocol complexity.

```text
gbrain/
  GbrainClient.java                  application-facing interface
  GbrainProperties.java              validated URL, token, and timeout settings
  GbrainMcpClient.java               MCP/JSON-RPC transport implementation
  GbrainHealthClient.java            unauthenticated health probe
  GbrainException.java               stable adapter exception hierarchy
  GbrainErrorCode.java               UNAVAILABLE, UNAUTHORIZED, RATE_LIMITED, ...
  model/                              application-owned adapter results
  mcp/                                JSON-RPC and MCP wire records only
```

`GbrainClient` should expose intent-oriented operations rather than a generic public `callTool` method:

```java
GbrainWriteResult upsertDocument(GbrainDocument document);
void deleteDocument(String externalId);
List<GbrainSearchHit> search(GbrainSearchRequest request);
GbrainSynthesisResult synthesize(GbrainSynthesisRequest request);
Optional<GbrainDocument> getDocument(String externalId);
GbrainCapabilities capabilities();
```

Keep the low-level generic tool call private to `GbrainMcpClient`. This prevents gbrain tool names and arbitrary JSON from spreading into service code.

## MCP transport behavior

The transport communicates with `${GBRAIN_BASE_URL}/mcp` using JSON-RPC 2.0 and `Authorization: Bearer ...`.

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
knowledgebridge.gbrain.access-token=${GBRAIN_ACCESS_TOKEN:}
knowledgebridge.gbrain.connect-timeout=${GBRAIN_CONNECT_TIMEOUT:PT2S}
knowledgebridge.gbrain.read-timeout=${GBRAIN_READ_TIMEOUT:PT30S}
knowledgebridge.gbrain.enabled=${GBRAIN_ENABLED:true}
```

Long-running synthesis should receive a separate, larger timeout rather than increasing every adapter call. Startup should not fail solely because gbrain is temporarily unavailable, but readiness and admin diagnostics must report the degraded state accurately.

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

- Enable and verify OpenRouter embeddings.
- Provision the `knowledgebridge` source and scoped runtime token.
- Capture sanitized `initialize`, `tools/list`, `put_page`, `search`, and `synthesize` fixtures from the pinned server.
- Resolve the permanent-deletion and entity-API gaps.

Exit criterion: a scripted MCP smoke test can write and semantically retrieve one synthetic page.

### Phase 2: transport foundation

- Implement properties, JSON-RPC records, `RestClient` transport, capability discovery, typed errors, timeouts, and logging.
- Add unit and component tests before connecting application services.

Exit criterion: all transport behavior is deterministic against fixtures and the mock server.

### Phase 3: ingestion integration

- Implement deterministic document rendering and `put_page`/read/delete/restore mappings.
- Connect `IngestionService` while keeping status transitions and retry decisions outside the adapter.
- Store the external slug only after a confirmed or reconciled write.

Exit criterion: manual creation and Markdown upload reach gbrain, with visible completed/failed status and safe retry behavior.

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
