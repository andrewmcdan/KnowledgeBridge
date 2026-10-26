package com.capstone.knowledgebridge.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.capstone.knowledgebridge.cache.QueryCacheService;
import com.capstone.knowledgebridge.cache.QueryMode;
import com.capstone.knowledgebridge.gbrain.GbrainClient;
import com.capstone.knowledgebridge.gbrain.GbrainErrorCode;
import com.capstone.knowledgebridge.gbrain.GbrainException;
import com.capstone.knowledgebridge.gbrain.model.GbrainRetrieval;
import com.capstone.knowledgebridge.gbrain.model.GbrainSearchHit;
import com.capstone.knowledgebridge.gbrain.model.GbrainSearchResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainSynthesisResult;
import com.capstone.knowledgebridge.knowledge.KnowledgeItem;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemRepository;
import com.capstone.knowledgebridge.knowledge.KnowledgeRevisionService;
import com.capstone.knowledgebridge.usage.TokenUsage;

class QueryServiceTests {

	private final GbrainClient gbrainClient = mock(GbrainClient.class);

	private final KnowledgeItemRepository knowledgeItemRepository = mock(KnowledgeItemRepository.class);

	// Mockito's default lookup answer is Optional.empty(), so every test starts on a cache miss.
	private final QueryCacheService queryCache = mock(QueryCacheService.class);

	private final KnowledgeRevisionService knowledgeRevisionService = mock(KnowledgeRevisionService.class);

	private final QueryService queryService = new QueryService(gbrainClient, knowledgeItemRepository, queryCache,
			knowledgeRevisionService);

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
		assertThat(response.usage()).isEqualTo(new TokenUsage("openrouter:model", 12L, 8L,
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
		assertThat(response.cacheHit()).isFalse();
		verify(queryCache, never()).store(anyString(), any(), anyLong(), any());
	}

	@Test
	void synthesisCachesAnLlmAnswerUnderTheRevisionReadBeforeTheCall() {
		when(knowledgeRevisionService.current()).thenReturn(7L);
		when(gbrainClient.synthesize(any())).thenReturn(new GbrainSynthesisResult("Employees need approval.",
				GbrainSynthesisResult.Status.SYNTHESIZED, List.of(), List.of(), null, List.of()));

		QueryService.SynthesisResponse response = queryService.synthesize("What travel approval is needed?");

		assertThat(response.cacheHit()).isFalse();
		verify(queryCache).store("What travel approval is needed?", QueryMode.SYNTHESIS, 7L, response);
	}

	@Test
	void synthesisReturnsACachedAnswerWithoutCallingGbrainAndReportsNoCost() {
		when(knowledgeRevisionService.current()).thenReturn(7L);
		QueryService.SynthesisResponse stored = new QueryService.SynthesisResponse("Employees need approval.", true,
				false, List.of(), List.of(), List.of(),
				new TokenUsage("openrouter:model", 12L, 8L, new BigDecimal("0.001")), false);
		when(queryCache.lookup("What travel approval is needed?", QueryMode.SYNTHESIS, 7L,
				QueryService.SynthesisResponse.class)).thenReturn(Optional.of(stored));

		QueryService.SynthesisResponse response = queryService.synthesize("What travel approval is needed?");

		assertThat(response.cacheHit()).isTrue();
		assertThat(response.answer()).isEqualTo("Employees need approval.");
		assertThat(response.usage())
				.isEqualTo(new TokenUsage("openrouter:model", 0L, 0L, BigDecimal.ZERO));
		verify(gbrainClient, never()).synthesize(any());
		verify(queryCache, never()).store(anyString(), any(), anyLong(), any());
	}

	@Test
	void cachedAnswerWithoutUsageReportsZeroNewTokens() {
		QueryService.SynthesisResponse stored = new QueryService.SynthesisResponse("Answer", true, false, List.of(),
				List.of(), List.of(), null, false);
		when(queryCache.lookup(anyString(), eq(QueryMode.SYNTHESIS), anyLong(),
				eq(QueryService.SynthesisResponse.class))).thenReturn(Optional.of(stored));

		QueryService.SynthesisResponse response = queryService.synthesize("Anything?");

		assertThat(response.cacheHit()).isTrue();
		assertThat(response.usage()).isEqualTo(new TokenUsage("unknown", 0L, 0L, BigDecimal.ZERO));
	}

	@Test
	void synthesisStoresNothingWhenGbrainFails() {
		when(gbrainClient.synthesize(any()))
				.thenThrow(new GbrainException(GbrainErrorCode.UNAVAILABLE, "gbrain is down"));

		assertThatThrownBy(() -> queryService.synthesize("What travel approval is needed?"))
				.isInstanceOf(GbrainException.class);
		verify(queryCache, never()).store(anyString(), any(), anyLong(), any());
	}

	@Test
	void estimatesUseTheQuestionAndUniqueCitedDocumentTextWhenUsageIsAbsent() {
		KnowledgeItem item = new KnowledgeItem("Note", "Note", 1L, "abcdefgh");
		ReflectionTestUtils.setField(item, "id", 42L);
		when(knowledgeItemRepository.findAllById(List.of(42L))).thenReturn(List.of(item));
		var citation = new GbrainSynthesisResult.GbrainCitation("42", "knowledgebridge/42");
		when(gbrainClient.synthesize(any())).thenReturn(new GbrainSynthesisResult("abcdefgh",
				GbrainSynthesisResult.Status.SYNTHESIZED, List.of(citation, citation), List.of(), null, List.of()));
		assertThat(queryService.synthesize("abcd").usage())
				.isEqualTo(new TokenUsage("unknown", 3L, 2L, null, true));
	}

	@Test
	void partialUsageKeepsReportedCountsAndEstimatesOnlyMissingCounts() {
		for (GbrainSynthesisResult.Usage usage : List.of(
				new GbrainSynthesisResult.Usage("model", 10L, null, null),
				new GbrainSynthesisResult.Usage(null, null, 20L, null),
				new GbrainSynthesisResult.Usage("model", -1L, -2L, null))) {
			when(gbrainClient.synthesize(any())).thenReturn(new GbrainSynthesisResult("abcdefgh",
					GbrainSynthesisResult.Status.SYNTHESIZED, List.of(), List.of(), usage, List.of()));
			TokenUsage result = queryService.synthesize("abcd").usage();
			assertThat(result.inputTokens())
					.isEqualTo(usage.inputTokens() == null ? 1L : Math.max(0, usage.inputTokens()));
			assertThat(result.outputTokens())
					.isEqualTo(usage.outputTokens() == null ? 2L : Math.max(0, usage.outputTokens()));
			assertThat(result.estimated()).isEqualTo(usage.inputTokens() == null || usage.outputTokens() == null);
		}
	}

}
