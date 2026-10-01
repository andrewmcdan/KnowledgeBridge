package com.capstone.knowledgebridge.gbrain;

import static com.capstone.knowledgebridge.gbrain.MockGbrainServer.JSON;
import static com.capstone.knowledgebridge.gbrain.MockGbrainServer.fixture;
import static com.capstone.knowledgebridge.gbrain.MockGbrainServer.result;
import static com.capstone.knowledgebridge.gbrain.MockGbrainServer.toolResult;
import static com.capstone.knowledgebridge.gbrain.MockGbrainServer.toolsList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.capstone.knowledgebridge.gbrain.MockGbrainServer.Response;
import com.capstone.knowledgebridge.gbrain.model.GbrainCapabilities;

import tools.jackson.databind.node.ObjectNode;

class GbrainMcpClientTests {

	private final MockGbrainServer server = new MockGbrainServer();

	private final List<Duration> sleeps = new CopyOnWriteArrayList<>();

	@AfterEach
	void stopServer() {
		server.close();
	}

	@Test
	void discoveryInitializesOnceAndCachesTheToolSurface() {
		GbrainMcpClient client = client(server.properties());

		GbrainCapabilities first = client.discoverCapabilities();
		GbrainCapabilities second = client.discoverCapabilities();

		assertThat(first.serverName()).isEqualTo("gbrain");
		assertThat(first.serverVersion()).isEqualTo("0.50.0.0");
		assertThat(first.protocolVersion()).isEqualTo("2025-03-26");
		assertThat(first.tools()).containsExactlyInAnyOrder(MockGbrainServer.allTools());
		assertThat(first.ready()).isTrue();
		assertThat(second).isEqualTo(first);

		List<String> methods = server.requests("/mcp").stream().map(MockGbrainServer.Request::rpcMethod).toList();
		assertThat(methods).containsExactly("initialize", "notifications/initialized", "tools/list", "tools/list");
		assertThat(server.requests("/token")).hasSize(1);

		MockGbrainServer.Request initialize = server.requests("/mcp").get(0);
		assertThat(initialize.header("Authorization")).isEqualTo("Bearer token-1");
		assertThat(initialize.header("Accept")).contains("application/json", "text/event-stream");
		assertThat(initialize.header("Content-Type")).startsWith("application/json");
		assertThat(initialize.json().path("params").path("protocolVersion").asString()).isEqualTo("2025-03-26");
		assertThat(server.requests("/mcp").get(1).json().has("id")).isFalse();
	}

	@Test
	void pinnedToolFixtureIsMissingRequiredToolsAndCallsFailClosed() {
		server.onMcp("tools/list", request -> Response.sse(fixture("tools-list-response.json", request.rpcId())));
		GbrainMcpClient client = client(server.properties());

		GbrainCapabilities capabilities = client.discoverCapabilities();

		assertThat(capabilities.ready()).isFalse();
		assertThat(capabilities.missingRequiredTools()).containsExactlyInAnyOrder("get_page", "delete_page",
				"restore_page");
		assertThatThrownBy(() -> client.callTool(GbrainTool.GET_PAGE, Map.of("slug", "knowledgebridge/x")))
				.isInstanceOfSatisfying(GbrainException.class,
						exception -> assertThat(exception.code()).isEqualTo(GbrainErrorCode.CONFIGURATION));
		assertThat(server.toolCalls()).isEmpty();
	}

	@Test
	void discoveryFollowsToolListCursors() {
		server.onMcp("tools/list", request -> {
			boolean firstPage = !request.json().path("params").has("cursor");
			return Response.sse(result(request.rpcId(), firstPage
					? toolsList(new String[]{"whoami", "put_page", "get_page"}, "page-2")
					: toolsList(new String[]{"search", "synthesize", "delete_page", "restore_page"}, null)));
		});

		GbrainCapabilities capabilities = client(server.properties()).discoverCapabilities();

		assertThat(capabilities.ready()).isTrue();
		List<MockGbrainServer.Request> pages = server.requests("/mcp")
				.stream()
				.filter(request -> "tools/list".equals(request.rpcMethod()))
				.toList();
		assertThat(pages).hasSize(2);
		assertThat(pages.get(1).json().path("params").path("cursor").asString()).isEqualTo("page-2");
	}

	@Test
	void discoveryRejectsUnboundedPaginationAndMissingToolLists() {
		server.onMcp("tools/list",
				request -> Response.sse(result(request.rpcId(), toolsList(new String[]{"whoami"}, "again"))));
		assertCode(() -> client(server.properties()).discoverCapabilities(), GbrainErrorCode.PROTOCOL);
		assertThat(server.requests("/mcp").stream().filter(request -> "tools/list".equals(request.rpcMethod())))
				.hasSize(GbrainMcpClient.MAX_TOOL_PAGES);

		server.onMcp("tools/list", request -> Response.sse(result(request.rpcId(), JSON.createObjectNode())));
		assertCode(() -> client(server.properties()).discoverCapabilities(), GbrainErrorCode.PROTOCOL);
	}

