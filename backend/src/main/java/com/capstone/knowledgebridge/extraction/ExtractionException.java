package com.capstone.knowledgebridge.extraction;

/** The uploaded file could not be read as its claimed format (corrupt, encrypted, or a different file type). */
public class ExtractionException extends RuntimeException {

	public ExtractionException(String message, Throwable cause) {
		super(message, cause);
	}

}
