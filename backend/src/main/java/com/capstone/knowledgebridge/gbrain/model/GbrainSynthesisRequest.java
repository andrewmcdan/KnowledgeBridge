package com.capstone.knowledgebridge.gbrain.model;

/**
 * A question for gbrain's LLM-backed {@code synthesize}. Each call costs money and can take minutes, so it is never
 * retried automatically.
 *
 * @param question
 *            the question; surrounding whitespace is removed
 */
public record GbrainSynthesisRequest(String question) {

	public static final int MAX_QUESTION_LENGTH = 2000;

	public GbrainSynthesisRequest {
		question = GbrainSearchRequest.requireQuestionText("synthesis question", question, MAX_QUESTION_LENGTH);
	}

}
