package com.capstone.knowledgebridge.gbrain.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;

/** JSON-RPC 2.0 request; a {@code null} id makes it a notification that expects no response. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcRequest(String jsonrpc, Long id, String method, Object params) {

	public static final String VERSION = "2.0";

	public static JsonRpcRequest request(long id, String method, Object params) {
		return new JsonRpcRequest(VERSION, id, method, params);
	}

	public static JsonRpcRequest notification(String method) {
		return new JsonRpcRequest(VERSION, null, method, null);
	}

}
