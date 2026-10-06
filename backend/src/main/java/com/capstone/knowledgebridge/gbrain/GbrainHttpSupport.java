package com.capstone.knowledgebridge.gbrain;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * HTTP mechanics shared by the token, MCP, and health clients: bounded timeouts, bounded bodies, and translation of
 * transport failures into {@link GbrainErrorCode}s. Response bodies of failed requests are never read or logged.
 */
final class GbrainHttpSupport {

	private GbrainHttpSupport() {
	}

	/**
	 * Uses the JDK client because, unlike HttpURLConnection, it never silently re-sends a POST after a connection
	 * failure. Redirects are not followed so a bearer token cannot be forwarded to another origin.
	 */
	static RestClient restClient(Duration connectTimeout, Duration readTimeout) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(connectTimeout)
				.version(HttpClient.Version.HTTP_1_1)
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(readTimeout);
		return RestClient.builder().requestFactory(requestFactory).build();
	}

	static byte[] readBounded(InputStream body, long maxBytes) throws IOException {
		byte[] bytes = body.readNBytes(Math.toIntExact(Math.min(maxBytes + 1, Integer.MAX_VALUE)));
		if (bytes.length > maxBytes) {
			throw new GbrainException(GbrainErrorCode.PROTOCOL, "gbrain response exceeded " + maxBytes + " bytes");
		}
		return bytes;
	}

	static void requireSuccess(ClientHttpResponse response, String endpoint) throws IOException {
		int status = response.getStatusCode().value();
		if (!response.getStatusCode().is2xxSuccessful()) {
			throw new GbrainException(classifyStatus(status), endpoint + " returned HTTP " + status, null,
					retryAfter(response.getHeaders()), null);
		}
	}

	static GbrainErrorCode classifyStatus(int status) {
		return switch (status) {
			case 401, 403 -> GbrainErrorCode.UNAUTHORIZED;
			// A missing route means the base URL or deployment is wrong, not that a page is missing.
			case 404 -> GbrainErrorCode.CONFIGURATION;
			case 408 -> GbrainErrorCode.UNAVAILABLE;
			case 413 -> GbrainErrorCode.VALIDATION;
			case 429 -> GbrainErrorCode.RATE_LIMITED;
			default -> status >= 500 ? GbrainErrorCode.UNAVAILABLE : GbrainErrorCode.PROTOCOL;
		};
	}

	/** Supports the delta-seconds form only; HTTP-date values fall back to the client's own backoff. */
	static Duration retryAfter(HttpHeaders headers) {
		String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
		if (value == null || !value.trim().matches("\\d{1,6}")) {
			return null;
		}
		return Duration.ofSeconds(Long.parseLong(value.trim()));
	}

	static GbrainException transportFailure(ResourceAccessException exception, String endpoint) {
		Throwable cause = exception.getCause();
		if (cause instanceof HttpConnectTimeoutException) {
			return new GbrainException(GbrainErrorCode.UNAVAILABLE, endpoint + " connection timed out", exception);
		}
		if (cause instanceof HttpTimeoutException) {
			return new GbrainException(GbrainErrorCode.TIMEOUT, endpoint + " response timed out", exception);
		}
		return new GbrainException(GbrainErrorCode.UNAVAILABLE, endpoint + " is unreachable", exception);
	}

}
