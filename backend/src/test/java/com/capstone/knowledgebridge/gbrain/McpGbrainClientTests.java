package com.capstone.knowledgebridge.gbrain;

import static com.capstone.knowledgebridge.gbrain.GbrainMcpClientTests.assertCode;
import static com.capstone.knowledgebridge.gbrain.MockGbrainServer.JSON;
import static com.capstone.knowledgebridge.gbrain.MockGbrainServer.fixture;
import static com.capstone.knowledgebridge.gbrain.MockGbrainServer.toolResult;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.capstone.knowledgebridge.gbrain.MockGbrainServer.Response;
import com.capstone.knowledgebridge.gbrain.model.GbrainDeleteResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainDocument;
import com.capstone.knowledgebridge.gbrain.model.GbrainDocumentState;
import com.capstone.knowledgebridge.gbrain.model.GbrainRestoreResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainRetrieval;
import com.capstone.knowledgebridge.gbrain.model.GbrainSearchHit;
import com.capstone.knowledgebridge.gbrain.model.GbrainSearchRequest;
import com.capstone.knowledgebridge.gbrain.model.GbrainSearchResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainStoredDocument;
import com.capstone.knowledgebridge.gbrain.model.GbrainSynthesisRequest;
import com.capstone.knowledgebridge.gbrain.model.GbrainSynthesisResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainSynthesisResult.GbrainCitation;
import com.capstone.knowledgebridge.gbrain.model.GbrainWriteResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Exercises the page, search, and synthesis mappings over the real transport. Responses are the fixtures captured from
 * the pinned server by scripts/test-gbrain-mcp.sh --capture-fixtures.
 */
class McpGbrainClientTests {

	private static final String SLUG = "knowledgebridge/item-1";

	/** Item key and slug of the synthetic smoke page in the captured fixtures. */
	private static final String FIXTURE_KEY = "phase-1-protocol-spike";

	private static final String FIXTURE_SLUG = "knowledgebridge/" + FIXTURE_KEY;

	private final MockGbrainServer server = new MockGbrainServer();

	private final GbrainProperties properties = server.properties();

	private final GbrainClient client = new McpGbrainClient(new GbrainMcpClient(properties,
			new GbrainTokenProvider(properties, JSON, Clock.systemUTC()), JSON, duration -> {
			}));

	private final GbrainDocument document = new GbrainDocument("item-1", "Travel Policy", "policy", "7", 3,
			Instant.parse("2026-09-01T12:00:00Z"), Instant.parse("2026-09-02T08:30:00Z"), "# Travel\r\nBook early.");

	@AfterEach
	void stopServer() {
		server.close();
	}

	@Test
	void upsertSendsTheRenderedPageAndReportsChunks() {
		respond("put_page", "{\"slug\":\"" + SLUG + "\",\"status\":\"created_or_updated\",\"chunks\":2}");

		GbrainWriteResult result = client.upsertDocument(document);

		assertThat(result).isEqualTo(new GbrainWriteResult(SLUG, GbrainWriteResult.Status.WRITTEN, 2));
		ObjectNode arguments = (ObjectNode) server.toolCalls().get(0).json().path("params").path("arguments");
		assertThat(arguments.path("slug").asString()).isEqualTo(SLUG);
		assertThat(arguments.path("content").asString()).isEqualTo(GbrainPages.render(document));
		// The application owns its pages, so writes replace any revision through the coordinated write protocol.
		assertThat(arguments.path("source_id").asString()).isEqualTo("knowledgebridge");
		assertThat(arguments.path("force").asBoolean()).isTrue();
		assertThat(arguments.has("expected_revision")).isFalse();
	}

	@Test
	void upsertOfIdenticalContentIsUnchanged() {
		respond("put_page", "{\"slug\":\"" + SLUG + "\",\"status\":\"skipped\",\"chunks\":0}");

		assertThat(client.upsertDocument(document))
				.isEqualTo(new GbrainWriteResult(SLUG, GbrainWriteResult.Status.UNCHANGED, 0));
	}