	@ParameterizedTest
	@CsvSource({
			"2024-11-05, gbrain, 0.50.0.0, PROTOCOL",
			"2025-03-26, other, 0.50.0.0, PROTOCOL",
			"2025-03-26, gbrain, 0.51.0.0, CONFIGURATION"})
	void initializeValidatesProtocolServerAndPinnedVersion(String protocol, String name, String version,
			GbrainErrorCode expected) {
		server.onMcp("initialize", request -> {
			ObjectNode response = (ObjectNode) fixture("initialize-response.json", request.rpcId());
			ObjectNode result = (ObjectNode) response.get("result");
			result.put("protocolVersion", protocol);
			((ObjectNode) result.get("serverInfo")).put("name", name).put("version", version);
			return Response.sse(response);
		});

		assertCode(() -> client(server.properties()).discoverCapabilities(), expected);
		assertThat(server.requests("/mcp")).hasSize(1);
	}

	@Test
	void initializeRejectsMissingServerInfo() {
		server.onMcp("initialize", request -> Response.sse(result(request.rpcId(),
				JSON.createObjectNode().put("protocolVersion", "2025-03-26"))));

		assertCode(() -> client(server.properties()).discoverCapabilities(), GbrainErrorCode.PROTOCOL);
	}

	@Test
	void callToolReturnsPayloadAndRetrievalMetadata() {
		server.onMcp("tools/call:search", request -> Response.sse(fixture("search-response.json", request.rpcId())));
		GbrainMcpClient client = client(server.properties());

		GbrainToolResult result = client.callTool(GbrainTool.SEARCH, Map.of("query", "birds", "limit", 5));

		assertThat(result.payload().get(0).path("slug").asString())
				.isEqualTo("knowledgebridge/phase-1-protocol-spike");
		assertThat(result.metadata().path("retrieval").path("vector_enabled").asBoolean()).isTrue();
		MockGbrainServer.Request call = server.toolCalls().get(0);
		assertThat(call.json().path("params").path("name").asString()).isEqualTo("search");
		assertThat(call.json().path("params").path("arguments").path("query").asString()).isEqualTo("birds");
		assertThat(call.json().path("jsonrpc").asString()).isEqualTo("2.0");
	}

	@Test
	void callToolWithoutMetadataReturnsAnEmptyObject() {
		server.onMcp("tools/call:put_page",
				request -> Response.sse(fixture("put-page-response.json", request.rpcId())));

		GbrainToolResult result = client(server.properties()).callTool(GbrainTool.PUT_PAGE,
				Map.of("slug", "knowledgebridge/x", "content", "# x"));

		assertThat(result.payload().path("status").asString()).isEqualTo("created_or_updated");
		assertThat(result.metadata().isObject()).isTrue();
		assertThat(result.metadata().isEmpty()).isTrue();
	}

