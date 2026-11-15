package com.capstone.knowledgebridge.jira;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.capstone.knowledgebridge.jira.model.JiraConnectionStatus;

class InMemoryJiraClientTests {

	private final InMemoryJiraClient client = new InMemoryJiraClient();

	@Test
	void servesTheSampleProjectAndWorkItems() {
		assertThat(client.listProjects()).extracting("key").containsExactly("ACME");
		assertThat(client.searchIssues("project = ACME", null).issues()).extracting("key")
				.containsExactly("ACME-5", "ACME-6");
		assertThat(client.searchIssues("project = ACME", null).last()).isTrue();
		assertThat(client.status()).isEqualTo(new JiraConnectionStatus(true, true, null));
	}

	@Test
	void findsWorkItemsAndCommentsByKey() {
		assertThat(client.getIssue("ACME-5")).isPresent();
		assertThat(client.getIssue("ACME-404")).isEmpty();
		assertThat(client.listComments("ACME-5")).hasSize(1);
		assertThat(client.listComments("ACME-404")).isEmpty();
	}

}
