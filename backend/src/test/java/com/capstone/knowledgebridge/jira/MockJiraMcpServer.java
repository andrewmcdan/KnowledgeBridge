package com.capstone.knowledgebridge.jira;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import org.springframework.util.unit.DataSize;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Local server that behaves like the observed Atlassian MCP endpoint, so tests exercise the real HTTP transport. */
final class MockJiraMcpServer implements AutoCloseable {

	static final JsonMapper JSON = JsonMapper.builder().build();

	record Request(String method, JsonNode body, String authorization, String session) {
	}

	record Response(int status, String contentType, String body, String session, String retryAfter) {

		Response(int status, String contentType, String body, String session) {
			this(status, contentType, body, session, null);
		}

		static Response sse(String json) {
			return new Response(200, "text/event-stream", "event: message\ndata: " + json + "\n\n", null);
		}

		static Response status(int status) {
			return new Response(status, "application/json", "{}", null);
		}

	}

	private final HttpServer server;

	private final ExecutorService executor = Executors.newCachedThreadPool();

	final List<Request> requests = new CopyOnWriteArrayList<>();

	private final Map<String, Function<Request, Response>> methods = new ConcurrentHashMap<>();

	private final Map<String, Function<JsonNode, String>> tools = new ConcurrentHashMap<>();

	private int sessions;

	MockJiraMcpServer() {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
		server.setExecutor(executor);
		server.createContext("/", this::handle);
		onMethod("initialize", request -> new Response(200, "text/event-stream",
				"data: " + result(request, "{}") + "\n\n", "session-" + (++sessions)));
		onMethod("notifications/initialized", request -> Response.status(202));
		onMethod("tools/call", this::callTool);
		server.start();
	}

	JiraProperties properties() {
		return properties(1024 * 1024, Duration.ofSeconds(2));
	}

	JiraProperties retryProperties(int retryMaxAttempts, Duration retryMaxBackoff) {
		return properties(1024 * 1024, Duration.ofSeconds(2), retryMaxAttempts, retryMaxBackoff);
	}

	JiraProperties properties(long maxResponseBytes, Duration readTimeout) {
		return properties(maxResponseBytes, readTimeout, 1, Duration.ofSeconds(1));
	}

	private JiraProperties properties(long maxResponseBytes, Duration readTimeout, int retryMaxAttempts,
			Duration retryMaxBackoff) {
		return new JiraProperties(true, null,
				URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v2/mcp"),
				"https://example.atlassian.net/", "user@example.com", "secret-token", "cloud-1", Duration.ofSeconds(1),
				readTimeout, DataSize.ofBytes(maxResponseBytes), 10, retryMaxAttempts, retryMaxBackoff);
	}

	McpJiraClient client() {
		return client(properties());
	}

	static McpJiraClient client(JiraProperties properties) {
		return new McpJiraClient(new JiraMcpTransport(properties, JSON, delay -> {
		}), properties);
	}

	void onMethod(String method, Function<Request, Response> handler) {
		methods.put(method, handler);
	}

	/** Answers a tool, or an {@code executeRead} operation by its name, with the text of a successful result. */
	void onTool(String tool, Function<JsonNode, String> text) {
		tools.put(tool, text);
	}

	void onToolError(String tool, String errorText) {
		onMethod("tools/call", request -> {
			if (tool.equals(toolName(request))) {
				return Response.sse(toolResult(request, errorText, true));
			}
			return callTool(request);
		});
	}

	static String fixture(String name) {
		try {
			return Files.readString(Path.of("src/test/resources/jira/mcp/" + name));
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	static String result(Request request, String resultJson) {
		return "{\"jsonrpc\":\"2.0\",\"id\":" + request.body().path("id").asLong() + ",\"result\":" + resultJson + "}";
	}

	static String toolResult(Request request, String text, boolean error) {
		return result(request, JSON.createObjectNode().put("isError", error)
				.set("content",
						JSON.createArrayNode().add(JSON.createObjectNode().put("type", "text").put("text", text)))
				.toString());
	}

	private Response callTool(Request request) {
		String text = tools.get(toolName(request)).apply(request.body().path("params").path("arguments"));
		return Response.sse(toolResult(request, text, false));
	}

	private static String toolName(Request request) {
		JsonNode params = request.body().path("params");
		String name = params.path("name").asString();
		return "executeRead".equals(name) ? params.path("arguments").path("name").asString() : name;
	}

	private void handle(HttpExchange exchange) throws IOException {
		JsonNode body = JSON.readTree(exchange.getRequestBody().readAllBytes());
		Request request = new Request(body.path("method").asString(), body,
				exchange.getRequestHeaders().getFirst("Authorization"),
				exchange.getRequestHeaders().getFirst("Mcp-Session-Id"));
		requests.add(request);
		Response response = methods.get(request.method()).apply(request);
		if (response.contentType() != null) {
			exchange.getResponseHeaders().set("Content-Type", response.contentType());
		}
		if (response.retryAfter() != null) {
			exchange.getResponseHeaders().set("Retry-After", response.retryAfter());
		}
		if (response.session() != null) {
			exchange.getResponseHeaders().set("Mcp-Session-Id", response.session());
		}
		byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
		if (bytes.length > 0) {
			exchange.getResponseBody().write(bytes);
		}
		exchange.close();
	}

	@Override
	public void close() {
		server.stop(0);
		executor.shutdownNow();
	}

}
