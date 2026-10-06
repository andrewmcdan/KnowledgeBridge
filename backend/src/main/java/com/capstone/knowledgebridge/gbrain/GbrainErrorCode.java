package com.capstone.knowledgebridge.gbrain;

/**
 * Stable, application-owned classification of gbrain failures. Callers branch on these values instead of on HTTP
 * statuses, JSON-RPC codes, or gbrain's own error strings.
 */
public enum GbrainErrorCode {

	/** Missing or invalid URL, credentials, server contract, or required capability. */
	CONFIGURATION,

	/** Rejected access token or insufficient scope. */
	UNAUTHORIZED,

	/** A page or resource required by the operation does not exist. */
	NOT_FOUND,

	/** gbrain rejected the slug, content, or tool arguments. */
	VALIDATION,

	/** HTTP 429 or an equivalent tool-level rate limit. */
	RATE_LIMITED,

	/** The response did not arrive in time; the outcome of a write is unknown. */
	TIMEOUT,

	/** Connection failure, health failure, or transient upstream failure. */
	UNAVAILABLE,

	/** Malformed JSON-RPC/MCP response or an unsupported contract. */
	PROTOCOL,

	/** A valid gbrain error not represented by another code. */
	ENGINE

}
