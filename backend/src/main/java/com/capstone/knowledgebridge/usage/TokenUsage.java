package com.capstone.knowledgebridge.usage;

import java.math.BigDecimal;

/** Counts supplied by the engine, or local estimates when engine counts are missing. */
public record TokenUsage(String model, Long inputTokens, Long outputTokens, BigDecimal estimatedCostUsd,
		boolean estimated) {
	public TokenUsage(String model, Long inputTokens, Long outputTokens, BigDecimal estimatedCostUsd) {
		this(model, inputTokens, outputTokens, estimatedCostUsd, false);
	}
}