	@ParameterizedTest
	@CsvSource({
			"page_not_found, NOT_FOUND",
			"not_found, NOT_FOUND",
			"invalid_params, VALIDATION",
			"invalid_request, VALIDATION",
			"permission_denied, UNAUTHORIZED",
			"scope_denied, UNAUTHORIZED",
			"insufficient_scope, UNAUTHORIZED",
			"source_binding_required, UNAUTHORIZED",
			"missing_source_scope, UNAUTHORIZED",
			"rate_limited, RATE_LIMITED",
			"unavailable, UNAVAILABLE",
			"embedding_failed, UNAVAILABLE",
			"database_error, UNAVAILABLE",
			"unknown_tool, CONFIGURATION",
			"unknown_operation, CONFIGURATION",
			"config_error, CONFIGURATION",
			"storage_error, ENGINE"})
	void toolErrorsAreClassifiedFromGbrainErrorCodes(String upstream, GbrainErrorCode expected) {
		server.onMcp("tools/call:delete_page", request -> Response.sse(toolResult(request.rpcId(),
				"{\"error\":\"" + upstream + "\",\"message\":\"secret page content\"}", true)));

		assertThatThrownBy(() -> client(server.properties()).callTool(GbrainTool.DELETE_PAGE, Map.of()))
				.isInstanceOfSatisfying(GbrainException.class, exception -> {
					assertThat(exception.code()).isEqualTo(expected);
					assertThat(exception.upstreamCode()).contains(upstream);
					assertThat(exception.getMessage()).doesNotContain("secret page content");
				});
		assertThat(server.toolCalls()).hasSize(1);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"plain text failure",
			"{\"message\":\"no code\"}",
			"{\"error\":42}"})
	void toolErrorsWithoutAnUpstreamCodeAreEngineFailures(String text) {
		server.onMcp("tools/call:restore_page", request -> Response.sse(toolResult(request.rpcId(), text, true)));

		assertThatThrownBy(() -> client(server.properties()).callTool(GbrainTool.RESTORE_PAGE, Map.of()))
				.isInstanceOfSatisfying(GbrainException.class, exception -> {
					assertThat(exception.code()).isEqualTo(GbrainErrorCode.ENGINE);
					assertThat(exception.upstreamCode()).isEmpty();
				});
	}

	@Test
	void malformedToolResultsAreProtocolFailures() {
		GbrainMcpClient client = client(server.properties());

		server.onMcp("tools/call:whoami", request -> Response.sse(toolResult(request.rpcId(), "not json", false)));
		assertCode(() -> client.callTool(GbrainTool.WHOAMI, Map.of()), GbrainErrorCode.PROTOCOL);

		server.onMcp("tools/call:whoami", request -> Response.sse(result(request.rpcId(), JSON.createObjectNode())));
		assertCode(() -> client.callTool(GbrainTool.WHOAMI, Map.of()), GbrainErrorCode.PROTOCOL);

		server.onMcp("tools/call:whoami", request -> {
			ObjectNode toolResult = JSON.createObjectNode();
			toolResult.putArray("content").addObject().put("type", "image").put("text", "{}");
			toolResult.withArray("content").addObject().put("type", "text");
			return Response.sse(result(request.rpcId(), toolResult));
		});
		assertCode(() -> client.callTool(GbrainTool.WHOAMI, Map.of()), GbrainErrorCode.PROTOCOL);
	}

	@Test
	void rejectedTokenIsRefreshedOnceEvenForWrites() {
		AtomicInteger calls = new AtomicInteger();
		server.onMcp("tools/call:put_page", request -> calls.incrementAndGet() == 1
				? Response.empty(401)
				: Response.sse(fixture("put-page-response.json", request.rpcId())));
		GbrainMcpClient client = client(server.properties());

		client.callTool(GbrainTool.PUT_PAGE, Map.of());

		assertThat(server.requests("/token")).hasSize(2);
		assertThat(server.toolCalls()).extracting(request -> request.header("Authorization"))
				.containsExactly("Bearer token-1", "Bearer token-2");
		assertThat(sleeps).isEmpty();
	}

	@Test
	void repeatedTokenRejectionIsUnauthorized() {
		server.onMcp("tools/call:search", request -> Response.empty(401));

		assertCode(() -> client(server.properties()).callTool(GbrainTool.SEARCH, Map.of()),
				GbrainErrorCode.UNAUTHORIZED);
		assertThat(server.toolCalls()).hasSize(2);
	}

	@Test
	void safeReadsRetryTransientFailuresWithExponentialBackoff() {
		server.onMcp("tools/call:get_page", request -> Response.empty(503));

		assertCode(() -> client(server.properties()).callTool(GbrainTool.GET_PAGE, Map.of()),
				GbrainErrorCode.UNAVAILABLE);
		assertThat(server.toolCalls()).hasSize(3);
		assertThat(sleeps).containsExactly(Duration.ofMillis(250), Duration.ofMillis(500));
	}

	@Test
	void safeReadsHonourRetryAfter() {
		AtomicInteger calls = new AtomicInteger();
		server.onMcp("tools/call:search", request -> calls.incrementAndGet() == 1
				? Response.empty(429).withHeader("Retry-After", "2")
				: Response.sse(fixture("search-response.json", request.rpcId())));

		client(server.properties()).callTool(GbrainTool.SEARCH, Map.of());

		assertThat(sleeps).containsExactly(Duration.ofSeconds(2));
	}

	@Test
	void retryAfterBeyondTheBackoffCapFailsImmediately() {
		server.onMcp("tools/call:search", request -> Response.empty(429).withHeader("Retry-After", "60"));

		assertThatThrownBy(() -> client(server.properties()).callTool(GbrainTool.SEARCH, Map.of()))
				.isInstanceOfSatisfying(GbrainException.class, exception -> {
					assertThat(exception.code()).isEqualTo(GbrainErrorCode.RATE_LIMITED);
					assertThat(exception.retryAfter()).contains(Duration.ofSeconds(60));
				});
		assertThat(server.toolCalls()).hasSize(1);
		assertThat(sleeps).isEmpty();
	}

	@Test
	void computedBackoffBeyondTheCapStopsRetrying() {
		server.onMcp("tools/call:search", request -> Response.empty(502));

		assertCode(() -> client(server.properties(true, 5, Duration.ofMillis(300))).callTool(GbrainTool.SEARCH,
				Map.of()), GbrainErrorCode.UNAVAILABLE);
		assertThat(server.toolCalls()).hasSize(2);
		assertThat(sleeps).containsExactly(Duration.ofMillis(250));
	}

	@Test
	void writesAndSynthesisAreNeverRetriedAutomatically() {
		server.onMcp("tools/call:put_page", request -> Response.empty(429).withHeader("Retry-After", "1"));
		server.onMcp("tools/call:synthesize", request -> Response.empty(503));
		GbrainMcpClient client = client(server.properties());

		assertCode(() -> client.callTool(GbrainTool.PUT_PAGE, Map.of()), GbrainErrorCode.RATE_LIMITED);
		assertCode(() -> client.callTool(GbrainTool.SYNTHESIZE, Map.of()), GbrainErrorCode.UNAVAILABLE);
		assertThat(server.toolCalls()).hasSize(2);
		assertThat(sleeps).isEmpty();
	}

	@Test
	void ambiguousWriteTimeoutIsReportedWithoutRetry() {
		server.onMcp("tools/call:put_page",
				request -> Response.sse(fixture("put-page-response.json", request.rpcId()))
						.withDelay(Duration.ofSeconds(2)));

		assertCode(() -> client(server.properties()).callTool(GbrainTool.PUT_PAGE, Map.of()),
				GbrainErrorCode.TIMEOUT);
		assertThat(server.toolCalls()).hasSize(1);
	}

	@Test
	void synthesisUsesTheLongerTimeout() {
		server.onMcp("tools/call:synthesize",
				request -> Response.sse(fixture("synthesize-response.json", request.rpcId()))
						.withDelay(Duration.ofSeconds(1)));

		GbrainToolResult result = client(server.properties()).callTool(GbrainTool.SYNTHESIZE,
				Map.of("question", "Which birds audit procurement?"));

		assertThat(result.payload().path("synthesis_status").asString()).isEqualTo("ok");
		assertThat(result.payload().path("sources").get(0).asString())
				.isEqualTo("knowledgebridge/phase-1-protocol-spike");
	}

	@Test
	void oversizedResponsesAreRejectedWithoutRetry() {
		server.onMcp("tools/call:search",
				request -> Response.sse(toolResult(request.rpcId(), "\"" + "x".repeat(70_000) + "\"", false)));

		assertCode(() -> client(server.properties()).callTool(GbrainTool.SEARCH, Map.of()),
				GbrainErrorCode.PROTOCOL);
		assertThat(server.toolCalls()).hasSize(1);
	}

	@Test
	void missingEndpointIsAConfigurationFailure() {
		server.on("/mcp", request -> Response.empty(404));

		assertCode(() -> client(server.properties()).discoverCapabilities(), GbrainErrorCode.CONFIGURATION);
	}

	@Test
	void disabledIntegrationMakesNoRequests() {
		assertCode(() -> client(server.properties(false, 3, Duration.ofSeconds(5))).discoverCapabilities(),
				GbrainErrorCode.CONFIGURATION);
		assertThat(server.requests()).isEmpty();
	}

	@Test
	void unreachableServerIsUnavailable() {
		GbrainProperties properties = server.properties();
		server.close();

		assertCode(() -> client(properties).discoverCapabilities(), GbrainErrorCode.UNAVAILABLE);
	}

	@Test
	void interruptedBackoffStopsRetrying() {
		server.onMcp("tools/call:search", request -> Response.empty(503));
		GbrainProperties properties = server.properties();
		GbrainMcpClient client = new GbrainMcpClient(properties,
				new GbrainTokenProvider(properties, JSON, Clock.systemUTC()), JSON, duration -> {
					throw new InterruptedException();
				});

		try {
			assertCode(() -> client.callTool(GbrainTool.SEARCH, Map.of()), GbrainErrorCode.UNAVAILABLE);
			assertThat(Thread.currentThread().isInterrupted()).isTrue();
		} finally {
			Thread.interrupted();
		}
		assertThat(server.toolCalls()).hasSize(1);
	}

	private GbrainMcpClient client(GbrainProperties properties) {
		return new GbrainMcpClient(properties, new GbrainTokenProvider(properties, JSON, Clock.systemUTC()), JSON,
				sleeps::add);
	}

	static void assertCode(ThrowingCallable call, GbrainErrorCode expected) {
		assertThatThrownBy(call).isInstanceOfSatisfying(GbrainException.class,
				exception -> assertThat(exception.code()).isEqualTo(expected));
	}

}
