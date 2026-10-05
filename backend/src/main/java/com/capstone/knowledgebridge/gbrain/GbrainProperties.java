package com.capstone.knowledgebridge.gbrain;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Validated gbrain connection settings. Defaults live in application.properties; this record rejects values that would
 * make the transport unsafe or unbounded. Missing OAuth credentials are allowed at startup so the application can run
 * before provisioning; calls then fail with {@link GbrainErrorCode#CONFIGURATION}. {@link Mode#IN_MEMORY} replaces
 * gbrain with a local stand-in for development when the engine is unavailable.
 */
@ConfigurationProperties("knowledgebridge.gbrain")
public record GbrainProperties(
		boolean enabled,
		URI baseUrl,
		String oauthClientId,
		String oauthClientSecret,
		URI oauthTokenUrl,
		Duration connectTimeout,
		Duration readTimeout,
		Duration synthesisTimeout,
		DataSize maxResponseSize,
		int retryMaxAttempts,
		Duration retryMaxBackoff,
		Mode mode) {

	public enum Mode {
		/** Index content in gbrain through MCP. */
		MCP,
		/** Keep pages in process memory; nothing is indexed or searchable. */
		IN_MEMORY
	}

	static final int MAX_RETRY_ATTEMPTS = 5;

	public GbrainProperties {
		mode = mode == null ? Mode.MCP : mode;
		requireHttpUrl("base-url", baseUrl);
		requireHttpUrl("oauth-token-url", oauthTokenUrl);
		requirePositive("connect-timeout", connectTimeout);
		requirePositive("read-timeout", readTimeout);
		requirePositive("synthesis-timeout", synthesisTimeout);
		requirePositive("retry-max-backoff", retryMaxBackoff);
		if (maxResponseSize == null || maxResponseSize.toBytes() <= 0) {
			throw invalid("max-response-size must be positive");
		}
		if (retryMaxAttempts < 1 || retryMaxAttempts > MAX_RETRY_ATTEMPTS) {
			throw invalid("retry-max-attempts must be between 1 and " + MAX_RETRY_ATTEMPTS);
		}
	}

	public URI mcpUrl() {
		return endpoint("/mcp");
	}

	public URI healthUrl() {
		return endpoint("/health");
	}

	public boolean hasCredentials() {
		return isPresent(oauthClientId) && isPresent(oauthClientSecret);
	}

	/** Never expose the client secret through logs, actuator output, or exception messages. */
	@Override
	public String toString() {
		return "GbrainProperties[enabled=" + enabled + ", baseUrl=" + baseUrl + ", oauthClientId=" + oauthClientId
				+ ", oauthClientSecret=" + (isPresent(oauthClientSecret) ? "<redacted>" : "<unset>")
				+ ", oauthTokenUrl=" + oauthTokenUrl + ", connectTimeout=" + connectTimeout + ", readTimeout="
				+ readTimeout + ", synthesisTimeout=" + synthesisTimeout + ", maxResponseSize=" + maxResponseSize
				+ ", retryMaxAttempts=" + retryMaxAttempts + ", retryMaxBackoff=" + retryMaxBackoff + ", mode=" + mode
				+ "]";
	}

	private URI endpoint(String path) {
		String base = baseUrl.toString();
		return URI.create((base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path);
	}

	private static boolean isPresent(String value) {
		return value != null && !value.isBlank();
	}

	private static void requireHttpUrl(String name, URI url) {
		if (url == null || url.getHost() == null
				|| !("http".equals(url.getScheme()) || "https".equals(url.getScheme()))) {
			throw invalid(name + " must be an absolute http or https URL");
		}
		if (url.getUserInfo() != null || url.getQuery() != null || url.getFragment() != null) {
			throw invalid(name + " must not contain credentials, a query, or a fragment");
		}
	}

	private static void requirePositive(String name, Duration value) {
		if (value == null || value.isNegative() || value.isZero()) {
			throw invalid(name + " must be a positive duration");
		}
	}

	private static IllegalArgumentException invalid(String message) {
		return new IllegalArgumentException("knowledgebridge.gbrain." + message);
	}

}