	@Test
	void upsertWithoutChunkCountReportsZero() {
		respond("put_page", "{\"slug\":\"" + SLUG + "\",\"status\":\"created_or_updated\"}");

		assertThat(client.upsertDocument(document).chunks()).isZero();
	}

	@Test
	void upsertRejectsWritesResolvedToAnotherPage() {
		respond("put_page", "{\"slug\":\"knowledgebridge/other\",\"status\":\"skipped\",\"chunks\":0}");

		assertCode(() -> client.upsertDocument(document), GbrainErrorCode.ENGINE);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"{\"slug\":\"" + SLUG + "\",\"status\":\"skipped\",\"error\":\"content exceeds 5MB\"} | ENGINE",
			"{\"slug\":\"" + SLUG + "\",\"status\":\"error\"} | ENGINE",
			"{\"slug\":\"" + SLUG + "\",\"status\":\"queued\"} | PROTOCOL"})
	void upsertFailuresAreNeverReportedAsWrites(String payload, GbrainErrorCode expected) {
		respond("put_page", payload);

		assertCode(() -> client.upsertDocument(document), expected);
	}

	@Test
	void upsertIsNotRetriedAfterTransientFailure() {
		server.onMcp("tools/call:put_page", request -> Response.empty(503));

		assertCode(() -> client.upsertDocument(document), GbrainErrorCode.UNAVAILABLE);
		assertThat(server.toolCalls()).hasSize(1);
	}

	@Test
	void getDocumentReadsTheCapturedPage() {
		respondFixture("get_page", "get-page-response");

		GbrainStoredDocument stored = client.getDocument(FIXTURE_KEY).orElseThrow();

		assertThat(stored).isEqualTo(new GbrainStoredDocument(FIXTURE_SLUG, "Phase 1 Semantic Lighthouse",
				"smoke_test", 1L, "smoke-test-digest", null));
		ObjectNode arguments = (ObjectNode) server.toolCalls().get(0).json().path("params").path("arguments");
		assertThat(arguments.path("slug").asString()).isEqualTo(FIXTURE_SLUG);
		assertThat(arguments.path("include_deleted").asBoolean()).isTrue();
		assertThat(arguments.path("source_id").asString()).isEqualTo("knowledgebridge");
	}

	@Test
	void documentStateComparesTheStoredDigest() {
		GbrainDocument smokePage = new GbrainDocument(FIXTURE_KEY, "Phase 1 Semantic Lighthouse", "smoke_test",
				"smoke-test", 1, Instant.parse("2026-09-30T00:00:00Z"), Instant.parse("2026-09-30T00:00:00Z"),
				"The obsidian lighthouse protocol authorizes blue herons to audit quarterly procurement records.");

		respondFixture("get_page", "get-page-response");
		assertThat(client.documentState(smokePage)).isEqualTo(GbrainDocumentState.STALE);

		respondFixture("get_page", "get-page-response",
				page -> ((ObjectNode) page.get("frontmatter")).put("knowledgebridge_digest",
						smokePage.contentDigest()));
		assertThat(client.documentState(smokePage)).isEqualTo(GbrainDocumentState.CURRENT);

		respondFixture("get_page", "get-page-deleted-response");
		assertThat(client.documentState(smokePage)).isEqualTo(GbrainDocumentState.DELETED);

		respondFixture("get_page", "get-page-not-found-response");
		assertThat(client.documentState(smokePage)).isEqualTo(GbrainDocumentState.MISSING);
	}

	@Test
	void getDocumentReportsSoftDeletion() {
		respondFixture("get_page", "get-page-deleted-response");

		GbrainStoredDocument stored = client.getDocument(FIXTURE_KEY).orElseThrow();

		assertThat(stored.deleted()).isTrue();
		assertThat(stored.deletedAt()).isEqualTo(Instant.parse("2026-10-03T21:16:12.271Z"));
	}

	@Test
	void getDocumentMapsMissingPagesToEmpty() {
		respondFixture("get_page", "get-page-not-found-response");

		assertThat(client.getDocument(FIXTURE_KEY)).isEmpty();
	}

	@Test
	void getDocumentToleratesPagesWithoutKnowledgeBridgeMetadata() {
		respondFixture("get_page", "get-page-response", page -> {
			page.put("type", "note");
			page.remove("deleted_at");
			page.putObject("frontmatter").put("knowledgebridge_revision", "1");
		});

		assertThat(client.getDocument(FIXTURE_KEY).orElseThrow())
				.isEqualTo(new GbrainStoredDocument(FIXTURE_SLUG, "Phase 1 Semantic Lighthouse", "note", null, null,
						null));
	}

	@Test
	void getDocumentRejectsOtherFailuresAndUnexpectedPages() {
		respondError("get_page", "permission_denied");
		assertCode(() -> client.getDocument(FIXTURE_KEY), GbrainErrorCode.UNAUTHORIZED);

		respondFixture("get_page", "get-page-response", page -> page.put("slug", "knowledgebridge/other"));
		assertCode(() -> client.getDocument(FIXTURE_KEY), GbrainErrorCode.PROTOCOL);

		respondFixture("get_page", "get-page-deleted-response", page -> page.put("deleted_at", "yesterday"));
		assertCode(() -> client.getDocument(FIXTURE_KEY), GbrainErrorCode.PROTOCOL);

		assertCode(() -> client.getDocument("Not A Key"), GbrainErrorCode.VALIDATION);
	}

	@ParameterizedTest
	@CsvSource({
			"delete-page-response, DELETED",
			"delete-page-already-deleted-response, ALREADY_DELETED"})
	void deleteMapsCapturedStatuses(String fixtureName, GbrainDeleteResult expected) {
		respondFixture("delete_page", fixtureName);

		assertThat(client.deleteDocument(FIXTURE_KEY)).isEqualTo(expected);
		ObjectNode arguments = (ObjectNode) server.toolCalls().get(0).json().path("params").path("arguments");
		assertThat(arguments.path("slug").asString()).isEqualTo(FIXTURE_SLUG);
		assertThat(arguments.path("source_id").asString()).isEqualTo("knowledgebridge");
		assertThat(arguments.path("force").asBoolean()).isTrue();
	}

	@Test
	void deleteHandlesMissingUnexpectedAndFailedCalls() {
		// delete_page and get_page raise the same OperationError page_not_found envelope.
		respondFixture("delete_page", "get-page-not-found-response");
		assertThat(client.deleteDocument(FIXTURE_KEY)).isEqualTo(GbrainDeleteResult.NOT_FOUND);

		respondFixture("delete_page", "delete-page-response", page -> page.put("status", "purged"));
		assertCode(() -> client.deleteDocument(FIXTURE_KEY), GbrainErrorCode.PROTOCOL);

		server.onMcp("tools/call:delete_page", request -> Response.empty(503));
		assertCode(() -> client.deleteDocument(FIXTURE_KEY), GbrainErrorCode.UNAVAILABLE);
		assertThat(server.toolCalls()).hasSize(3);
	}

	@ParameterizedTest
	@CsvSource({
			"restore-page-response, RESTORED",
			"restore-page-already-active-response, ALREADY_ACTIVE"})
	void restoreMapsCapturedStatuses(String fixtureName, GbrainRestoreResult expected) {
		respondFixture("restore_page", fixtureName);

		assertThat(client.restoreDocument(FIXTURE_KEY)).isEqualTo(expected);
		assertThat(server.toolCalls().get(0).json().path("params").path("arguments").path("force").asBoolean())
				.isTrue();
	}

	@Test
	void restoreHandlesPurgedUnexpectedAndFailedCalls() {
		respondFixture("restore_page", "get-page-not-found-response");
		assertThat(client.restoreDocument(FIXTURE_KEY)).isEqualTo(GbrainRestoreResult.NOT_FOUND);

		respondFixture("restore_page", "restore-page-response", page -> page.remove("status"));
		assertCode(() -> client.restoreDocument(FIXTURE_KEY), GbrainErrorCode.PROTOCOL);

		respondError("restore_page", "permission_denied");
		assertCode(() -> client.restoreDocument(FIXTURE_KEY), GbrainErrorCode.UNAUTHORIZED);
	}

	@Test
	void searchMapsTheCapturedHitsAndRetrievalEvidence() {
		respondSearch(hits -> {
		}, meta -> {
		});

		GbrainSearchResult result = client
				.search(new GbrainSearchRequest("Which birds review purchasing?", 5, Set.of("smoke_test", "note")));

		assertThat(result).isEqualTo(new GbrainSearchResult(
				List.of(new GbrainSearchHit(FIXTURE_KEY, FIXTURE_SLUG, "Phase 1 Semantic Lighthouse", "smoke_test",
						"The obsidian lighthouse protocol authorizes blue herons to audit quarterly procurement records."
								+ " This synthetic sentence exists only to verify semantic retrieval.",
						0.7988870469269639)),
				new GbrainRetrieval(true, false, true, List.of())));
		ObjectNode arguments = (ObjectNode) server.toolCalls().get(0).json().path("params").path("arguments");
		assertThat(arguments.path("query").asString()).isEqualTo("Which birds review purchasing?");
		assertThat(arguments.path("limit").asInt()).isEqualTo(5);
		assertThat(arguments.path("source_id").asString()).isEqualTo("knowledgebridge");
		assertThat(arguments.path("types").toString())
				.isEqualTo("[\"knowledgebridge_note\",\"knowledgebridge_smoke_test\"]");
	}

	@Test
	void searchOmitsPagesOutsideTheSlugPrefixAndUnfilteredTypes() {
		respondSearch(hits -> {
			hits.addObject().put("slug", "people/alice-example").put("score", 0.9);
			hits.addObject().put("slug", "knowledgebridge/Not A Key").put("score", 0.7);
		}, meta -> {
		});

		assertThat(client.search(new GbrainSearchRequest("herons", 10, Set.of())).hits())
				.extracting(GbrainSearchHit::itemKey)
				.containsExactly(FIXTURE_KEY);
		assertThat(server.toolCalls().get(0).json().path("params").path("arguments").has("types")).isFalse();
	}

	@Test
	void searchEnforcesTheTypeFilterWhenGbrainDropsIt() {
		// gbrain searches every type when no page has a requested type, so other types can come back.
		respondSearch(hits -> {
		}, meta -> {
		});

		assertThat(client.search(new GbrainSearchRequest("herons", 10, Set.of("policy"))).hits()).isEmpty();
		assertThat(client.search(new GbrainSearchRequest("herons", 10, Set.of("policy", "smoke_test"))).hits())
				.extracting(GbrainSearchHit::itemKey)
				.containsExactly(FIXTURE_KEY);
	}

	@Test
	void searchReportsDegradedRetrieval() {
		respondSearch(hits -> {
		}, meta -> {
			ObjectNode retrieval = (ObjectNode) meta.path("retrieval");
			retrieval.put("vector_enabled", false);
			retrieval.putObject("projection_readiness").put("status", "projection_pending").put("ready", false);
			ArrayNode degraded = retrieval.putArray("degraded");
			degraded.addObject().put("stage", "embed_unavailable").put("reason", "no_provider");
			degraded.addObject().put("stage", "keyword_zero");
		});

		GbrainRetrieval retrieval = client.search(new GbrainSearchRequest("herons", 10, Set.of())).retrieval();

		assertThat(retrieval).isEqualTo(new GbrainRetrieval(false, false, false,
				List.of(new GbrainRetrieval.Degradation("embed_unavailable", "no_provider"),
						new GbrainRetrieval.Degradation("keyword_zero", null))));
		assertThat(retrieval.healthy()).isFalse();
	}

	@Test
	void searchToleratesRetrievalMetadataWithoutOptionalFields() {
		respondSearch(hits -> {
		}, meta -> meta.putObject("retrieval").put("returned_count", 1));

		assertThat(client.search(new GbrainSearchRequest("herons", 10, Set.of())).retrieval())
				.isEqualTo(new GbrainRetrieval(false, false, true, List.of()));
	}

	@Test
	void searchRejectsMalformedResponses() {
		GbrainSearchRequest request = new GbrainSearchRequest("herons", 10, Set.of());

		respond("search", "{}");
		assertCode(() -> client.search(request), GbrainErrorCode.PROTOCOL);

		// A bare tool result carries no _meta.retrieval evidence.
		respond("search", "[]");
		assertCode(() -> client.search(request), GbrainErrorCode.PROTOCOL);

		respondSearch(hits -> ((ObjectNode) hits.get(0)).put("score", "high"), meta -> {
		});
		assertCode(() -> client.search(request), GbrainErrorCode.PROTOCOL);

		respondSearch(hits -> ((ObjectNode) hits.get(0)).remove("chunk_text"), meta -> {
		});
		assertCode(() -> client.search(request), GbrainErrorCode.PROTOCOL);

		respondSearch(hits -> {
		}, meta -> ((ObjectNode) meta.path("retrieval")).put("degraded", "embed_unavailable"));
		assertCode(() -> client.search(request), GbrainErrorCode.PROTOCOL);

		respondSearch(hits -> {
		}, meta -> ((ObjectNode) meta.path("retrieval")).putArray("degraded").addObject().put("reason", "timeout"));
		assertCode(() -> client.search(request), GbrainErrorCode.PROTOCOL);
	}

	@Test
	void synthesizeMapsTheCapturedAnswer() {
		respondFixture("synthesize", "synthesize-response");

		GbrainSynthesisResult result = client.synthesize(new GbrainSynthesisRequest("Who audits procurement?"));

		assertThat(result).isEqualTo(new GbrainSynthesisResult(
				"The obsidian lighthouse protocol authorizes blue herons to audit quarterly procurement records ["
						+ FIXTURE_SLUG + "].",
				GbrainSynthesisResult.Status.SYNTHESIZED, List.of(new GbrainCitation(FIXTURE_KEY, FIXTURE_SLUG)),
				List.of(),
				new GbrainSynthesisResult.Usage("openai:gpt-4o-mini", 600L, 80L, new BigDecimal("0.000138")),
				List.of()));
		assertThat(server.toolCalls().get(0).json().path("params").path("arguments").toString())
				.isEqualTo("{\"question\":\"Who audits procurement?\"}");
	}

	@Test
	void synthesizeReportsExtractiveFallbackGapsAndDeduplicatedCitations() {
		respondFixture("synthesize", "synthesize-response", payload -> {
			payload.put("synthesis_status", "extractive_fallback");
			payload.putArray("sources").add(FIXTURE_SLUG).add("people/alice-example").add(FIXTURE_SLUG);
			payload.putArray("gaps").add("No data on Q3 audits");
			payload.putArray("warnings").add("LLM_OUTPUT_TRUNCATED");
			((ObjectNode) payload.path("cost")).putNull("input_tokens").put("usd_estimate", new BigDecimal("0.0012"));
		});

		GbrainSynthesisResult result = client.synthesize(new GbrainSynthesisRequest("Who audits procurement?"));

		assertThat(result.status()).isEqualTo(GbrainSynthesisResult.Status.EXTRACTIVE_FALLBACK);
		assertThat(result.citations()).containsExactly(new GbrainCitation(FIXTURE_KEY, FIXTURE_SLUG));
		assertThat(result.gaps()).containsExactly("No data on Q3 audits");
		assertThat(result.warnings()).containsExactly("LLM_OUTPUT_TRUNCATED");
		assertThat(result.usage()).isEqualTo(new GbrainSynthesisResult.Usage("openai:gpt-4o-mini", null, 80L,
				new BigDecimal("0.0012")));
	}

	@Test
	void synthesizeToleratesMissingAccountingAndOptionalLists() {
		respondFixture("synthesize", "synthesize-response", payload -> {
			payload.remove("cost");
			payload.remove("sources");
			payload.remove("gaps");
			payload.remove("warnings");
		});

		GbrainSynthesisResult result = client.synthesize(new GbrainSynthesisRequest("Who audits procurement?"));

		assertThat(result.usage()).isEqualTo(new GbrainSynthesisResult.Usage(null, null, null, null));
		assertThat(result.citations()).isEmpty();
		assertThat(result.gaps()).isEmpty();
	}

	@Test
	void synthesizeRejectsMalformedResponses() {
		GbrainSynthesisRequest request = new GbrainSynthesisRequest("Who audits procurement?");

		respondFixture("synthesize", "synthesize-response", payload -> payload.put("synthesis_status", "pending"));
		assertCode(() -> client.synthesize(request), GbrainErrorCode.PROTOCOL);

		respondFixture("synthesize", "synthesize-response", payload -> payload.remove("answer"));
		assertCode(() -> client.synthesize(request), GbrainErrorCode.PROTOCOL);

		respondFixture("synthesize", "synthesize-response", payload -> payload.put("sources", FIXTURE_SLUG));
		assertCode(() -> client.synthesize(request), GbrainErrorCode.PROTOCOL);

		respondFixture("synthesize", "synthesize-response", payload -> payload.putArray("gaps").add(3));
		assertCode(() -> client.synthesize(request), GbrainErrorCode.PROTOCOL);
	}

	@Test
	void synthesizeIsNeverRetried() {
		respondError("synthesize", "unavailable");
		assertCode(() -> client.synthesize(new GbrainSynthesisRequest("Why?")), GbrainErrorCode.UNAVAILABLE);

		server.onMcp("tools/call:synthesize", request -> Response.empty(503));
		assertCode(() -> client.synthesize(new GbrainSynthesisRequest("Why?")), GbrainErrorCode.UNAVAILABLE);
		assertThat(server.toolCalls()).hasSize(2);
	}

	private void respond(String tool, String payload) {
		server.onMcp("tools/call:" + tool, request -> Response.sse(toolResult(request.rpcId(), payload, false)));
	}

	private void respondError(String tool, String code) {
		server.onMcp("tools/call:" + tool, request -> Response.sse(toolResult(request.rpcId(),
				"{\"error\":\"" + code + "\",\"message\":\"denied\"}", true)));
	}

	private void respondFixture(String tool, String fixtureName) {
		respondFixture(tool, fixtureName, page -> {
		});
	}

	/** Serves the captured search fixture after editing its hits and its {@code _meta} block. */
	private void respondSearch(Consumer<ArrayNode> editHits, Consumer<ObjectNode> editMeta) {
		server.onMcp("tools/call:search", request -> {
			JsonNode response = fixture("search-response.json", request.rpcId());
			ObjectNode content = (ObjectNode) response.path("result").path("content").get(0);
			ArrayNode hits = (ArrayNode) JSON.readTree(content.path("text").asString());
			editHits.accept(hits);
			content.put("text", hits.toString());
			editMeta.accept((ObjectNode) response.path("result").path("_meta"));
			return Response.sse(response);
		});
	}

	/** Serves a captured fixture, optionally editing the tool payload encoded in its text block. */
	private void respondFixture(String tool, String fixtureName, Consumer<ObjectNode> edit) {
		server.onMcp("tools/call:" + tool, request -> {
			JsonNode response = fixture(fixtureName + ".json", request.rpcId());
			ObjectNode content = (ObjectNode) response.path("result").path("content").get(0);
			ObjectNode payload = (ObjectNode) JSON.readTree(content.path("text").asString());
			edit.accept(payload);
			content.put("text", payload.toString());
			return Response.sse(response);
		});
	}

}
