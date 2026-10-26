package com.capstone.knowledgebridge.titles;

/** Safe user-facing naming failure; upstream response bodies and credentials must not be exposed. */
public class TitleGenerationException extends RuntimeException {

	public TitleGenerationException(String message) {
		super(message);
	}
}
