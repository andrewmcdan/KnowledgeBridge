package com.capstone.knowledgebridge.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Spring already turns bean-validation failures and missing multipart parts into 400s on its own. This is only for
 * MaxUploadSizeExceededException, which would otherwise surface as a bare 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<Void> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
		// RFC 9110 renamed 413 from "Payload Too Large" to "Content Too Large"; HttpStatus still has
		// PAYLOAD_TOO_LARGE but it's deprecated in favor of this one.
		return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).build();
	}

}
