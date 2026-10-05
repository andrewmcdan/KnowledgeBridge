package com.capstone.knowledgebridge.gbrain;

import java.time.Duration;
import java.util.Optional;

/**
 * The only exception type the gbrain adapter throws. Messages never contain credentials, document content, questions,
 * or answers; {@link #upstreamCode()} carries gbrain's machine-readable error string when one was returned.
 */
public class GbrainException extends RuntimeException {

	private final GbrainErrorCode code;

	private final String upstreamCode;

	private final Duration retryAfter;

	public GbrainException(GbrainErrorCode code, String message) {
		this(code, message, null, null, null);
	}

	public GbrainException(GbrainErrorCode code, String message, Throwable cause) {
		this(code, message, null, null, cause);
	}

	public GbrainException(GbrainErrorCode code, String message, String upstreamCode, Duration retryAfter,
			Throwable cause) {
		super(message, cause);
		this.code = code;
		this.upstreamCode = upstreamCode;
		this.retryAfter = retryAfter;
	}

	public GbrainErrorCode code() {
		return code;
	}

	/** gbrain's own error code, such as {@code page_not_found}, when the failure came from a tool result. */
	public Optional<String> upstreamCode() {
		return Optional.ofNullable(upstreamCode);
	}

	/** Delay requested by the server through {@code Retry-After}, when present. */
	public Optional<Duration> retryAfter() {
		return Optional.ofNullable(retryAfter);
	}

}
