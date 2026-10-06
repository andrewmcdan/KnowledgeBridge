package com.capstone.knowledgebridge.gbrain;

import static com.capstone.knowledgebridge.gbrain.MockGbrainServer.JSON;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.capstone.knowledgebridge.gbrain.MockGbrainServer.Response;
import com.capstone.knowledgebridge.gbrain.model.GbrainHealth;

class GbrainHealthClientTests {

	private final MockGbrainServer server = new MockGbrainServer();

	@AfterEach
	void stopServer() {
		server.close();
	}

	@Test
	void reportsUpWithoutCredentials() {
		server.on("/health",
				request -> Response.json(200, "{\"status\":\"ok\",\"version\":\"0.60.37.0\",\"engine\":\"postgres\"}"));

		assertThat(check()).isEqualTo(GbrainHealth.up("0.60.37.0"));
		assertThat(server.requests("/health").get(0).header("Authorization")).isNull();
		assertThat(server.requests("/token")).isEmpty();
	}

	@Test
	void reportsUpWhenVersionIsAbsent() {
		server.on("/health", request -> Response.json(200, "{\"status\":\"ok\"}"));

		assertThat(check()).isEqualTo(GbrainHealth.up(null));
	}

	@Test
	void reportsDegradedEngineAsUnavailable() {
		server.on("/health", request -> Response.json(503,
				"{\"error\":\"service_unavailable\",\"error_description\":\"Database connection failed\"}"));

		assertThat(check()).isEqualTo(GbrainHealth.down(GbrainErrorCode.UNAVAILABLE));
	}

	@Test
	void reportsUnexpectedBodiesAsProtocolFailures() {
		server.on("/health", request -> Response.json(200, "{\"status\":\"starting\"}"));
		assertThat(check()).isEqualTo(GbrainHealth.down(GbrainErrorCode.PROTOCOL));

		server.on("/health", request -> Response.json(200, "<html>"));
		assertThat(check()).isEqualTo(GbrainHealth.down(GbrainErrorCode.PROTOCOL));
	}

	@Test
	void reportsUnreachableServerAsUnavailable() {
		GbrainProperties properties = server.properties();
		server.close();

		assertThat(new GbrainHealthClient(properties, JSON).check())
				.isEqualTo(GbrainHealth.down(GbrainErrorCode.UNAVAILABLE));
	}

	@Test
	void reportsDisabledWithoutContactingGbrain() {
		GbrainHealthClient client = new GbrainHealthClient(server.properties(false, 3, Duration.ofSeconds(5)),
				JSON);

		assertThat(client.check()).isEqualTo(GbrainHealth.disabled());
		assertThat(server.requests()).isEmpty();
	}

	private GbrainHealth check() {
		return new GbrainHealthClient(server.properties(), JSON).check();
	}

}
