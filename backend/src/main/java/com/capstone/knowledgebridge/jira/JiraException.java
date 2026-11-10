package com.capstone.knowledgebridge.jira;

import java.time.Duration;
import java.util.Optional;

/** The only exception the Jira adapter throws. Messages never contain credentials or work item content. */
public class JiraException extends RuntimeException {

	private final JiraErrorCode code;

	private final Duration retryAfter;

	public JiraException(JiraErrorCode code, String message) {
		this(code, message, (Throwable) null);
	}

	public JiraException(JiraErrorCode code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
		this.retryAfter = null;
	}

	public JiraException(JiraErrorCode code, String message, Duration retryAfter) {
		super(message);
		this.code = code;
		this.retryAfter = retryAfter;
	}

	public JiraErrorCode code() {
		return code;
	}

	/** Delay Jira requested through {@code Retry-After}, when it sent one. */
	public Optional<Duration> retryAfter() {
		return Optional.ofNullable(retryAfter);
	}

}
