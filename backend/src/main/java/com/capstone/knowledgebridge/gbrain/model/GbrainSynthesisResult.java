package com.capstone.knowledgebridge.gbrain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * An AI-generated answer and the evidence behind it. Present it as AI-generated, and treat
 * {@link Status#EXTRACTIVE_FALLBACK} as quoted excerpts rather than a reasoned answer.
 *
 * @param answer
 *            answer text; inline citation markers use the gbrain slug, for example {@code [knowledgebridge/<item-key>]}
 * @param status
 *            whether the model composed the answer or gbrain fell back to excerpts
 * @param citations
 *            cited knowledge items in gbrain's order, without duplicates
 * @param gaps
 *            what the model reported it had no data on; empty when it reported none
 * @param usage
 *            best-effort model and token accounting
 * @param warnings
 *            gbrain's compose warnings, such as {@code LLM_OUTPUT_TRUNCATED}
 */
public record GbrainSynthesisResult(String answer, Status status, List<GbrainCitation> citations, List<String> gaps,
		Usage usage, List<String> warnings) {

	public GbrainSynthesisResult {
		citations = List.copyOf(citations);
		gaps = List.copyOf(gaps);
		warnings = List.copyOf(warnings);
	}

	public enum Status {
		/** The model composed the answer from the gathered pages. */
		SYNTHESIZED,
		/** The model call failed; the answer is a digest of retrieved excerpts. */
		EXTRACTIVE_FALLBACK
	}

	/**
	 * One cited knowledge item.
	 *
	 * @param itemKey
	 *            application identity of the cited item
	 * @param externalId
	 *            gbrain page slug, as used in the answer's inline markers
	 */
	public record GbrainCitation(String itemKey, String externalId) {
	}

	/**
	 * Cost accounting as gbrain reports it. Any field is {@code null} when the provider did not report usage or gbrain
	 * has no price for the model.
	 *
	 * @param model
	 *            model route that answered, such as {@code openrouter:anthropic/claude-haiku-4.5}
	 * @param inputTokens
	 *            prompt tokens
	 * @param outputTokens
	 *            completion tokens
	 * @param estimatedCostUsd
	 *            gbrain's price estimate in US dollars
	 */
	public record Usage(String model, Long inputTokens, Long outputTokens, BigDecimal estimatedCostUsd) {
	}

}
