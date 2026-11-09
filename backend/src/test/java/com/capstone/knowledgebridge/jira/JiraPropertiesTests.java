package com.capstone.knowledgebridge.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

class JiraPropertiesTests {

	private static final URI MCP = URI.create("https://mcp.atlassian.com/v2/mcp");

	private static final String SITE = "https://example.atlassian.net";

	private static final Duration TIMEOUT = Duration.ofSeconds(2);

	private static final DataSize SIZE = DataSize.ofMegabytes(1);

	@Test
	void configuredRequiresEnabledAndEveryConnectionValue() {
		assertThat(properties(true, SITE, "e", "t", "c").configured()).isTrue();
		assertThat(properties(false, SITE, "e", "t", "c").configured()).isFalse();
		assertThat(properties(true, " ", "e", "t", "c").configured()).isFalse();
		assertThat(properties(true, null, "e", "t", "c").configured()).isFalse();
		assertThat(properties(true, SITE, "", "t", "c").configured()).isFalse();
		assertThat(properties(true, SITE, "e", "", "c").configured()).isFalse();
		assertThat(properties(true, SITE, "e", "t", "").configured()).isFalse();
	}

	@Test
	void defaultsToMcpModeAndKeepsAnExplicitMode() {
		assertThat(properties(true, SITE, "e", "t", "c").mode()).isEqualTo(JiraProperties.Mode.MCP);
		assertThat(new JiraProperties(true, JiraProperties.Mode.IN_MEMORY, MCP, SITE, "e", "t", "c", TIMEOUT, TIMEOUT,
				SIZE, 10).mode()).isEqualTo(JiraProperties.Mode.IN_MEMORY);
	}

	@Test
	void buildsBrowseUrlsWithOrWithoutATrailingSlash() {
		assertThat(properties(true, SITE, "e", "t", "c").browseUrl("KB-1")).isEqualTo(SITE + "/browse/KB-1");
		assertThat(properties(true, SITE + "/", "e", "t", "c").browseUrl("KB-1")).isEqualTo(SITE + "/browse/KB-1");
	}

	@Test
	void redactsTheApiToken() {
		assertThat(properties(true, SITE, "e", "secret-token", "c").toString()).contains("<redacted>")
				.doesNotContain("secret-token");
		assertThat(properties(true, SITE, "e", null, "c").toString()).contains("apiToken=<unset>");
	}

	@ParameterizedTest
	@ValueSource(strings = {"localhost:3131", "/relative", "ftp://localhost"})
	void rejectsInvalidMcpUrls(String url) {
		assertInvalid(URI.create(url), SITE, TIMEOUT, TIMEOUT, SIZE, 10, "mcp-url");
	}

	@Test
	void rejectsAMissingMcpUrl() {
		assertInvalid(null, SITE, TIMEOUT, TIMEOUT, SIZE, 10, "mcp-url");
	}

	@ParameterizedTest
	@ValueSource(strings = {"example.atlassian.net", "https://example.atlassian.net/jira", "ftp://example.net"})
	void rejectsInvalidSiteUrls(String site) {
		assertInvalid(MCP, site, TIMEOUT, TIMEOUT, SIZE, 10, "site-url");
	}

	@Test
	void rejectsNonPositiveTimeouts() {
		for (Duration invalid : new Duration[]{null, Duration.ZERO, Duration.ofSeconds(-1)}) {
			assertInvalid(MCP, SITE, invalid, TIMEOUT, SIZE, 10, "connect-timeout");
			assertInvalid(MCP, SITE, TIMEOUT, invalid, SIZE, 10, "read-timeout");
		}
	}

	@Test
	void rejectsInvalidSizeLimits() {
		assertInvalid(MCP, SITE, TIMEOUT, TIMEOUT, null, 10, "max-response-size");
		assertInvalid(MCP, SITE, TIMEOUT, TIMEOUT, DataSize.ofBytes(0), 10, "max-response-size");
		assertInvalid(MCP, SITE, TIMEOUT, TIMEOUT, SIZE, 0, "page-size");
		assertInvalid(MCP, SITE, TIMEOUT, TIMEOUT, SIZE, 101, "page-size");
	}

	private static JiraProperties properties(boolean enabled, String site, String email, String token, String cloud) {
		return new JiraProperties(enabled, null, MCP, site, email, token, cloud, TIMEOUT, TIMEOUT, SIZE, 10);
	}

	private static void assertInvalid(URI mcp, String site, Duration connect, Duration read, DataSize size,
			int pageSize,
			String message) {
		assertThatThrownBy(
				() -> new JiraProperties(true, null, mcp, site, "e", "t", "c", connect, read, size, pageSize))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining(message);
	}

}
