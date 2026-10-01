package com.capstone.knowledgebridge.gbrain;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.capstone.knowledgebridge.gbrain.mcp.JsonRpcRequest;
import com.capstone.knowledgebridge.gbrain.mcp.McpCallToolResult;
import com.capstone.knowledgebridge.gbrain.mcp.McpInitializeResult;
import com.capstone.knowledgebridge.gbrain.mcp.McpResponseReader;
import com.capstone.knowledgebridge.gbrain.mcp.McpToolsListResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainCapabilities;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP Streamable HTTP transport for the pinned gbrain revision. It initializes once, discovers the tool surface lazily
 * and caches it, and invokes tools through JSON-RPC with bounded timeouts, bounded responses, and a conservative retry
 * policy. Logs carry a correlation id, method, tool, attempt, outcome, and duration, never payloads or tokens.
 */
public class GbrainMcpClient {

	static final String PROTOCOL_VERSION = "2025-03-26";

	static final String SERVER_NAME = "gbrain";

	/** Version reported by revision a6be012a; changing it requires rerunning the compatibility smoke test. */
	static final String SERVER_VERSION = "0.50.0.0";

	static final Duration INITIAL_BACKOFF = Duration.ofMillis(250);

	static final int MAX_TOOL_PAGES = 20;

	private static final String ENDPOINT = "gbrain MCP endpoint";

	private static final Set<GbrainErrorCode> TRANSIENT_FAILURES = Set.of(GbrainErrorCode.RATE_LIMITED,
			GbrainErrorCode.TIMEOUT, GbrainErrorCode.UNAVAILABLE);

	private static final Map<String, GbrainErrorCode> TOOL_ERRORS = Map.ofEntries(
			Map.entry("page_not_found", GbrainErrorCode.NOT_FOUND),
			Map.entry("not_found", GbrainErrorCode.NOT_FOUND),
			Map.entry("invalid_params", GbrainErrorCode.VALIDATION),
			Map.entry("invalid_request", GbrainErrorCode.VALIDATION),
			Map.entry("permission_denied", GbrainErrorCode.UNAUTHORIZED),
			Map.entry("scope_denied", GbrainErrorCode.UNAUTHORIZED),
			Map.entry("insufficient_scope", GbrainErrorCode.UNAUTHORIZED),
			Map.entry("source_binding_required", GbrainErrorCode.UNAUTHORIZED),
			Map.entry("missing_source_scope", GbrainErrorCode.UNAUTHORIZED),
			Map.entry("rate_limited", GbrainErrorCode.RATE_LIMITED),
			Map.entry("unavailable", GbrainErrorCode.UNAVAILABLE),
			Map.entry("embedding_failed", GbrainErrorCode.UNAVAILABLE),
			Map.entry("database_error", GbrainErrorCode.UNAVAILABLE),
			Map.entry("unknown_tool", GbrainErrorCode.CONFIGURATION),
			Map.entry("unknown_operation", GbrainErrorCode.CONFIGURATION),
			Map.entry("config_error", GbrainErrorCode.CONFIGURATION));

	private static final Logger log = LoggerFactory.getLogger(GbrainMcpClient.class);

	private final GbrainProperties properties;

	private final GbrainTokenProvider tokenProvider;

	private final JsonMapper jsonMapper;

	private final McpResponseReader responseReader;

	private final Sleeper sleeper;

	private final RestClient standardClient;

	private final RestClient longRunningClient;

	private final AtomicLong requestIds = new AtomicLong();

	private McpInitializeResult session;

	private volatile GbrainCapabilities capabilities;

	GbrainMcpClient(GbrainProperties properties, GbrainTokenProvider tokenProvider, JsonMapper jsonMapper,
			Sleeper sleeper) {
		this.properties = properties;
		this.tokenProvider = tokenProvider;
		this.jsonMapper = jsonMapper;
		this.responseReader = new McpResponseReader(jsonMapper);
		this.sleeper = sleeper;
		this.standardClient = GbrainHttpSupport.restClient(properties.connectTimeout(), properties.readTimeout());
		this.longRunningClient = GbrainHttpSupport.restClient(properties.connectTimeout(),
				properties.synthesisTimeout());
	}

	/** Pauses between retries; injectable so tests stay deterministic. */
	@FunctionalInterface
	interface Sleeper {

		void sleep(Duration duration) throws InterruptedException;

	}

