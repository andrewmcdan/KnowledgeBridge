package com.capstone.knowledgebridge.jira;

/** The only exception the Jira adapter throws. Messages never contain credentials or work item content. */
public class JiraException extends RuntimeException {

	private final JiraErrorCode code;

	public JiraException(JiraErrorCode code, String message) {
		this(code, message, null);
	}

	public JiraException(JiraErrorCode code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
	}

	public JiraErrorCode code() {
		return code;
	}

}
