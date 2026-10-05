package com.capstone.knowledgebridge.gbrain.mcp;

import tools.jackson.databind.JsonNode;

/** JSON-RPC 2.0 response envelope; exactly one of {@code result} and {@code error} is expected. */
public record JsonRpcResponse(String jsonrpc, JsonNode id, JsonNode result, JsonRpcError error) {
}