	/**
	 * Refreshes the tool surface with {@code tools/list}. Use for startup smoke tests, admin diagnostics, and after a
	 * gbrain upgrade or credential change; ordinary tool calls reuse the cached result.
	 */
	public GbrainCapabilities discoverCapabilities() {
		String correlationId = UUID.randomUUID().toString();
		McpInitializeResult server = initialize(correlationId);
		Set<String> tools = new LinkedHashSet<>();
		String cursor = null;
		for (int page = 0; page < MAX_TOOL_PAGES; page++) {
			Map<String, Object> params = new LinkedHashMap<>();
			if (cursor != null) {
				params.put("cursor", cursor);
			}
			McpToolsListResult result = responseReader
					.convert(send("tools/list", params, true, false, correlationId, null), McpToolsListResult.class);
			if (result.tools() == null) {
				throw new GbrainException(GbrainErrorCode.PROTOCOL, "gbrain returned tools/list without tools");
			}
			result.tools().forEach(tool -> tools.add(tool.name()));
			cursor = result.nextCursor();
			if (cursor == null) {
				Set<String> missing = Arrays.stream(GbrainTool.values())
						.map(GbrainTool::toolName)
						.filter(name -> !tools.contains(name))
						.collect(Collectors.toCollection(LinkedHashSet::new));
				if (!missing.isEmpty()) {
					log.warn("gbrain tool surface is missing required tools {} correlationId={}", missing,
							correlationId);
				}
				GbrainCapabilities discovered = new GbrainCapabilities(server.serverInfo().name(),
						server.serverInfo().version(), server.protocolVersion(), tools, missing);
				capabilities = discovered;
				return discovered;
			}
		}
		throw new GbrainException(GbrainErrorCode.PROTOCOL,
				"gbrain tools/list exceeded " + MAX_TOOL_PAGES + " pages");
	}

	/**
	 * Invokes one tool. Fails closed with {@link GbrainErrorCode#CONFIGURATION} if the tool is not in the discovered
	 * surface, and treats {@code isError} results as failures even though the HTTP and JSON-RPC layers succeeded.
	 */
	GbrainToolResult callTool(GbrainTool tool, Map<String, ?> arguments) {
		GbrainCapabilities surface = capabilities;
		if (surface == null) {
			surface = discoverCapabilities();
		}
		if (!surface.tools().contains(tool.toolName())) {
			throw new GbrainException(GbrainErrorCode.CONFIGURATION,
					"gbrain tool '" + tool.toolName() + "' is not available to the configured credential");
		}
		String correlationId = UUID.randomUUID().toString();
		JsonNode result = send("tools/call", Map.of("name", tool.toolName(), "arguments", arguments),
				tool.safeToRetry(), tool.longRunning(), correlationId, tool.toolName());
		return toolResult(tool, responseReader.convert(result, McpCallToolResult.class));
	}

	private synchronized McpInitializeResult initialize(String correlationId) {
		if (session != null) {
			return session;
		}
		Map<String, Object> params = Map.of(
				"protocolVersion", PROTOCOL_VERSION,
				"capabilities", Map.of(),
				"clientInfo", Map.of("name", "knowledgebridge-backend", "version", "0.0.1"));
		McpInitializeResult result = responseReader
				.convert(send("initialize", params, true, false, correlationId, null), McpInitializeResult.class);
		if (!PROTOCOL_VERSION.equals(result.protocolVersion()) || result.serverInfo() == null
				|| !SERVER_NAME.equals(result.serverInfo().name())) {
			throw new GbrainException(GbrainErrorCode.PROTOCOL,
					"gbrain did not negotiate MCP protocol " + PROTOCOL_VERSION);
		}
		if (!SERVER_VERSION.equals(result.serverInfo().version())) {
			throw new GbrainException(GbrainErrorCode.CONFIGURATION, "gbrain server version "
					+ result.serverInfo().version() + " is not the pinned version " + SERVER_VERSION);
		}
		send("notifications/initialized", null, false, false, correlationId, null);
		session = result;
		return result;
	}

	private GbrainToolResult toolResult(GbrainTool tool, McpCallToolResult result) {
		String text = result.content() == null
				? null
				: result.content()
						.stream()
						.filter(content -> "text".equals(content.type()) && content.text() != null)
						.map(McpCallToolResult.Content::text)
						.findFirst()
						.orElse(null);
		if (text == null) {
			throw new GbrainException(GbrainErrorCode.PROTOCOL,
					"gbrain tool '" + tool.toolName() + "' returned no text content");
		}
		JsonNode payload;
		try {
			payload = jsonMapper.readTree(text);
		} catch (JacksonException exception) {
			if (result.failed()) {
				throw new GbrainException(GbrainErrorCode.ENGINE,
						"gbrain tool '" + tool.toolName() + "' failed", exception);
			}
			throw new GbrainException(GbrainErrorCode.PROTOCOL,
					"gbrain tool '" + tool.toolName() + "' returned malformed JSON", exception);
		}
		if (result.failed()) {
			String upstreamCode = payload.path("error").isString() ? payload.path("error").asString() : null;
			GbrainErrorCode code = upstreamCode == null
					? GbrainErrorCode.ENGINE
					: TOOL_ERRORS.getOrDefault(upstreamCode, GbrainErrorCode.ENGINE);
			throw new GbrainException(code,
					"gbrain tool '" + tool.toolName() + "' failed" + (upstreamCode == null ? "" : ": " + upstreamCode),
					upstreamCode, null, null);
		}
		return new GbrainToolResult(payload, result.meta() == null ? jsonMapper.createObjectNode() : result.meta());
	}

