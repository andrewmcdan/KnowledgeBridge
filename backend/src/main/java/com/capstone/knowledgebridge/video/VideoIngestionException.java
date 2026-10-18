package com.capstone.knowledgebridge.video;

public class VideoIngestionException extends RuntimeException {

	private final String code;

	public VideoIngestionException(String code, String message) {
		super(message);
		this.code = code;
	}

	public VideoIngestionException(String code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
	}

	public String getCode() {
		return code;
	}
}
