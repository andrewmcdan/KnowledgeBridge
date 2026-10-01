package com.capstone.knowledgebridge.gbrain;

import static com.capstone.knowledgebridge.gbrain.GbrainMcpClientTests.assertCode;
import static com.capstone.knowledgebridge.gbrain.MockGbrainServer.JSON;
import static com.capstone.knowledgebridge.gbrain.MockGbrainServer.fixture;
import static com.capstone.knowledgebridge.gbrain.MockGbrainServer.toolResult;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
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
import com.capstone.knowledgebridge.gbrain.model.GbrainStoredDocument;
import com.capstone.knowledgebridge.gbrain.model.GbrainWriteResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Exercises the page mappings over the real transport. put_page shapes follow the Phase 1 fixture; get_page,
 * delete_page, and restore_page responses are the fixtures captured live by scripts/test-gbrain-mcp.sh
 * --capture-fixtures.
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
		assertThat(stored.deletedAt()).isEqualTo(Instant.parse("2026-10-01T22:58:52.440Z"));
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
