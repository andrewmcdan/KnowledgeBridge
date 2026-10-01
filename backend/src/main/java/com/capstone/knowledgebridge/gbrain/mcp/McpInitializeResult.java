package com.capstone.knowledgebridge.gbrain.mcp;

/** Result of the MCP {@code initialize} request. */
public record McpInitializeResult(String protocolVersion, ServerInfo serverInfo) {

	public record ServerInfo(String name, String version) {
	}

}
