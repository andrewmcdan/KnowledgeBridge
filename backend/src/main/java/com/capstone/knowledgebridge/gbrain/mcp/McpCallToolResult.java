package com.capstone.knowledgebridge.gbrain.mcp;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

/**
 * Result of the MCP {@code tools/call} request. {@code isError} marks a tool failure inside a successful JSON-RPC
 * response; {@code _meta} carries gbrain retrieval metadata such as {@code vector_enabled}.
 */
public record McpCallToolResult(List<Content> content, Boolean isError, @JsonProperty("_meta") JsonNode meta) {

	/** {@code isError} is optional on the wire and defaults to false. */
	public boolean failed() {
		return Boolean.TRUE.equals(isError);
	}

	public record Content(String type, String text) {
	}

}
