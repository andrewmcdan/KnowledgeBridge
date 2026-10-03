package com.capstone.knowledgebridge.gbrain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.capstone.knowledgebridge.gbrain.model.GbrainDeleteResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainDocument;
import com.capstone.knowledgebridge.gbrain.model.GbrainDocumentState;
import com.capstone.knowledgebridge.gbrain.model.GbrainRestoreResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainRetrieval;
import com.capstone.knowledgebridge.gbrain.model.GbrainSearchRequest;
import com.capstone.knowledgebridge.gbrain.model.GbrainSearchResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainSynthesisRequest;
import com.capstone.knowledgebridge.gbrain.model.GbrainWriteResult;

class InMemoryGbrainClientTests {

	private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");

	private final GbrainClient client = new InMemoryGbrainClient(Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void mirrorsGbrainPageLifecycle() {
		GbrainDocument first = document(1, "Version one");
		GbrainDocument second = document(2, "Version two");

		assertThat(client.documentState(first)).isEqualTo(GbrainDocumentState.MISSING);
		assertThat(client.deleteDocument("item-1")).isEqualTo(GbrainDeleteResult.NOT_FOUND);
		assertThat(client.restoreDocument("item-1")).isEqualTo(GbrainRestoreResult.NOT_FOUND);

		assertThat(client.upsertDocument(first).status()).isEqualTo(GbrainWriteResult.Status.WRITTEN);
		assertThat(client.upsertDocument(first)).isEqualTo(
				new GbrainWriteResult("knowledgebridge/item-1", GbrainWriteResult.Status.UNCHANGED, 0));
		assertThat(client.documentState(first)).isEqualTo(GbrainDocumentState.CURRENT);
		assertThat(client.documentState(second)).isEqualTo(GbrainDocumentState.STALE);

		assertThat(client.upsertDocument(second).status()).isEqualTo(GbrainWriteResult.Status.WRITTEN);
		assertThat(client.getDocument("item-1").orElseThrow().revision()).isEqualTo(2L);

		assertThat(client.restoreDocument("item-1")).isEqualTo(GbrainRestoreResult.ALREADY_ACTIVE);
		assertThat(client.deleteDocument("item-1")).isEqualTo(GbrainDeleteResult.DELETED);
		assertThat(client.getDocument("item-1").orElseThrow().deletedAt()).isEqualTo(NOW);
		assertThat(client.deleteDocument("item-1")).isEqualTo(GbrainDeleteResult.ALREADY_DELETED);
		assertThat(client.documentState(second)).isEqualTo(GbrainDocumentState.DELETED);

		assertThat(client.restoreDocument("item-1")).isEqualTo(GbrainRestoreResult.RESTORED);
		assertThat(client.documentState(second)).isEqualTo(GbrainDocumentState.CURRENT);
	}

	@Test
	void writingASoftDeletedPageRevivesIt() {
		GbrainDocument document = document(1, "Body");
		client.upsertDocument(document);
		client.deleteDocument("item-1");

		assertThat(client.upsertDocument(document).status()).isEqualTo(GbrainWriteResult.Status.WRITTEN);
		assertThat(client.documentState(document)).isEqualTo(GbrainDocumentState.CURRENT);
	}

	@Test
	void searchFindsNothingAndReportsNoVectorArm() {
		client.upsertDocument(document(1, "Body"));

		assertThat(client.search(new GbrainSearchRequest("Body", 10, Set.of())))
				.isEqualTo(new GbrainSearchResult(List.of(), new GbrainRetrieval(false, false, List.of())));
	}

	@Test
	void synthesisIsUnavailable() {
		GbrainMcpClientTests.assertCode(() -> client.synthesize(new GbrainSynthesisRequest("Why?")),
				GbrainErrorCode.UNAVAILABLE);
	}

	private static GbrainDocument document(long revision, String body) {
		return new GbrainDocument("item-1", "Title", "note", "7", revision, NOW, NOW, body);
	}

}
