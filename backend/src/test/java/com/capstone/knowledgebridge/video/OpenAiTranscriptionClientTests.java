package com.capstone.knowledgebridge.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class OpenAiTranscriptionClientTests {

	@TempDir
	Path temporaryDirectory;

	@Test
	void sendsMultipartAndReturnsTrimmedText() throws Exception {
		try (Server server = new Server(200, "{\"text\":\"  hello world  \"}")) {
			OpenAiTranscriptionClient client = client("key", server.uri());
			Path audio = Files.writeString(temporaryDirectory.resolve("audio.mp3"), "audio");

			assertThat(client.transcribe(audio)).isEqualTo("hello world");
			assertThat(client.model()).isEqualTo("test-model");
			assertThat(server.requests()).isEqualTo(1);
		}
	}

	@Test
	void requiresANonBlankApiKey() {
		URI uri = URI.create("http://localhost:1");
		Path audio = temporaryDirectory.resolve("audio.mp3");

		assertCode(client(null, uri), audio, "TRANSCRIPTION_CONFIGURATION");
		assertCode(client(" ", uri), audio, "TRANSCRIPTION_CONFIGURATION");
	}

	@Test
	void rejectsOversizedSegmentAndMissingFile() throws Exception {
		Path large = temporaryDirectory.resolve("large.mp3");
		try (FileChannel channel = FileChannel.open(large, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
			channel.position(25L * 1024 * 1024);
			channel.write(ByteBuffer.wrap(new byte[]{1}));
		}
		OpenAiTranscriptionClient client = client("key", URI.create("http://localhost:1"));

		assertCode(client, large, "TRANSCRIPTION_FILE_TOO_LARGE");
		assertCode(client, temporaryDirectory.resolve("missing.mp3"), "TRANSCRIPTION_UNAVAILABLE");
	}

	@Test
	void rejectsNullMissingAndBlankResponseText() throws Exception {
		Path audio = Files.writeString(temporaryDirectory.resolve("audio.mp3"), "audio");
		try (Server server = new Server(204, "")) {
			assertCode(client("key", server.uri()), audio, "TRANSCRIPTION_RESPONSE_INVALID");
		}
		try (Server server = new Server(200, "{}")) {
			assertCode(client("key", server.uri()), audio, "TRANSCRIPTION_RESPONSE_INVALID");
		}
		try (Server server = new Server(200, "{\"text\":\" \"}")) {
			assertCode(client("key", server.uri()), audio, "TRANSCRIPTION_RESPONSE_INVALID");
		}
	}

	@Test
	void mapsRateLimitOtherRejectionsAndNetworkFailure() throws Exception {
		Path audio = Files.writeString(temporaryDirectory.resolve("audio.mp3"), "audio");
		try (Server server = new Server(429, "{}")) {
			assertCode(client("key", server.uri()), audio, "TRANSCRIPTION_RATE_LIMITED");
		}
		try (Server server = new Server(400, "{}")) {
			assertCode(client("key", server.uri()), audio, "TRANSCRIPTION_REJECTED");
		}
		Server stopped = new Server(200, "{}");
		URI uri = stopped.uri();
		stopped.close();
		assertCode(client("key", uri), audio, "TRANSCRIPTION_UNAVAILABLE");
	}

	private static OpenAiTranscriptionClient client(String key, URI baseUrl) {
		TranscriptionProperties properties = new TranscriptionProperties(key, baseUrl, "test-model",
				Duration.ofSeconds(2));
		return new OpenAiTranscriptionClient(RestClient.builder().baseUrl(baseUrl.toString()).build(), properties);
	}

	private static void assertCode(OpenAiTranscriptionClient client, Path path, String code) {
		assertThatThrownBy(() -> client.transcribe(path)).isInstanceOfSatisfying(VideoIngestionException.class,
				exception -> assertThat(exception.getCode()).isEqualTo(code));
	}

	private static final class Server implements AutoCloseable {

		private final HttpServer server;

		private final AtomicInteger requests = new AtomicInteger();

		private final int status;

		private final byte[] response;

		private Server(int status, String response) throws IOException {
			this.status = status;
			this.response = response.getBytes(StandardCharsets.UTF_8);
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/audio/transcriptions", this::handle);
			server.start();
		}

		private void handle(HttpExchange exchange) throws IOException {
			requests.incrementAndGet();
			exchange.getRequestBody().readAllBytes();
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, status == 204 ? -1 : response.length);
			if (status != 204) {
				exchange.getResponseBody().write(response);
			}
			exchange.close();
		}

		private URI uri() {
			return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
		}

		private int requests() {
			return requests.get();
		}

		@Override
		public void close() {
			server.stop(0);
		}
	}
}
