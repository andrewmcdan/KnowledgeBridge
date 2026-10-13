package com.capstone.knowledgebridge.query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.capstone.knowledgebridge.gbrain.GbrainClient;
import com.capstone.knowledgebridge.gbrain.model.GbrainSearchHit;
import com.capstone.knowledgebridge.gbrain.model.GbrainSearchRequest;
import com.capstone.knowledgebridge.gbrain.model.GbrainSearchResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainSynthesisRequest;
import com.capstone.knowledgebridge.gbrain.model.GbrainSynthesisResult;
import com.capstone.knowledgebridge.knowledge.KnowledgeItem;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemRepository;

@Service
public class QueryService {

	public static final int DEFAULT_SEARCH_LIMIT = 10;

	public record SearchResponse(List<SearchHitResponse> hits, RetrievalResponse retrieval) {
	}

	public record SearchHitResponse(String itemKey, String externalId, String title, String documentType,
			String snippet,
			double score) {
	}

	public record RetrievalResponse(boolean vectorEnabled, boolean projectionReady, boolean degraded) {
	}

	public record SynthesisResponse(String answer, boolean aiGenerated, boolean extractiveFallback,
			List<CitationResponse> citations, List<String> gaps, List<String> warnings, UsageResponse usage) {
	}

	public record CitationResponse(String itemKey, String externalId, String title, String documentType) {
	}

	public record UsageResponse(String model, Long inputTokens, Long outputTokens, BigDecimal estimatedCostUsd) {
	}

	private final GbrainClient gbrainClient;

	private final KnowledgeItemRepository knowledgeItemRepository;

	public QueryService(GbrainClient gbrainClient, KnowledgeItemRepository knowledgeItemRepository) {
		this.gbrainClient = gbrainClient;
		this.knowledgeItemRepository = knowledgeItemRepository;
	}

	public SearchResponse search(String question, Integer limit) {
		int requestedLimit = limit == null ? DEFAULT_SEARCH_LIMIT : limit;
		GbrainSearchResult result = gbrainClient.search(new GbrainSearchRequest(question, requestedLimit, null));
		List<SearchHitResponse> hits = result.hits().stream().map(QueryService::toSearchHit).toList();
		return new SearchResponse(hits, new RetrievalResponse(result.retrieval().vectorEnabled(),
				result.retrieval().projectionReady(), !result.retrieval().healthy()));
	}

	public SynthesisResponse synthesize(String question) {
		GbrainSynthesisResult result = gbrainClient.synthesize(new GbrainSynthesisRequest(question));
		Map<String, KnowledgeItem> itemsById = knowledgeItemRepository.findAllById(itemIds(result.citations()))
				.stream()
				.collect(Collectors.toMap(item -> Long.toString(item.getId()), Function.identity()));
		List<CitationResponse> citations = result.citations().stream()
				.map(citation -> toCitation(citation, itemsById.get(citation.itemKey())))
				.toList();
		GbrainSynthesisResult.Usage usage = result.usage();
		UsageResponse responseUsage = usage == null
				? null
				: new UsageResponse(usage.model(), usage.inputTokens(), usage.outputTokens(), usage.estimatedCostUsd());
		return new SynthesisResponse(result.answer(), result.status() == GbrainSynthesisResult.Status.SYNTHESIZED,
				result.status() == GbrainSynthesisResult.Status.EXTRACTIVE_FALLBACK, citations, result.gaps(),
				result.warnings(), responseUsage);
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
