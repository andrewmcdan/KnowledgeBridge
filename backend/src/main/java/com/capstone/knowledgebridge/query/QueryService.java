package com.capstone.knowledgebridge.query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.capstone.knowledgebridge.cache.QueryCacheService;
import com.capstone.knowledgebridge.cache.QueryMode;
import com.capstone.knowledgebridge.gbrain.GbrainClient;
import com.capstone.knowledgebridge.gbrain.model.GbrainSearchHit;
import com.capstone.knowledgebridge.gbrain.model.GbrainSearchRequest;
import com.capstone.knowledgebridge.gbrain.model.GbrainSearchResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainSynthesisRequest;
import com.capstone.knowledgebridge.gbrain.model.GbrainSynthesisResult;
import com.capstone.knowledgebridge.knowledge.KnowledgeItem;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemRepository;
import com.capstone.knowledgebridge.knowledge.KnowledgeRevisionService;
import com.capstone.knowledgebridge.usage.TokenEstimator;
import com.capstone.knowledgebridge.usage.TokenUsage;

@Service
public class QueryService {

	private static final Logger log = LoggerFactory.getLogger(QueryService.class);

	public static final int DEFAULT_SEARCH_LIMIT = 10;

	public record SearchResponse(List<SearchHitResponse> hits, RetrievalResponse retrieval) {
	}

	public record SearchHitResponse(String itemKey, String externalId, String title, String documentType,
			String snippet,
			double score) {
	}

	public record RetrievalResponse(boolean vectorEnabled, boolean projectionReady, boolean degraded) {
	}

	/** cacheHit is true when the answer came from the exact-match cache instead of a new LLM call. */
	public record SynthesisResponse(String answer, boolean aiGenerated, boolean extractiveFallback,
			List<CitationResponse> citations, List<String> gaps, List<String> warnings, TokenUsage usage,
			boolean cacheHit) {

		/**
		 * A cache hit made no LLM call, so it reports zero tokens and cost. The model name is kept because the frontend
		 * requires it whenever usage is present.
		 */
		SynthesisResponse asCacheHit() {
			TokenUsage noCost = new TokenUsage(usage == null ? "unknown" : usage.model(), 0L, 0L,
					BigDecimal.ZERO);
			return new SynthesisResponse(answer, aiGenerated, extractiveFallback, citations, gaps, warnings, noCost,
					true);
		}
	}

	public record CitationResponse(String itemKey, String externalId, String title, String documentType) {
	}

	private final GbrainClient gbrainClient;

	private final KnowledgeItemRepository knowledgeItemRepository;

	private final QueryCacheService queryCache;

	private final KnowledgeRevisionService knowledgeRevisionService;

	public QueryService(GbrainClient gbrainClient, KnowledgeItemRepository knowledgeItemRepository,
			QueryCacheService queryCache, KnowledgeRevisionService knowledgeRevisionService) {
		this.gbrainClient = gbrainClient;
		this.knowledgeItemRepository = knowledgeItemRepository;
		this.queryCache = queryCache;
		this.knowledgeRevisionService = knowledgeRevisionService;
	}

	public SearchResponse search(String question, Integer limit) {
		int requestedLimit = limit == null ? DEFAULT_SEARCH_LIMIT : limit;
		GbrainSearchResult result = gbrainClient.search(new GbrainSearchRequest(question, requestedLimit, null));
		List<SearchHitResponse> hits = result.hits().stream().map(QueryService::toSearchHit).toList();
		return new SearchResponse(hits, new RetrievalResponse(result.retrieval().vectorEnabled(),
				result.retrieval().projectionReady(), !result.retrieval().healthy()));
	}

	/**
	 * Answers from the exact-match cache when this question was already answered at the current knowledge revision;
	 * otherwise asks gbrain. Only LLM answers (aiGenerated) are stored: extractive fallbacks are retried next time, and
	 * gbrain errors throw before anything is stored. The revision is read before the gbrain call, so an answer that
	 * races a knowledge write is stored under the older revision and never served after it.
	 */
	public SynthesisResponse synthesize(String question) {
		long revision = knowledgeRevisionService.current();
		Optional<SynthesisResponse> cached = queryCache.lookup(question, QueryMode.SYNTHESIS, revision,
				SynthesisResponse.class);
		if (cached.isPresent()) {
			return cached.get().asCacheHit();
		}
		SynthesisResponse response = askGbrain(question);
		if (response.aiGenerated()) {
			queryCache.store(question, QueryMode.SYNTHESIS, revision, response);
		} else {
			log.info("Not caching synthesis answer: gbrain returned an extractive fallback");
		}
		return response;
	}

	private SynthesisResponse askGbrain(String question) {
		GbrainSynthesisResult result = gbrainClient.synthesize(new GbrainSynthesisRequest(question));
		Map<String, KnowledgeItem> itemsById = knowledgeItemRepository.findAllById(itemIds(result.citations()))
				.stream()
				.collect(Collectors.toMap(item -> Long.toString(item.getId()), Function.identity()));
		List<CitationResponse> citations = result.citations().stream()
				.map(citation -> toCitation(citation, itemsById.get(citation.itemKey())))
				.toList();
		GbrainSynthesisResult.Usage usage = result.usage();
		TokenUsage responseUsage = null;
		if (usage != null || result.status() == GbrainSynthesisResult.Status.SYNTHESIZED) {
			Long input = usage == null ? null : usage.inputTokens();
			Long output = usage == null ? null : usage.outputTokens();
			long inputCharacters = question.length()
					+ itemsById.values().stream().mapToLong(item -> item.getBodyText().length()).sum();
			responseUsage = new TokenUsage(usage == null || usage.model() == null ? "unknown" : usage.model(),
					input == null ? TokenEstimator.estimate(inputCharacters) : Math.max(0, input),
					output == null ? TokenEstimator.estimate(result.answer().length()) : Math.max(0, output),
					usage == null ? null : usage.estimatedCostUsd(), input == null || output == null);
		}
		return new SynthesisResponse(result.answer(), result.status() == GbrainSynthesisResult.Status.SYNTHESIZED,
				result.status() == GbrainSynthesisResult.Status.EXTRACTIVE_FALLBACK, citations, result.gaps(),
				result.warnings(), responseUsage, false);
	}

	private static List<Long> itemIds(List<GbrainSynthesisResult.GbrainCitation> citations) {
		return citations.stream()
				.map(GbrainSynthesisResult.GbrainCitation::itemKey)
				.map(QueryService::parseItemId)
				.flatMap(java.util.Optional::stream)
				.distinct()
				.toList();
	}

	private static java.util.Optional<Long> parseItemId(String itemKey) {
		try {
			return java.util.Optional.of(Long.parseLong(itemKey));
		} catch (NumberFormatException ex) {
			return java.util.Optional.empty();
		}
	}

	private static SearchHitResponse toSearchHit(GbrainSearchHit hit) {
		return new SearchHitResponse(hit.itemKey(), hit.externalId(), hit.title(), hit.documentType(), hit.snippet(),
				hit.score());
	}

	private static CitationResponse toCitation(GbrainSynthesisResult.GbrainCitation citation, KnowledgeItem item) {
		return new CitationResponse(citation.itemKey(), citation.externalId(), item == null ? null : item.getTitle(),
				item == null ? null : item.getType());
	}

}
