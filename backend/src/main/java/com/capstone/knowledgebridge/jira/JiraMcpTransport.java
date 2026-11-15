package com.capstone.knowledgebridge.jira;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP over HTTP to Atlassian's server: Basic auth, a session opened on first use and reopened if the server drops it,
 * classification of HTTP, JSON-RPC, and tool-level failures, and retries for rate limits and outages. Every tool this
 * adapter calls is read-only, so a retry can't repeat a side effect.
 */
final class JiraMcpTransport {

	private static final Logger log = LoggerFactory.getLogger(JiraMcpTransport.class);

	/** Timeouts are left out: at a full read timeout per attempt, a retry would hold a request for minutes. */
	private static final Set<JiraErrorCode> TRANSIENT_FAILURES = Set.of(JiraErrorCode.RATE_LIMITED,
			JiraErrorCode.UNAVAILABLE);

	private static final Duration INITIAL_BACKOFF = Duration.ofMillis(500);

	private static final String SESSION_HEADER = "Mcp-Session-Id";

	private static final String PROTOCOL_VERSION = "2025-06-18";

	private static final Map<String, Object> INITIALIZE_PARAMS = Map.of("protocolVersion", PROTOCOL_VERSION,
			"capabilities", Map.of(), "clientInfo", Map.of("name", "knowledgebridge", "version", "1.0"));

	private final JiraProperties properties;

	private final JsonMapper jsonMapper;

	private final RestClient restClient;

	private final Sleeper sleeper;

	private final AtomicLong requestIds = new AtomicLong();

	private String session;

