package com.capstone.knowledgebridge.jira;

/** Distinguishes the transport byte limit from malformed Jira content, so search can shrink its page. */
final class JiraResponseTooLargeException extends JiraException {
	JiraResponseTooLargeException(long limit) {
		super(JiraErrorCode.PROTOCOL, "Jira MCP response exceeded " + limit + " bytes");
	}
}
