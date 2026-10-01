package com.capstone.knowledgebridge.gbrain;

import static com.capstone.knowledgebridge.gbrain.GbrainMcpClientTests.assertCode;
import static com.capstone.knowledgebridge.gbrain.MockGbrainServer.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.capstone.knowledgebridge.gbrain.MockGbrainServer.Response;

class GbrainTokenProviderTests {

	private final MockGbrainServer server = new MockGbrainServer();

	private final MutableClock clock = new MutableClock();

	@AfterEach
	void stopServer() {
		server.close();
	}

	@Test
	void requestsAScopedClientCredentialsTokenAndCachesIt() {
		GbrainTokenProvider provider = provider(server.properties());

		assertThat(provider.accessToken()).isEqualTo("token-1");
		assertThat(provider.accessToken()).isEqualTo("token-1");

		assertThat(server.requests("/token")).hasSize(1);
		MockGbrainServer.Request request = server.requests("/token").get(0);
		assertThat(request.method()).isEqualTo("POST");
		assertThat(request.header("Content-Type")).startsWith("application/x-www-form-urlencoded");
		assertThat(request.body()).contains("grant_type=client_credentials", "client_id=client-id",
				"client_secret=client-secret-value", "scope=read+write");
	}

	@Test
	void refreshesShortlyBeforeExpiry() {
		GbrainTokenProvider provider = provider(server.properties());
		provider.accessToken();

		clock.advance(Duration.ofSeconds(3600).minus(GbrainTokenProvider.EXPIRY_SKEW).minusSeconds(1));
		assertThat(provider.accessToken()).isEqualTo("token-1");

		clock.advance(Duration.ofSeconds(1));
		assertThat(provider.accessToken()).isEqualTo("token-2");
	}

	@Test
	void invalidateDropsOnlyTheRejectedToken() {
		GbrainTokenProvider provider = provider(server.properties());
		provider.accessToken();

		provider.invalidate("some-older-token");
		assertThat(provider.accessToken()).isEqualTo("token-1");

		provider.invalidate("token-1");
		assertThat(provider.accessToken()).isEqualTo("token-2");
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "\"expires_in\":0,", "\"expires_in\":\"3600\","})
	void missingOrInvalidLifetimeFallsBackToTheDefault(String expiresIn) {
		server.on("/token", request -> Response.json(200,
				"{" + expiresIn + "\"access_token\":\"short\",\"token_type\":\"bearer\"}"));
		GbrainTokenProvider provider = provider(server.properties());
		provider.accessToken();

		clock.advance(GbrainTokenProvider.DEFAULT_LIFETIME.minus(GbrainTokenProvider.EXPIRY_SKEW).minusSeconds(1));
		provider.accessToken();
		assertThat(server.requests("/token")).hasSize(1);

		clock.advance(Duration.ofSeconds(1));
		provider.accessToken();
		assertThat(server.requests("/token")).hasSize(2);
	}

	@Test
	void missingCredentialsAreAConfigurationFailure() {
		GbrainProperties configured = server.properties();
		GbrainProperties unprovisioned = new GbrainProperties(true, configured.baseUrl(), "", "",
				configured.oauthTokenUrl(), configured.connectTimeout(), configured.readTimeout(),
				configured.synthesisTimeout(), configured.maxResponseSize(), 3, configured.retryMaxBackoff());

		assertCode(() -> provider(unprovisioned).accessToken(), GbrainErrorCode.CONFIGURATION);
		assertThat(server.requests()).isEmpty();
	}

	@Test
	void rejectedCredentialsAreUnauthorizedWithoutLeakingTheSecret() {
		server.on("/token", request -> Response.json(400, "{\"error\":\"invalid_client\"}"));
		assertThatThrownBy(() -> provider(server.properties()).accessToken())
				.isInstanceOfSatisfying(GbrainException.class, exception -> {
					assertThat(exception.code()).isEqualTo(GbrainErrorCode.UNAUTHORIZED);
					assertThat(exception.getMessage()).doesNotContain("client-secret-value");
				});

		server.on("/token", request -> Response.json(401, "{\"error\":\"invalid_client\"}"));
		assertCode(() -> provider(server.properties()).accessToken(), GbrainErrorCode.UNAUTHORIZED);
	}

	@Test
	void unavailableTokenEndpointIsClassified() {
		server.on("/token", request -> Response.empty(503));
		assertCode(() -> provider(server.properties()).accessToken(), GbrainErrorCode.UNAVAILABLE);

		GbrainProperties properties = server.properties();
		server.close();
		assertCode(() -> provider(properties).accessToken(), GbrainErrorCode.UNAVAILABLE);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"not json",
			"{\"token_type\":\"Bearer\"}",
			"{\"access_token\":\" \",\"token_type\":\"Bearer\"}",
			"{\"access_token\":42,\"token_type\":\"Bearer\"}",
			"{\"access_token\":\"abc\"}",
			"{\"access_token\":\"abc\",\"token_type\":\"mac\"}"})
	void malformedTokenResponsesAreProtocolFailures(String body) {
		server.on("/token", request -> Response.json(200, body));

		assertCode(() -> provider(server.properties()).accessToken(), GbrainErrorCode.PROTOCOL);
	}

	private GbrainTokenProvider provider(GbrainProperties properties) {
		return new GbrainTokenProvider(properties, JSON, clock);
	}

	private static final class MutableClock extends Clock {

		private Instant now = Instant.parse("2026-10-01T00:00:00Z");

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

}
