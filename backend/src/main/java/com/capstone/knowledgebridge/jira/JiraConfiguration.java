package com.capstone.knowledgebridge.jira;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

/** Wires the Jira client. Nothing contacts Jira during startup, so the application starts while Jira is unreachable. */
@Configuration
@EnableConfigurationProperties(JiraProperties.class)
public class JiraConfiguration {

	private static final Logger log = LoggerFactory.getLogger(JiraConfiguration.class);

	@Bean
	JiraClient jiraClient(JiraProperties properties, JsonMapper jsonMapper) {
		if (properties.enabled() && properties.mode() == JiraProperties.Mode.IN_MEMORY) {
			log.warn("Jira mode is IN_MEMORY: serving fixed sample work items");
			return new InMemoryJiraClient();
		}
		return new McpJiraClient(new JiraMcpTransport(properties, jsonMapper), properties);
	}

}
