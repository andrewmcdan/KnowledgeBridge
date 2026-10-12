package com.capstone.knowledgebridge.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.capstone.knowledgebridge.gbrain.GbrainClient;
import com.capstone.knowledgebridge.gbrain.model.GbrainRetrieval;
import com.capstone.knowledgebridge.gbrain.model.GbrainSearchHit;
import com.capstone.knowledgebridge.gbrain.model.GbrainSearchResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainSynthesisResult;
import com.capstone.knowledgebridge.knowledge.KnowledgeItem;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemRepository;

class QueryServiceTests {

	private final GbrainClient gbrainClient = mock(GbrainClient.class);

	private final KnowledgeItemRepository knowledgeItemRepository = mock(KnowledgeItemRepository.class);

	private final QueryService queryService = new QueryService(gbrainClient, knowledgeItemRepository);

	@Test
	void searchUsesTheDefaultLimitAndReturnsRetrievalEvidence() {
		when(gbrainClient.search(any())).thenReturn(new GbrainSearchResult(
				List.of(new GbrainSearchHit("42", "knowledgebridge/42", "Benefits Policy", "policy", "Benefits", 0.9)),
				new GbrainRetrieval(true, false, true, List.of())));

		QueryService.SearchResponse response = queryService.search("What benefits are offered?", null);

		assertThat(response.hits()).singleElement().satisfies(hit -> {
			assertThat(hit.title()).isEqualTo("Benefits Policy");
			assertThat(hit.score()).isEqualTo(0.9);
		});
		assertThat(response.retrieval()).isEqualTo(new QueryService.RetrievalResponse(true, true, false));
		verify(gbrainClient).search(new com.capstone.knowledgebridge.gbrain.model.GbrainSearchRequest(
				"What benefits are offered?", QueryService.DEFAULT_SEARCH_LIMIT, null));
	}

	@Test
	void synthesisMapsKnownCitationMetadataAndUsage() {
		KnowledgeItem item = new KnowledgeItem("Travel Policy", "Policy", 7L, "body");
		ReflectionTestUtils.setField(item, "id", 42L);
		when(knowledgeItemRepository.findAllById(List.of(42L))).thenReturn(List.of(item));
		when(gbrainClient.synthesize(any())).thenReturn(new GbrainSynthesisResult("Employees need approval.",
				GbrainSynthesisResult.Status.SYNTHESIZED, List.of(
						new GbrainSynthesisResult.GbrainCitation("42", "knowledgebridge/42"),
						new GbrainSynthesisResult.GbrainCitation("not-a-number", "knowledgebridge/not-a-number")),
				List.of(),
				new GbrainSynthesisResult.Usage("openrouter:model", 12L, 8L, new BigDecimal("0.001")), List.of()));

		QueryService.SynthesisResponse response = queryService.synthesize("What travel approval is needed?");

		assertThat(response.aiGenerated()).isTrue();
		assertThat(response.extractiveFallback()).isFalse();
		assertThat(response.citations()).containsExactly(
				new QueryService.CitationResponse("42", "knowledgebridge/42", "Travel Policy", "Policy"),
				new QueryService.CitationResponse("not-a-number", "knowledgebridge/not-a-number", null, null));
		assertThat(response.usage()).isEqualTo(new QueryService.UsageResponse("openrouter:model", 12L, 8L,
				new BigDecimal("0.001")));
	}

	@Test
	void searchUsesTheRequestedLimitAndReportsDegradedRetrieval() {
		when(gbrainClient.search(any())).thenReturn(new GbrainSearchResult(List.of(),
				new GbrainRetrieval(true, false, false, List.of())));

		QueryService.SearchResponse response = queryService.search("What is unavailable?", 3);

		assertThat(response.hits()).isEmpty();
		assertThat(response.retrieval()).isEqualTo(new QueryService.RetrievalResponse(true, false, true));
		verify(gbrainClient).search(new com.capstone.knowledgebridge.gbrain.model.GbrainSearchRequest(
				"What is unavailable?", 3, null));
	}

	@Test
	void synthesisMarksExtractiveFallbackWithoutUsage() {
		when(knowledgeItemRepository.findAllById(List.of())).thenReturn(List.of());
		when(gbrainClient.synthesize(any())).thenReturn(new GbrainSynthesisResult("Excerpt",
				GbrainSynthesisResult.Status.EXTRACTIVE_FALLBACK, List.of(), List.of("No policy date"), null,
				List.of("LLM_OUTPUT_TRUNCATED")));

		QueryService.SynthesisResponse response = queryService.synthesize("When was the policy updated?");

		assertThat(response.aiGenerated()).isFalse();
		assertThat(response.extractiveFallback()).isTrue();
		assertThat(response.usage()).isNull();
		assertThat(response.gaps()).containsExactly("No policy date");
		assertThat(response.warnings()).containsExactly("LLM_OUTPUT_TRUNCATED");
	}

}
