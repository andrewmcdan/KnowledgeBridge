package com.capstone.knowledgebridge.gbrain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

class GbrainPropertiesTests {

	private static final URI BASE = URI.create("http://localhost:3131");

	private static final URI TOKEN = URI.create("http://localhost:3131/token");

	private static final Duration TIMEOUT = Duration.ofSeconds(2);

	@Test
	void buildsEndpointUrlsFromTheBaseUrl() {
		assertThat(properties(BASE).mcpUrl()).isEqualTo(URI.create("http://localhost:3131/mcp"));
		assertThat(properties(URI.create("https://brain.example/")).healthUrl())
				.isEqualTo(URI.create("https://brain.example/health"));
		assertThat(properties(URI.create("https://brain.example/gbrain")).mcpUrl())
				.isEqualTo(URI.create("https://brain.example/gbrain/mcp"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"localhost:3131", "/relative", "ftp://localhost", "http://user:pass@localhost",
			"http://localhost?x=1", "http://localhost#fragment"})
	void rejectsUnsafeOrInvalidUrls(String url) {
		assertInvalid(() -> properties(URI.create(url)), "base-url");
		assertInvalid(() -> new GbrainProperties(true, BASE, null, null, URI.create(url), TIMEOUT, TIMEOUT, TIMEOUT,
				DataSize.ofMegabytes(1), 3, TIMEOUT), "oauth-token-url");
	}

	@Test
	void rejectsMissingBaseUrl() {
		assertInvalid(() -> properties(null), "base-url");
	}

	@Test
	void rejectsMissingZeroOrNegativeDurations() {
		for (Duration invalid : new Duration[]{null, Duration.ZERO, Duration.ofSeconds(-1)}) {
			assertInvalid(() -> withDurations(invalid, TIMEOUT, TIMEOUT, TIMEOUT), "connect-timeout");
			assertInvalid(() -> withDurations(TIMEOUT, invalid, TIMEOUT, TIMEOUT), "read-timeout");
			assertInvalid(() -> withDurations(TIMEOUT, TIMEOUT, invalid, TIMEOUT), "synthesis-timeout");
			assertInvalid(() -> withDurations(TIMEOUT, TIMEOUT, TIMEOUT, invalid), "retry-max-backoff");
		}
	}

	@Test
	void rejectsUnboundedResponsesAndRetryCounts() {
		assertInvalid(() -> new GbrainProperties(true, BASE, null, null, TOKEN, TIMEOUT, TIMEOUT, TIMEOUT, null, 3,
				TIMEOUT), "max-response-size");
		assertInvalid(() -> new GbrainProperties(true, BASE, null, null, TOKEN, TIMEOUT, TIMEOUT, TIMEOUT,
				DataSize.ofBytes(0), 3, TIMEOUT), "max-response-size");
		assertInvalid(() -> withAttempts(0), "retry-max-attempts");
		assertInvalid(() -> withAttempts(GbrainProperties.MAX_RETRY_ATTEMPTS + 1), "retry-max-attempts");
		assertThat(withAttempts(GbrainProperties.MAX_RETRY_ATTEMPTS).retryMaxAttempts()).isEqualTo(5);
	}

	@Test
	void credentialsRequireBothNonBlankValues() {
		assertThat(withCredentials("id", "secret").hasCredentials()).isTrue();
		assertThat(withCredentials(null, "secret").hasCredentials()).isFalse();
		assertThat(withCredentials(" ", "secret").hasCredentials()).isFalse();
		assertThat(withCredentials("id", null).hasCredentials()).isFalse();
		assertThat(withCredentials("id", "").hasCredentials()).isFalse();
	}

	@Test
	void toStringNeverExposesTheClientSecret() {
		assertThat(withCredentials("client-id", "super-secret").toString()).contains("client-id", "<redacted>")
				.doesNotContain("super-secret");
		assertThat(withCredentials("client-id", "").toString()).contains("oauthClientSecret=<unset>");
	}

	private static GbrainProperties properties(URI baseUrl) {
		return new GbrainProperties(true, baseUrl, null, null, TOKEN, TIMEOUT, TIMEOUT, TIMEOUT,
				DataSize.ofMegabytes(1), 3, TIMEOUT);
	}

	private static GbrainProperties withDurations(Duration connect, Duration read, Duration synthesis,
			Duration backoff) {
		return new GbrainProperties(true, BASE, null, null, TOKEN, connect, read, synthesis, DataSize.ofMegabytes(1),
				3, backoff);
	}

	private static GbrainProperties withAttempts(int attempts) {
		return new GbrainProperties(true, BASE, null, null, TOKEN, TIMEOUT, TIMEOUT, TIMEOUT,
				DataSize.ofMegabytes(1), attempts, TIMEOUT);
	}

	private static GbrainProperties withCredentials(String clientId, String clientSecret) {
		return new GbrainProperties(true, BASE, clientId, clientSecret, TOKEN, TIMEOUT, TIMEOUT, TIMEOUT,
				DataSize.ofMegabytes(1), 3, TIMEOUT);
	}

	private static void assertInvalid(ThrowingCallable call, String property) {
		assertThatThrownBy(call).isInstanceOf(IllegalArgumentException.class)
				.hasMessageStartingWith("knowledgebridge.gbrain." + property);
	}

}
