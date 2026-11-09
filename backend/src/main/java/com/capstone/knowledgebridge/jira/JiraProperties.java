package com.capstone.knowledgebridge.jira;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Validated Jira connection settings. Missing credentials are allowed at startup so teammates can run the application
 * without a Jira site; calls then fail with {@link JiraErrorCode#NOT_CONFIGURED}.
 */
@ConfigurationProperties("knowledgebridge.jira")
public record JiraProperties(
		boolean enabled,
		Mode mode,
		URI mcpUrl,
		String siteUrl,
		String email,
		String apiToken,
		String cloudId,
		Duration connectTimeout,
		Duration readTimeout,
		DataSize maxResponseSize,
		int pageSize,
		int retryMaxAttempts,
		Duration retryMaxBackoff) {

	public enum Mode {
		/** Read Jira through Atlassian's MCP server. */
		MCP,
		/** Serve a small fixed data set from memory for development and tests. */
		IN_MEMORY
	}

	static final int MAX_PAGE_SIZE = 100;

	static final int MAX_RETRY_ATTEMPTS = 10;

	public JiraProperties {
		mode = mode == null ? Mode.MCP : mode;
		if (mcpUrl == null || mcpUrl.getHost() == null || !mcpUrl.getScheme().matches("https?")) {
			throw invalid("mcp-url must be an absolute http or https URL");
		}
		if (isPresent(siteUrl) && !siteUrl.matches("https?://[^\\s/?#]+/?")) {
			throw invalid("site-url must be the root http or https URL of the Jira site");
		}
		requirePositive("connect-timeout", connectTimeout);
		requirePositive("read-timeout", readTimeout);
		if (maxResponseSize == null || maxResponseSize.toBytes() <= 0) {
			throw invalid("max-response-size must be positive");
		}
		if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
			throw invalid("page-size must be between 1 and " + MAX_PAGE_SIZE);
		}
		if (retryMaxAttempts < 1 || retryMaxAttempts > MAX_RETRY_ATTEMPTS) {
			throw invalid("retry-max-attempts must be between 1 and " + MAX_RETRY_ATTEMPTS);
		}
		requirePositive("retry-max-backoff", retryMaxBackoff);
	}

	public boolean configured() {
		return enabled && isPresent(siteUrl) && isPresent(email) && isPresent(apiToken) && isPresent(cloudId);
	}

	public String browseUrl(String issueKey) {
		return siteUrl.replaceAll("/+$", "") + "/browse/" + issueKey;
	}

	/** Never expose the API token through logs, actuator output, or exception messages. */
	@Override
	public String toString() {
		return "JiraProperties[enabled=" + enabled + ", mode=" + mode + ", mcpUrl=" + mcpUrl + ", siteUrl=" + siteUrl
				+ ", email=" + email + ", apiToken=" + (isPresent(apiToken) ? "<redacted>" : "<unset>") + ", cloudId="
				+ cloudId + ", connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout
				+ ", maxResponseSize=" + maxResponseSize + ", pageSize=" + pageSize + ", retryMaxAttempts="
				+ retryMaxAttempts + ", retryMaxBackoff=" + retryMaxBackoff + "]";
	}

	private static boolean isPresent(String value) {
		return value != null && !value.isBlank();
	}

	private static void requirePositive(String name, Duration value) {
		if (value == null || value.isNegative() || value.isZero()) {
			throw invalid(name + " must be a positive duration");
		}
	}

	private static IllegalArgumentException invalid(String message) {
		return new IllegalArgumentException("knowledgebridge.jira." + message);
	}

}
