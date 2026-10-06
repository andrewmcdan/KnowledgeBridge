package com.capstone.knowledgebridge.gbrain;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.util.unit.DataSize;

import com.capstone.knowledgebridge.gbrain.model.GbrainDeleteResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainDocument;
import com.capstone.knowledgebridge.gbrain.model.GbrainDocumentState;
import com.capstone.knowledgebridge.gbrain.model.GbrainRestoreResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainSearchHit;
import com.capstone.knowledgebridge.gbrain.model.GbrainSearchRequest;
import com.capstone.knowledgebridge.gbrain.model.GbrainSearchResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainSynthesisRequest;
import com.capstone.knowledgebridge.gbrain.model.GbrainSynthesisResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainWriteResult;

import tools.jackson.databind.JsonNode;

/**
 * End-to-end check of the adapter against a running pinned gbrain with embeddings and a synthesis model. Run it with
 * {@code gradlew liveTest} against a disposable instance (scripts/test-gbrain-live.sh); it is excluded from
 * {@code test}. It writes one synthetic page and soft-deletes it, and makes one paid synthesis call.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "GBRAIN_LIVE_BASE_URL", matches = ".+")
class GbrainLiveTests {

	private static final String BODY_V1 = """
			# Saffron Kite Protocol

			Under the saffron kite protocol, copper otters inspect the lighthouse ledger every equinox.
			Any discrepancy is reported to the tidewater council within three days.
			""";

	private static final String BODY_V2 = BODY_V1 + "\nThe council archives each report for nine winters.\n";

	@Test
	void indexesSearchesSynthesizesAndSoftDeletesAPage() {
		URI baseUrl = URI.create(System.getenv("GBRAIN_LIVE_BASE_URL"));
		GbrainProperties properties = new GbrainProperties(true, baseUrl, System.getenv("GBRAIN_LIVE_CLIENT_ID"),
				System.getenv("GBRAIN_LIVE_CLIENT_SECRET"), baseUrl.resolve("/token"), Duration.ofSeconds(5),
				Duration.ofSeconds(60), Duration.ofSeconds(180), DataSize.ofMegabytes(4), 3, Duration.ofSeconds(5),
				GbrainProperties.Mode.MCP);
		GbrainMcpClient transport = new GbrainMcpClient(properties,
				new GbrainTokenProvider(properties, MockGbrainServer.JSON, Clock.systemUTC()), MockGbrainServer.JSON,
				duration -> Thread.sleep(duration.toMillis()));
		GbrainClient client = new McpGbrainClient(transport);

		assertThat(transport.discoverCapabilities().ready()).isTrue();
		JsonNode identity = transport.callTool(GbrainTool.WHOAMI, Map.of()).payload();
		assertThat(identity.path("source_id").asString()).isEqualTo(GbrainPages.SOURCE_ID);
		assertThat(identity.path("scopes").valueStream().map(JsonNode::asString))
				.containsExactlyInAnyOrder("read", "write");

		String itemKey = "live-" + HexFormat.of().formatHex(randomBytes());
		Instant created = Instant.now();
		GbrainDocument first = document(itemKey, 1, created, BODY_V1);
		try {
			GbrainWriteResult written = client.upsertDocument(first);
			assertThat(written.status()).isEqualTo(GbrainWriteResult.Status.WRITTEN);
			assertThat(written.chunks()).isPositive();
			assertThat(client.upsertDocument(first).status()).isEqualTo(GbrainWriteResult.Status.UNCHANGED);
			assertThat(client.documentState(first)).isEqualTo(GbrainDocumentState.CURRENT);

			// A paraphrase shares no distinctive words with the page, so only the vector arm can find it. gbrain embeds
			// after the write commits, so the page becomes searchable shortly afterwards.
			String paraphrase = "Which river animals audit the beacon records each season?";
			GbrainSearchResult found = awaitHit(client,
					new GbrainSearchRequest(paraphrase, 10, Set.of("live_test")), itemKey);
			assertThat(found.retrieval().healthy()).isTrue();
			assertThat(found.hits()).extracting(GbrainSearchHit::itemKey).contains(itemKey);
			GbrainSearchHit hit = found.hits()
					.stream()
					.filter(candidate -> candidate.itemKey().equals(itemKey))
					.findFirst()
					.orElseThrow();
			assertThat(hit.documentType()).isEqualTo("live_test");
			assertThat(hit.title()).isEqualTo("Saffron Kite Protocol");

			// The type filter reaches gbrain: another type excludes the page.
			assertThat(client.search(new GbrainSearchRequest(paraphrase, 10, Set.of("policy"))).hits())
					.extracting(GbrainSearchHit::itemKey)
					.doesNotContain(itemKey);

			GbrainSynthesisResult answer = client
					.synthesize(new GbrainSynthesisRequest("Who inspects the lighthouse ledger, and how often?"));
			assertThat(answer.status()).isEqualTo(GbrainSynthesisResult.Status.SYNTHESIZED);
			assertThat(answer.citations()).extracting(GbrainSynthesisResult.GbrainCitation::itemKey)
					.contains(itemKey);
			assertThat(answer.answer()).isNotBlank();
			assertThat(answer.usage().model()).isNotBlank();

			GbrainDocument second = document(itemKey, 2, created, BODY_V2);
			assertThat(client.upsertDocument(second).status()).isEqualTo(GbrainWriteResult.Status.WRITTEN);
			assertThat(client.documentState(second)).isEqualTo(GbrainDocumentState.CURRENT);
			assertThat(client.documentState(first)).isEqualTo(GbrainDocumentState.STALE);

			assertThat(client.deleteDocument(itemKey)).isEqualTo(GbrainDeleteResult.DELETED);
			assertThat(client.documentState(second)).isEqualTo(GbrainDocumentState.DELETED);
			assertThat(client.search(new GbrainSearchRequest(paraphrase, 10, Set.of())).hits())
					.extracting(GbrainSearchHit::itemKey)
					.doesNotContain(itemKey);
			assertThat(client.restoreDocument(itemKey)).isEqualTo(GbrainRestoreResult.RESTORED);
			assertThat(client.documentState(second)).isEqualTo(GbrainDocumentState.CURRENT);
		} finally {
			client.deleteDocument(itemKey);
		}
	}

	private static GbrainSearchResult awaitHit(GbrainClient client, GbrainSearchRequest request, String itemKey) {
		GbrainSearchResult result = client.search(request);
		for (int attempt = 1; attempt < 30
				&& result.hits().stream().noneMatch(hit -> hit.itemKey().equals(itemKey)); attempt++) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(exception);
			}
			result = client.search(request);
		}
		return result;
	}

	private static GbrainDocument document(String itemKey, long revision, Instant created, String body) {
		return new GbrainDocument(itemKey, "Saffron Kite Protocol", "live_test", "live-test", revision, created,
				Instant.now(), body);
	}

	private static byte[] randomBytes() {
		byte[] bytes = new byte[6];
		ThreadLocalRandom.current().nextBytes(bytes);
		return bytes;
	}

}
