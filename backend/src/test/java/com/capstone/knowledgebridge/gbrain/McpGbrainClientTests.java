package com.capstone.knowledgebridge.gbrain;

import static com.capstone.knowledgebridge.gbrain.GbrainMcpClientTests.assertCode;
import static com.capstone.knowledgebridge.gbrain.MockGbrainServer.JSON;
import static com.capstone.knowledgebridge.gbrain.MockGbrainServer.toolResult;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;

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

import tools.jackson.databind.node.ObjectNode;

/**
 * Exercises the page mappings over the real transport. put_page responses come from the Phase 1 fixture; get_page,
 * delete_page, and restore_page shapes are derived from the pinned revision's src/core/ops/pages.ts.
 */
class McpGbrainClientTests {

	private static final String SLUG = "knowledgebridge/item-1";

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
	void getDocumentReadsKnowledgeBridgeMetadataIncludingDeletedPages() {
		respond("get_page", page(document.contentDigest(), "3", "null"));

		GbrainStoredDocument stored = client.getDocument("item-1").orElseThrow();

		assertThat(stored).isEqualTo(new GbrainStoredDocument(SLUG, "Travel Policy", "policy", 3L,
				document.contentDigest(), null));
		assertThat(client.documentState(document)).isEqualTo(GbrainDocumentState.CURRENT);
		ObjectNode arguments = (ObjectNode) server.toolCalls().get(0).json().path("params").path("arguments");
		assertThat(arguments.path("include_deleted").asBoolean()).isTrue();
		assertThat(arguments.path("source_id").asString()).isEqualTo("knowledgebridge");
	}

	@Test
	void getDocumentReportsSoftDeletionAndStaleOrForeignPages() {
		respond("get_page", page("stale", "3", "\"2026-09-03T10:15:30.123Z\""));
		assertThat(client.getDocument("item-1").orElseThrow().deletedAt())
				.isEqualTo(Instant.parse("2026-09-03T10:15:30.123Z"));
		assertThat(client.documentState(document)).isEqualTo(GbrainDocumentState.DELETED);

		respond("get_page", page("stale", "\"3\"", "null"));
		assertThat(client.getDocument("item-1").orElseThrow().revision()).isNull();
		assertThat(client.documentState(document)).isEqualTo(GbrainDocumentState.STALE);

		respond("get_page", "{\"slug\":\"" + SLUG + "\",\"title\":\"Foreign\",\"type\":\"note\"}");
		assertThat(client.getDocument("item-1").orElseThrow())
				.isEqualTo(new GbrainStoredDocument(SLUG, "Foreign", "note", null, null, null));
	}

	@Test
	void getDocumentMapsMissingPagesToEmpty() {
		respondError("get_page", "page_not_found");

		assertThat(client.getDocument("item-1")).isEmpty();
		assertThat(client.documentState(document)).isEqualTo(GbrainDocumentState.MISSING);
	}

	@Test
	void getDocumentRejectsOtherFailuresAndUnexpectedPages() {
		respondError("get_page", "permission_denied");
		assertCode(() -> client.getDocument("item-1"), GbrainErrorCode.UNAUTHORIZED);

		respond("get_page", "{\"slug\":\"knowledgebridge/other\"}");
		assertCode(() -> client.getDocument("item-1"), GbrainErrorCode.PROTOCOL);

		respond("get_page", page("x", "1", "\"yesterday\""));
		assertCode(() -> client.getDocument("item-1"), GbrainErrorCode.PROTOCOL);

		assertCode(() -> client.getDocument("Not A Key"), GbrainErrorCode.VALIDATION);
	}

	@ParameterizedTest
	@CsvSource({
			"soft_deleted, DELETED",
			"already_soft_deleted, ALREADY_DELETED"})
	void deleteMapsGbrainStatuses(String status, GbrainDeleteResult expected) {
		respond("delete_page", "{\"status\":\"" + status + "\",\"slug\":\"" + SLUG + "\"}");

		assertThat(client.deleteDocument("item-1")).isEqualTo(expected);
		ObjectNode arguments = (ObjectNode) server.toolCalls().get(0).json().path("params").path("arguments");
		assertThat(arguments.path("slug").asString()).isEqualTo(SLUG);
		assertThat(arguments.path("source_id").asString()).isEqualTo("knowledgebridge");
	}

	@Test
	void deleteHandlesMissingUnexpectedAndFailedCalls() {
		respondError("delete_page", "page_not_found");
		assertThat(client.deleteDocument("item-1")).isEqualTo(GbrainDeleteResult.NOT_FOUND);

		respond("delete_page", "{\"status\":\"purged\"}");
		assertCode(() -> client.deleteDocument("item-1"), GbrainErrorCode.PROTOCOL);

		server.onMcp("tools/call:delete_page", request -> Response.empty(503));
		assertCode(() -> client.deleteDocument("item-1"), GbrainErrorCode.UNAVAILABLE);
		assertThat(server.toolCalls()).hasSize(3);
	}

	@ParameterizedTest
	@CsvSource({
			"restored, RESTORED",
			"already_active, ALREADY_ACTIVE"})
	void restoreMapsGbrainStatuses(String status, GbrainRestoreResult expected) {
		respond("restore_page", "{\"status\":\"" + status + "\",\"slug\":\"" + SLUG + "\"}");

		assertThat(client.restoreDocument("item-1")).isEqualTo(expected);
	}

	@Test
	void restoreHandlesPurgedUnexpectedAndFailedCalls() {
		respondError("restore_page", "page_not_found");
		assertThat(client.restoreDocument("item-1")).isEqualTo(GbrainRestoreResult.NOT_FOUND);

		respond("restore_page", "{}");
		assertCode(() -> client.restoreDocument("item-1"), GbrainErrorCode.PROTOCOL);

		respondError("restore_page", "permission_denied");
		assertCode(() -> client.restoreDocument("item-1"), GbrainErrorCode.UNAUTHORIZED);
	}

	private void respond(String tool, String payload) {
		server.onMcp("tools/call:" + tool, request -> Response.sse(toolResult(request.rpcId(), payload, false)));
	}

	private void respondError(String tool, String code) {
		server.onMcp("tools/call:" + tool, request -> Response.sse(toolResult(request.rpcId(),
				"{\"error\":\"" + code + "\",\"message\":\"Page not found: " + SLUG + "\"}", true)));
	}

	/** Shape of get_page for a remote caller: the page row plus tags, with frontmatter as parsed by gbrain. */
	private static String page(String digest, String revision, String deletedAt) {
		return """
				{"id":12,"slug":"%s","type":"policy","title":"Travel Policy","compiled_truth":"# Travel\\nBook early.\\n",
				"timeline":"","frontmatter":{"title":"Travel Policy","type":"policy","knowledgebridge_id":"item-1",
				"knowledgebridge_owner":"7","knowledgebridge_revision":%s,"knowledgebridge_digest":"%s"},
				"content_hash":"abc","created_at":"2026-09-01T12:00:01.000Z","updated_at":"2026-09-02T08:30:01.000Z",
				"deleted_at":%s,"source_id":"knowledgebridge","tags":[]}
				"""
				.formatted(SLUG, revision, digest, deletedAt);
	}

}
