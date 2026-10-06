package com.capstone.knowledgebridge.gbrain;

import static com.capstone.knowledgebridge.gbrain.GbrainMcpClientTests.assertCode;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.ResourceAccessException;

class GbrainHttpSupportTests {

	@ParameterizedTest
	@CsvSource({
			"400, PROTOCOL",
			"401, UNAUTHORIZED",
			"403, UNAUTHORIZED",
			"404, CONFIGURATION",
			"405, PROTOCOL",
			"408, UNAVAILABLE",
			"413, VALIDATION",
			"429, RATE_LIMITED",
			"500, UNAVAILABLE",
			"502, UNAVAILABLE",
			"503, UNAVAILABLE",
			"504, UNAVAILABLE"})
	void classifiesHttpStatuses(int status, GbrainErrorCode expected) {
		assertThat(GbrainHttpSupport.classifyStatus(status)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource(nullValues = "NULL", value = {
			"NULL, NULL",
			"'Wed, 21 Oct 2026 07:28:00 GMT', NULL",
			"-1, NULL",
			"1234567, NULL",
			"0, 0",
			"' 3 ', 3"})
	void parsesDeltaSecondsRetryAfter(String header, Long expectedSeconds) {
		HttpHeaders headers = new HttpHeaders();
		if (header != null) {
			headers.add(HttpHeaders.RETRY_AFTER, header);
		}

		assertThat(GbrainHttpSupport.retryAfter(headers))
				.isEqualTo(expectedSeconds == null ? null : Duration.ofSeconds(expectedSeconds));
	}

	@Test
	void classifiesTransportFailures() {
		assertThat(failure(new HttpConnectTimeoutException("connect")).code())
				.isEqualTo(GbrainErrorCode.UNAVAILABLE);
		assertThat(failure(new HttpTimeoutException("read")).code()).isEqualTo(GbrainErrorCode.TIMEOUT);
		assertThat(failure(new ConnectException("refused")).code()).isEqualTo(GbrainErrorCode.UNAVAILABLE);
	}

	@Test
	void readsBodiesUpToTheLimit() throws IOException {
		assertThat(GbrainHttpSupport.readBounded(new ByteArrayInputStream(new byte[]{1, 2, 3}), 3)).hasSize(3);
		assertCode(() -> GbrainHttpSupport.readBounded(new ByteArrayInputStream(new byte[]{1, 2, 3, 4}), 3),
				GbrainErrorCode.PROTOCOL);
	}

	private static GbrainException failure(IOException cause) {
		GbrainException exception = GbrainHttpSupport
				.transportFailure(new ResourceAccessException("I/O error", cause), "gbrain");
		assertThat(exception.getCause()).isInstanceOf(ResourceAccessException.class);
		return exception;
	}

}
