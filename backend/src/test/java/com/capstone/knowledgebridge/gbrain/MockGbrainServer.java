package com.capstone.knowledgebridge.gbrain;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Local HTTP server that mimics the pinned gbrain endpoints so tests exercise the real RestClient transport. By default
 * it issues tokens, answers initialize and tools/list like revision a6be012a, and accepts the initialized notification;
 * tests override individual paths or MCP methods.
 */
final class MockGbrainServer implements AutoCloseable {

	static final JsonMapper JSON = JsonMapper.builder().build();

	private final HttpServer server;

	private final ExecutorService executor = Executors.newCachedThreadPool();

	private final List<Request> requests = new CopyOnWriteArrayList<>();

	private final Map<String, Function<Request, Response>> paths = new ConcurrentHashMap<>();

	private final Map<String, Function<Request, Response>> mcpMethods = new ConcurrentHashMap<>();

	private int tokensIssued;

	MockGbrainServer() {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
		server.setExecutor(executor);
		server.createContext("/", this::handle);
		on("/token", request -> Response.json(200, "{\"access_token\":\"token-" + nextToken()
				+ "\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"scope\":\"read write\"}"));
		on("/mcp", this::dispatchMcp);
		onMcp("initialize", request -> Response.sse(fixture("initialize-response.json", request.rpcId())));
		onMcp("notifications/initialized", request -> Response.empty(202));
		onMcp("tools/list", request -> Response.sse(result(request.rpcId(), toolsList(allTools(), null))));
		server.start();
	}

	URI baseUrl() {
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
	}

	GbrainProperties properties() {
		return properties(true, 3, Duration.ofSeconds(5));
	}

	GbrainProperties properties(boolean enabled, int retryMaxAttempts, Duration retryMaxBackoff) {
		return new GbrainProperties(enabled, baseUrl(), "client-id", "client-secret-value",
				baseUrl().resolve("/token"), Duration.ofSeconds(1), Duration.ofMillis(500), Duration.ofSeconds(3),
				DataSize.ofKilobytes(64), retryMaxAttempts, retryMaxBackoff, null);
	}

	void on(String path, Function<Request, Response> handler) {
		paths.put(path, handler);
	}

	/** Overrides an MCP method, or a tool when the key is {@code tools/call:<tool>}. */
	void onMcp(String method, Function<Request, Response> handler) {
		mcpMethods.put(method, handler);
	}

	List<Request> requests() {
		return List.copyOf(requests);
	}

	List<Request> requests(String path) {
		return requests.stream().filter(request -> request.path().equals(path)).toList();
	}

	List<Request> toolCalls() {
		return requests("/mcp").stream().filter(request -> "tools/call".equals(request.rpcMethod())).toList();
	}

	@Override
	public void close() {
		server.stop(0);
		executor.shutdownNow();
	}

	static String[] allTools() {
		return Arrays.stream(GbrainTool.values()).map(GbrainTool::toolName).toArray(String[]::new);
	}

	static JsonNode fixture(String name, long id) {
		try (InputStream input = MockGbrainServer.class.getResourceAsStream("/gbrain/fixtures/" + name)) {
			ObjectNode node = (ObjectNode) JSON.readTree(input);
			node.put("id", id);
			return node;
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	static JsonNode result(long id, JsonNode result) {
		ObjectNode response = JSON.createObjectNode();
		response.put("jsonrpc", "2.0");
		response.put("id", id);
		response.set("result", result);
		return response;
	}

	static JsonNode toolsList(String[] names, String nextCursor) {
		ObjectNode result = JSON.createObjectNode();
		ArrayNode tools = result.putArray("tools");
		for (String name : names) {
			tools.addObject().put("name", name);
		}
		if (nextCursor != null) {
			result.put("nextCursor", nextCursor);
		}
		return result;
	}

	/** A tools/call result whose single text block contains {@code text}. */
	static JsonNode toolResult(long id, String text, boolean isError) {
		ObjectNode result = JSON.createObjectNode();
		result.putArray("content").addObject().put("type", "text").put("text", text);
		if (isError) {
			result.put("isError", true);
		}
		return result(id, result);
	}

	private synchronized int nextToken() {
		return ++tokensIssued;
	}

	private Response dispatchMcp(Request request) {
		String key = "tools/call".equals(request.rpcMethod())
				? "tools/call:" + request.json().path("params").path("name").asString()
				: request.rpcMethod();
		Function<Request, Response> handler = mcpMethods.get(key);
		return handler == null ? Response.empty(500) : handler.apply(request);
	}

	private void handle(HttpExchange exchange) throws IOException {
		try (exchange) {
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			Map<String, String> headers = new LinkedHashMap<>();
			exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values.get(0)));
			Request request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), headers,
					body);
			requests.add(request);
			Function<Request, Response> handler = paths.get(request.path());
			Response response = handler == null ? Response.empty(404) : handler.apply(request);
			if (response.delay() != null) {
				Thread.sleep(response.delay());
			}
			response.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
			byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
			if (response.contentType() != null) {
				exchange.getResponseHeaders().add("Content-Type", response.contentType());
			}
			exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
			if (bytes.length > 0) {
				try (OutputStream output = exchange.getResponseBody()) {
					output.write(bytes);
				}
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		} catch (IOException exception) {
			// The client gave up (timeout tests); nothing to report.
		}
	}

	record Request(String method, String path, Map<String, String> headers, String body) {

		JsonNode json() {
			return JSON.readTree(body);
		}

		String rpcMethod() {
			return json().path("method").asString();
		}

		long rpcId() {
			return json().path("id").asLong();
		}

		String header(String name) {
			return headers.get(name.toLowerCase());
		}

	}

	record Response(int status, String contentType, String body, Map<String, String> headers, Duration delay) {

		static Response sse(JsonNode message) {
			return new Response(200, "text/event-stream", "event: message\ndata: " + message + "\n\n", Map.of(),
					null);
		}

		static Response json(int status, String body) {
			return new Response(status, "application/json", body, Map.of(), null);
		}

		static Response empty(int status) {
			return new Response(status, null, "", Map.of(), null);
		}

		Response withHeader(String name, String value) {
			Map<String, String> merged = new LinkedHashMap<>(headers);
			merged.put(name, value);
			return new Response(status, contentType, body, merged, delay);
		}

		Response withDelay(Duration value) {
			return new Response(status, contentType, body, headers, value);
		}

	}

}
