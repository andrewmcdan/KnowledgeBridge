package com.capstone.knowledgebridge.jira;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import tools.jackson.databind.json.JsonMapper;

class JiraConfigurationTests {

	@Test
	void usesTheInMemoryClientOnlyWhenEnabledInThatMode() {
		assertThat(client(true, JiraProperties.Mode.IN_MEMORY)).isInstanceOf(InMemoryJiraClient.class);
		assertThat(client(false, JiraProperties.Mode.IN_MEMORY)).isInstanceOf(McpJiraClient.class);
		assertThat(client(true, JiraProperties.Mode.MCP)).isInstanceOf(McpJiraClient.class);
	}

	private static JiraClient client(boolean enabled, JiraProperties.Mode mode) {
		JiraProperties properties = new JiraProperties(enabled, mode, URI.create("https://mcp.atlassian.com/v2/mcp"),
				"",
				"", "", "", Duration.ofSeconds(1), Duration.ofSeconds(1), DataSize.ofMegabytes(1), 10);
		return new JiraConfiguration().jiraClient(properties, JsonMapper.builder().build());
	}

}
