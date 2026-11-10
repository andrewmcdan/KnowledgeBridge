package com.capstone.knowledgebridge.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.capstone.knowledgebridge.extraction.ExtractionException;
import com.capstone.knowledgebridge.jira.JiraException;

/**
 * Safe JSON errors for validation, malformed requests, multipart limits, and unexpected failures.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		return new ResponseEntity<>(ApiError.forStatus(status.value()), headers, status);
	}

	/** An upload that can't be ingested: the reason tells the user what went wrong and what to do about it. */
	@ExceptionHandler(ExtractionException.class)
	public ResponseEntity<ApiError> handleExtraction(ExtractionException exception) {
		log.info("Rejected upload: {}", exception.reason(), exception.getCause());
		ExtractionException.Reason reason = exception.reason();
		return ResponseEntity.badRequest().body(new ApiError(reason.code(), reason.message()));
	}

	/** Upstream failures are 502, never 401, so a Jira credential problem doesn't sign the user out. */
	@ExceptionHandler(JiraException.class)
	public ResponseEntity<ApiError> handleJira(JiraException exception) {
		log.warn("Jira request failed: {}: {}", exception.code(), exception.getMessage());
		return switch (exception.code()) {
			case NOT_CONFIGURED -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
					.body(new ApiError("JIRA_NOT_CONFIGURED", "The Jira integration is not configured."));
			case NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.forStatus(404));
			default -> ResponseEntity.status(HttpStatus.BAD_GATEWAY)
					.body(new ApiError("JIRA_UNAVAILABLE", "Jira could not complete the request. Please try again."));
		};
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.forStatus(403));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
		log.error("Unhandled API failure", exception);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.forStatus(500));
	}

}