	JiraMcpTransport(JiraProperties properties, JsonMapper jsonMapper, Sleeper sleeper) {
		this.properties = properties;
		this.jsonMapper = jsonMapper;
		this.sleeper = sleeper;
		// The JDK client never re-sends a POST on its own; refusing redirects keeps credentials on this origin.
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.connectTimeout())
				.version(HttpClient.Version.HTTP_1_1)
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
	}

	/** Invokes one tool and returns the {@code data} member of its decoded result. */
	JsonNode callTool(String tool, Map<String, ?> arguments) {
		if (!properties.configured()) {
			throw new JiraException(JiraErrorCode.NOT_CONFIGURED, "Jira integration is not configured");
		}
		Map<String, Object> params = Map.of("name", tool, "arguments", arguments);
		for (int attempt = 1;; attempt++) {
			try {
				return callOnce(tool, params);
			} catch (JiraException failure) {
				Duration delay = retryDelay(failure, attempt);
				if (delay == null) {
					throw failure;
				}
				log.info("Jira tool {} attempt {} failed with {}; retrying in {}", tool, attempt, failure.code(),
						delay);
				pause(delay);
			}
		}
	}

	/** Returns null when the failure should surface, including when Jira asks for a longer wait than we allow. */
	private Duration retryDelay(JiraException failure, int attempt) {
		if (!TRANSIENT_FAILURES.contains(failure.code()) || attempt >= properties.retryMaxAttempts()) {
			return null;
		}
		Duration delay = failure.retryAfter().orElse(INITIAL_BACKOFF.multipliedBy(1L << (attempt - 1)));
		return delay.compareTo(properties.retryMaxBackoff()) > 0 ? null : delay;
	}

	private void pause(Duration delay) {
		try {
			sleeper.sleep(delay);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new JiraException(JiraErrorCode.UNAVAILABLE, "Jira retry was interrupted", exception);
		}
	}

	private JsonNode callOnce(String tool, Map<String, Object> params) {
		for (int attempt = 0; attempt < 2; attempt++) {
			try {
				return data(tool, exchange("tools/call", params, session()).result());
			} catch (SessionRejectedException rejected) {
				clearSession();
			}
		}
		throw protocol("Jira rejected the MCP session twice");
	}

	private synchronized String session() {
		if (session == null) {
			String opened = exchange("initialize", INITIALIZE_PARAMS, null).sessionId();
			if (opened == null) {
				throw protocol("Jira did not issue an MCP session");
			}
			exchange("notifications/initialized", null, opened);
			session = opened;
		}
		return session;
	}

	private synchronized void clearSession() {
		session = null;
	}

	private Reply exchange(String method, Object params, String sessionId) {
		Long id = params == null ? null : requestIds.incrementAndGet();
		Map<String, Object> request = id == null
				? Map.of("jsonrpc", "2.0", "method", method)
				: Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params);
		try {
			return restClient.post()
					.uri(properties.mcpUrl())
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
					.headers(headers -> {
						headers.setBasicAuth(properties.email(), properties.apiToken(), StandardCharsets.UTF_8);
						if (sessionId != null) {
							headers.set(SESSION_HEADER, sessionId);
							headers.set("Mcp-Protocol-Version", PROTOCOL_VERSION);
						}
					})
					.body(jsonMapper.writeValueAsBytes(request))
					.exchange((httpRequest, response) -> {
						int status = response.getStatusCode().value();
						// The MCP specification signals an expired session with 404; Atlassian also uses 400.
						if (sessionId != null && (status == 400 || status == 404)) {
							throw new SessionRejectedException();
						}
						if (!response.getStatusCode().is2xxSuccessful()) {
							throw new JiraException(classify(status), "Jira MCP returned HTTP " + status,
									retryAfter(response.getHeaders()));
						}
						byte[] body = readBounded(response.getBody());
						return new Reply(response.getHeaders().getFirst(SESSION_HEADER),
								id == null ? null : result(response.getHeaders().getContentType(), body, id));
					});
		} catch (ResourceAccessException exception) {
			throw exception.getCause() instanceof HttpTimeoutException
					? new JiraException(JiraErrorCode.TIMEOUT, "Jira MCP timed out", exception)
					: new JiraException(JiraErrorCode.UNAVAILABLE, "Jira MCP is unreachable", exception);
		}
	}

	private byte[] readBounded(InputStream body) throws IOException {
		long limit = properties.maxResponseSize().toBytes();
		byte[] bytes = body.readNBytes(Math.toIntExact(limit + 1));
		if (bytes.length > limit) {
			throw protocol("Jira MCP response exceeded " + limit + " bytes");
		}
		return bytes;
	}

	private JsonNode result(MediaType contentType, byte[] body, long id) {
		String text = new String(body, StandardCharsets.UTF_8);
		List<String> messages = MediaType.TEXT_EVENT_STREAM.isCompatibleWith(contentType)
				? text.lines().filter(line -> line.startsWith("data:")).map(line -> line.substring(5)).toList()
				: List.of(text);
		for (String message : messages) {
			JsonNode node = readJson(message);
			if (node.path("id").asLong(-1) == id) {
				if (!node.path("result").isObject()) {
					throw protocol("Jira MCP returned no result for " + id);
				}
				return node.path("result");
			}
		}
		throw protocol("Jira MCP did not answer request " + id);
	}

	private JsonNode data(String tool, JsonNode result) {
		String text = result.path("content").path(0).path("text").asString("");
		if (result.path("isError").asBoolean(false)) {
			throw toolFailure(tool, text);
		}
		JsonNode data = readJson(text).path("data");
		if (data.isMissingNode()) {
			throw protocol("Jira tool " + tool + " returned no data");
		}
		return data;
	}

	private JiraException toolFailure(String tool, String text) {
		JsonNode error;
		try {
			error = jsonMapper.readTree(text);
		} catch (JacksonException exception) {
			error = jsonMapper.createObjectNode();
		}
		int status = error.path("statusCode").asInt(0);
		// The disabled-API-token refusal is the one observed failure that carries no status code.
		JiraErrorCode code = status == 0 && error.path("message").asString("").contains("permission")
				? JiraErrorCode.UNAUTHORIZED
				: classify(status);
		log.warn("Jira tool {} failed with status {} classified as {}", tool, status, code);
		return new JiraException(code, "Jira tool " + tool + " failed with status " + status);
	}

	private JsonNode readJson(String text) {
		try {
			return jsonMapper.readTree(text);
		} catch (JacksonException exception) {
			throw new JiraException(JiraErrorCode.PROTOCOL, "Jira MCP returned malformed JSON", exception);
		}
	}

	/** Supports the delta-seconds form only; an HTTP date falls back to the client's own backoff. */
	private static Duration retryAfter(HttpHeaders headers) {
		String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
		return value == null || !value.trim().matches("\\d{1,6}")
				? null
				: Duration.ofSeconds(Long.parseLong(value.trim()));
	}

	private static JiraErrorCode classify(int status) {
		return switch (status) {
			case 400 -> JiraErrorCode.VALIDATION;
			case 401, 403 -> JiraErrorCode.UNAUTHORIZED;
			case 404 -> JiraErrorCode.NOT_FOUND;
			case 429 -> JiraErrorCode.RATE_LIMITED;
			default -> status >= 500 ? JiraErrorCode.UNAVAILABLE : JiraErrorCode.PROTOCOL;
		};
	}

	private static JiraException protocol(String message) {
		return new JiraException(JiraErrorCode.PROTOCOL, message);
	}

	@FunctionalInterface
	interface Sleeper {

		void sleep(Duration delay) throws InterruptedException;

	}

	private record Reply(String sessionId, JsonNode result) {
	}

	/** Signals that the server no longer knows this session; never escapes this class. */
	private static final class SessionRejectedException extends RuntimeException {

		SessionRejectedException() {
			super(null, null, false, false);
		}

	}

}
