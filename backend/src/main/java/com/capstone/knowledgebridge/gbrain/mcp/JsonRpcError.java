package com.capstone.knowledgebridge.gbrain.mcp;

/** JSON-RPC 2.0 error object. The server-provided message is not propagated because it may echo request content. */
public record JsonRpcError(int code, String message) {
}
