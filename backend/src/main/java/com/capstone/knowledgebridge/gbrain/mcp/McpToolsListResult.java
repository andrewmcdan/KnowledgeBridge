package com.capstone.knowledgebridge.gbrain.mcp;

import java.util.List;

/** Result of the MCP {@code tools/list} request. Input schemas are intentionally not bound. */
public record McpToolsListResult(List<Tool> tools, String nextCursor) {

	public record Tool(String name) {
	}

}
