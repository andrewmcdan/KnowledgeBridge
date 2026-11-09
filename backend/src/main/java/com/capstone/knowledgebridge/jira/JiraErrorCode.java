package com.capstone.knowledgebridge.jira;

/** Application-owned classification of Jira failures, so callers never branch on Atlassian statuses or messages. */
public enum JiraErrorCode {

	/** The integration is disabled or its connection settings are missing. */
	NOT_CONFIGURED,

	/** Jira rejected the API token or the token lacks a required scope. */
	UNAUTHORIZED,

	/** The requested Jira resource does not exist or is not visible to the token's user. */
	NOT_FOUND,

	/** Jira rejected the tool arguments. */
	VALIDATION,

	/** HTTP 429 or an equivalent tool-level rate limit. */
	RATE_LIMITED,

	/** The response did not arrive in time. */
	TIMEOUT,

	/** Connection failure or an upstream server error. */
	UNAVAILABLE,

	/** A malformed MCP response or an unsupported payload. */
	PROTOCOL

}
