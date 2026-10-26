package com.capstone.knowledgebridge.usage;

/** Approximate token count: four UTF-16 characters per token, rounded up; not a provider tokenizer. */
public final class TokenEstimator {

	private TokenEstimator() {
	}

	public static long estimate(long characters) {
		return (characters + 3) / 4;
	}
}