	/**
	 * Sends one JSON-RPC message with the retry policy: a rejected token is refreshed once for any call, because gbrain
	 * rejects it before dispatch; other transient failures are retried only when {@code safeToRetry} is set.
	 */
	private JsonNode send(String method, Object params, boolean safeToRetry, boolean longRunning,
			String correlationId, String toolName) {
		if (!properties.enabled()) {
			throw new GbrainException(GbrainErrorCode.CONFIGURATION, "gbrain integration is disabled");
		}
		boolean tokenRefreshed = false;
		int attempt = 1;
		while (true) {
			long started = System.nanoTime();
			try {
				JsonNode result = exchange(method, params, longRunning);
				log.debug("gbrain {} tool={} correlationId={} attempt={} outcome=ok durationMs={}", method, toolName,
						correlationId, attempt, elapsedMillis(started));
				return result;
			} catch (TokenRejectedException rejected) {
				tokenProvider.invalidate(rejected.token);
				if (tokenRefreshed) {
					throw new GbrainException(GbrainErrorCode.UNAUTHORIZED, ENDPOINT + " rejected the access token");
				}
				tokenRefreshed = true;
				log.debug("gbrain {} tool={} correlationId={} refreshing rejected access token", method, toolName,
						correlationId);
			} catch (GbrainException failure) {
				Duration delay = retryDelay(failure, safeToRetry, attempt);
				log.warn("gbrain {} tool={} correlationId={} attempt={} outcome={} durationMs={} retry={}", method,
						toolName, correlationId, attempt, failure.code(), elapsedMillis(started), delay != null);
				if (delay == null) {
					throw failure;
				}
				pause(delay);
				attempt++;
			}
		}
	}

	private JsonNode exchange(String method, Object params, boolean longRunning) {
		Long id = params == null ? null : requestIds.incrementAndGet();
		JsonRpcRequest request = id == null
				? JsonRpcRequest.notification(method)
				: JsonRpcRequest.request(id, method, params);
		byte[] body = jsonMapper.writeValueAsBytes(request);
		String token = tokenProvider.accessToken();
		try {
			return (longRunning ? longRunningClient : standardClient).post()
					.uri(properties.mcpUrl())
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
					.headers(headers -> headers.setBearerAuth(token))
					.body(body)
					.exchange((httpRequest, response) -> {
						if (response.getStatusCode().value() == 401) {
							throw new TokenRejectedException(token);
						}
						GbrainHttpSupport.requireSuccess(response, ENDPOINT);
						byte[] bytes = GbrainHttpSupport.readBounded(response.getBody(),
								properties.maxResponseSize().toBytes());
						return id == null
								? null
								: responseReader.readResult(response.getHeaders().getContentType(), bytes, id);
					});
		} catch (ResourceAccessException exception) {
			throw GbrainHttpSupport.transportFailure(exception, ENDPOINT);
		}
	}

	private Duration retryDelay(GbrainException failure, boolean safeToRetry, int attempt) {
		if (!safeToRetry || attempt >= properties.retryMaxAttempts()
				|| !TRANSIENT_FAILURES.contains(failure.code())) {
			return null;
		}
		Duration delay = failure.retryAfter().orElse(INITIAL_BACKOFF.multipliedBy(1L << (attempt - 1)));
		// Respect a longer Retry-After by failing now instead of retrying early or blocking the caller.
		return delay.compareTo(properties.retryMaxBackoff()) > 0 ? null : delay;
	}

	private void pause(Duration delay) {
		try {
			sleeper.sleep(delay);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new GbrainException(GbrainErrorCode.UNAVAILABLE, "gbrain retry was interrupted", exception);
		}
	}

	private static long elapsedMillis(long startedNanos) {
		return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
	}

	/** Signals HTTP 401 so the token can be refreshed; never escapes this class. */
	private static final class TokenRejectedException extends RuntimeException {

		private final String token;

		TokenRejectedException(String token) {
			super(null, null, false, false);
			this.token = token;
		}

	}

}
