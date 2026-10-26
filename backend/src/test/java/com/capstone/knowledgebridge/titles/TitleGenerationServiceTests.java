package com.capstone.knowledgebridge.titles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.capstone.knowledgebridge.knowledge.IngestionStage;
import com.capstone.knowledgebridge.knowledge.KnowledgeItem;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TitleGenerationServiceTests {

	private final JsonMapper json = new JsonMapper();
	private final KnowledgeItemRepository repository = mock(KnowledgeItemRepository.class);
	private final RestClient.Builder builder = RestClient.builder().baseUrl("http://title-provider.test");
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
	private final TitleGenerationService titles = new TitleGenerationService(builder.build(), "key", "test-model", json,
			repository);

	@Test
	void defaultNamingNeverContactsTheProviderOrDatabase() {
		titles.prepare(new KnowledgeItem("Original", "Note", 1L, "Body"));
		server.verify();
		verifyNoInteractions(repository);
	}

	@Test
	void usesGuidanceAndContentAndPersistsTheFinalTitleOnlyOnce() {
		String guide = "Meeting 10/27/2026";
		KnowledgeItem item = new KnowledgeItem(guide, "Meeting", 1L, "Budget review and allocation decisions.");
		item.requestAiTitle(true);
		List<IngestionStage> stages = new ArrayList<>();
		doAnswer(call -> {
			stages.add(item.getIngestionStage());
			return item;
		}).when(repository).save(item);
		server.expect(requestTo("http://title-provider.test/chat/completions")).andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer key"))
				.andExpect(request -> {
					JsonNode body = json.readTree(((MockClientHttpRequest) request).getBodyAsString());
					assertThat(body.path("model").asString()).isEqualTo("test-model");
					assertThat(body.path("response_format").path("json_schema").path("strict").asBoolean()).isTrue();
					JsonNode input = json.readTree(body.path("messages").path(1).path("content").asString());
					assertThat(input.path("titleGuidance").asString()).isEqualTo(guide);
					assertThat(input.path("contentExcerpt").asString()).contains("Budget review");
				})
				.andRespond(withSuccess(response("  Meeting October 27, 2026 - Budget Review  "),
						MediaType.APPLICATION_JSON));
		titles.prepare(item);
		titles.prepare(item);
		assertThat(item.getTitle()).isEqualTo("Meeting October 27, 2026 - Budget Review");
		assertThat(item.isAiTitleRequested()).isFalse();
		assertThat(stages).containsExactly(IngestionStage.GENERATING_TITLE, IngestionStage.INDEXING);
		server.verify();
	}

	@Test
	void boundsLongContentWithBeginningMiddleAndEndingExcerpts() {
		String text = "Beginning" + "a".repeat(20_000) + "Middle" + "b".repeat(20_000) + "Ending";
		server.expect(requestTo("http://title-provider.test/chat/completions")).andExpect(request -> {
			JsonNode body = json.readTree(((MockClientHttpRequest) request).getBodyAsString());
			String excerpt = json.readTree(body.path("messages").path(1).path("content").asString())
					.path("contentExcerpt").asString();
			assertThat(excerpt).contains("Beginning", "Middle", "Ending").hasSizeLessThan(25_000);
		}).andRespond(withSuccess(response("Title"), MediaType.APPLICATION_JSON));
		assertThat(titles.generate("Guidance", text)).isEqualTo("Title");
		server.verify();
	}

	@Test
	void rejectsInvalidTitlesAndIncompleteOrMalformedProviderOutput() {
		for (String payload : List.of("{}", "{\"choices\":[{\"finish_reason\":\"length\"}]}",
				completion("{}"), completion("{\"title\":42}"), completion("{\"title\":null}"),
				response(" "), response("x".repeat(256)), response("Two\nlines"), completion("not json"), "not json")) {
			RestClient.Builder local = RestClient.builder().baseUrl("http://title-provider.test");
			MockRestServiceServer stub = MockRestServiceServer.bindTo(local).build();
			stub.expect(requestTo("http://title-provider.test/chat/completions"))
					.andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));
			TitleGenerationService service = new TitleGenerationService(local.build(), "key", "model", json,
					repository);
			assertThatThrownBy(() -> service.generate("Guide", "Text")).isInstanceOf(TitleGenerationException.class);
			stub.verify();
		}
	}

	@Test
	void configurationAndProviderFailuresRemainSafeAndDoNotRetryAutomatically() {
		TitleGenerationService missingKey = new TitleGenerationService(builder.build(), " ", "model", json, repository);
		assertThatThrownBy(() -> missingKey.generate("Guide", "Text")).hasMessageContaining("not configured");
		server.expect(requestTo("http://title-provider.test/chat/completions"))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("private upstream details"));
		KnowledgeItem item = new KnowledgeItem("Keep guidance", "Note", 1L, "Keep content");
		item.requestAiTitle(true);
		assertThatThrownBy(() -> titles.prepare(item))
				.hasMessage("AI naming is unavailable. Retry ingestion to try again.");
		assertThat(item.getTitle()).isEqualTo("Keep guidance");
		assertThat(item.isAiTitleRequested()).isTrue();
		server.verify();
	}

	@Test
	void emptyAndUnavailableResponsesAreRejected() {
		server.expect(requestTo("http://title-provider.test/chat/completions"))
				.andRespond(withStatus(HttpStatus.NO_CONTENT));
		server.expect(requestTo("http://title-provider.test/chat/completions"))
				.andRespond(withException(new IOException("private network details")));
		assertThatThrownBy(() -> titles.generate("Guide", "Text")).hasMessageContaining("invalid title");
		assertThatThrownBy(() -> titles.generate("Guide", "Text")).hasMessageContaining("unavailable");
		server.verify();
	}

	private String response(String title) {
		return completion(json.writeValueAsString(Map.of("title", title)));
	}

	private String completion(String content) {
		return json.writeValueAsString(
				Map.of("choices", List.of(Map.of("finish_reason", "stop", "message", Map.of("content", content)))));
	}
}
