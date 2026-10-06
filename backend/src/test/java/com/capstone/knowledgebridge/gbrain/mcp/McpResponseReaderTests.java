package com.capstone.knowledgebridge.gbrain.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;

import com.capstone.knowledgebridge.gbrain.GbrainErrorCode;
import com.capstone.knowledgebridge.gbrain.GbrainException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class McpResponseReaderTests {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final McpResponseReader reader = new McpResponseReader(JSON);

	@Test
	void readsPlainJsonResponses() {
		JsonNode result = read(MediaType.APPLICATION_JSON, "{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"ok\":true}}",
				7);

		assertThat(result.path("ok").asBoolean()).isTrue();
	}

	@Test
	void readsTheMatchingEventFromAnSseStream() {
		String stream = ": keep-alive\r\n\r\n"
				+ "event: message\r\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}\r\n\r\n"
				+ "data: {\"jsonrpc\":\"2.0\",\"id\":\"7\",\"result\":{\"which\":\"string id\"}}\n\n"
				+ "data: {\"jsonrpc\":\"2.0\",\"id\":6,\"result\":{\"which\":\"other\"}}\n\n"
				+ "id: 1\ndata: {\"jsonrpc\":\"2.0\",\ndata:\"id\":7,\"result\":{\"which\":\"match\"}}";

		JsonNode result = read(MediaType.parseMediaType("text/event-stream;charset=utf-8"), stream, 7);

		assertThat(result.path("which").asString()).isEqualTo("match");
	}

	@Test
	void parsesSseDataFieldsPerSpecification() {
		assertThat(McpResponseReader.eventData("data\ndata:x\ndata: y\n\nevent: only\n\rdata:  z"))
				.containsExactly("\nx\ny", " z");
	}

	@Test
	void rejectsMissingOrUnsupportedContentTypes() {
		assertProtocol(() -> read(null, "{}", 1));
		assertProtocol(() -> read(MediaType.TEXT_PLAIN, "{}", 1));
	}

	@Test
	void rejectsMalformedOrUnmatchedResponses() {
		assertProtocol(() -> read(MediaType.APPLICATION_JSON, "{not json", 1));
		assertProtocol(() -> read(MediaType.APPLICATION_JSON, "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{}}", 1));
		assertProtocol(() -> read(MediaType.APPLICATION_JSON, "{\"jsonrpc\":\"1.0\",\"id\":1,\"result\":{}}", 1));
		assertProtocol(() -> read(MediaType.APPLICATION_JSON, "{\"jsonrpc\":\"2.0\",\"id\":1}", 1));
		assertProtocol(() -> read(MediaType.APPLICATION_JSON, "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null}", 1));
		assertProtocol(() -> read(MediaType.APPLICATION_JSON, "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":\"x\"}", 1));
	}

	@ParameterizedTest
	@CsvSource({
			"-32700, PROTOCOL",
			"-32600, PROTOCOL",
			"-32601, PROTOCOL",
			"-32602, VALIDATION",
			"-32603, ENGINE",
			"-32000, ENGINE"})
	void classifiesJsonRpcErrorsWithoutExposingTheServerMessage(int code, GbrainErrorCode expected) {
		String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":" + code
				+ ",\"message\":\"echoed secret\"}}";

		assertThatThrownBy(() -> read(MediaType.APPLICATION_JSON, body, 1))
				.isInstanceOfSatisfying(GbrainException.class, exception -> {
					assertThat(exception.code()).isEqualTo(expected);
					assertThat(exception.getMessage()).contains(String.valueOf(code)).doesNotContain("echoed secret");
				});
	}

	@Test
	void convertsTypedPayloadsAndRejectsUnexpectedShapes() {
		McpInitializeResult result = reader.convert(JSON.readTree(
				"{\"protocolVersion\":\"2025-03-26\",\"serverInfo\":{\"name\":\"gbrain\",\"version\":\"1\"},\"extra\":1}"),
				McpInitializeResult.class);

		assertThat(result.serverInfo().name()).isEqualTo("gbrain");
		assertProtocol(() -> reader.convert(JSON.readTree("\"text\""), McpInitializeResult.class));
	}

	@Test
	void serializesNotificationsWithoutAnId() {
		assertThat(JSON.writeValueAsString(JsonRpcRequest.notification("notifications/initialized")))
				.isEqualTo("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
		assertThat(JSON.writeValueAsString(JsonRpcRequest.request(3, "tools/list", Map.of())))
				.isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/list\",\"params\":{}}");
	}

	private JsonNode read(MediaType contentType, String body, long id) {
		return reader.readResult(contentType, body.getBytes(StandardCharsets.UTF_8), id);
	}

	private static void assertProtocol(ThrowingCallable call) {
		assertThatThrownBy(call).isInstanceOfSatisfying(GbrainException.class,
				exception -> assertThat(exception.code()).isEqualTo(GbrainErrorCode.PROTOCOL));
	}

}
