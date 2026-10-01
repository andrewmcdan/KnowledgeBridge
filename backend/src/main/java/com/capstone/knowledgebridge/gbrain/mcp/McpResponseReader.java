package com.capstone.knowledgebridge.gbrain.mcp;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.MediaType;

import com.capstone.knowledgebridge.gbrain.GbrainErrorCode;
import com.capstone.knowledgebridge.gbrain.GbrainException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Extracts the JSON-RPC response for one request from a Streamable HTTP body. The pinned gbrain server frames responses
 * as {@code text/event-stream}; plain {@code application/json} is accepted as the protocol allows either.
 */
public final class McpResponseReader {

	private final JsonMapper jsonMapper;

	public McpResponseReader(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	/** Returns the {@code result} member of the response whose id matches, or throws a classified exception. */
	public JsonNode readResult(MediaType contentType, byte[] body, long expectedId) {
		JsonRpcResponse response = findResponse(messages(contentType, body), expectedId);
		if (!JsonRpcRequest.VERSION.equals(response.jsonrpc())) {
			throw protocol("gbrain returned an unsupported JSON-RPC version");
		}
		if (response.error() != null) {
			int code = response.error().code();
			throw new GbrainException(classifyRpcError(code), "gbrain returned JSON-RPC error " + code);
		}
		if (response.result() == null || response.result().isNull()) {
			throw protocol("gbrain returned a JSON-RPC response without a result");
		}
		return response.result();
	}

	public <T> T convert(JsonNode node, Class<T> type) {
		try {
			return jsonMapper.treeToValue(node, type);
		} catch (JacksonException exception) {
			throw new GbrainException(GbrainErrorCode.PROTOCOL,
					"gbrain returned an unexpected " + type.getSimpleName() + " payload", exception);
		}
	}

	static GbrainErrorCode classifyRpcError(int code) {
		return switch (code) {
			// Parse error, invalid request, method not found: the client and server disagree on the protocol.
			case -32700, -32600, -32601 -> GbrainErrorCode.PROTOCOL;
			case -32602 -> GbrainErrorCode.VALIDATION;
			default -> GbrainErrorCode.ENGINE;
		};
	}

	private List<String> messages(MediaType contentType, byte[] body) {
		String text = new String(body, StandardCharsets.UTF_8);
		if (contentType == null) {
			throw protocol("gbrain returned a response without a content type");
		}
		if (MediaType.TEXT_EVENT_STREAM.isCompatibleWith(contentType)) {
			return eventData(text);
		}
		if (MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
			return List.of(text);
		}
		throw protocol("gbrain returned unsupported content type " + contentType);
	}

	private JsonRpcResponse findResponse(List<String> messages, long expectedId) {
		for (String message : messages) {
			JsonNode node;
			try {
				node = jsonMapper.readTree(message);
			} catch (JacksonException exception) {
				throw new GbrainException(GbrainErrorCode.PROTOCOL, "gbrain returned malformed JSON", exception);
			}
			// Skip server notifications and responses to other requests on the same stream.
			JsonNode id = node.path("id");
			if (id.isIntegralNumber() && id.asLong() == expectedId) {
				return convert(node, JsonRpcResponse.class);
			}
		}
		throw protocol("gbrain did not return a response for JSON-RPC id " + expectedId);
	}

	/** Parses Server-Sent Events, joining multi-line {@code data} fields of each event. */
	static List<String> eventData(String stream) {
		List<String> events = new ArrayList<>();
		StringBuilder data = null;
		for (String line : (stream + "\n\n").split("\r\n|\r|\n", -1)) {
			if (line.isEmpty()) {
				if (data != null) {
					events.add(data.toString());
					data = null;
				}
			} else if (line.equals("data") || line.startsWith("data:")) {
				String value = line.length() > 5 ? line.substring(5) : "";
				value = value.startsWith(" ") ? value.substring(1) : value;
				data = data == null ? new StringBuilder(value) : data.append('\n').append(value);
			}
		}
		return events;
	}

	private static GbrainException protocol(String message) {
		return new GbrainException(GbrainErrorCode.PROTOCOL, message);
	}

}
