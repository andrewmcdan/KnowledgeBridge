package com.capstone.knowledgebridge.jira;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.capstone.knowledgebridge.jira.model.JiraIssue;
import com.capstone.knowledgebridge.jiraintegration.JiraMarkdownConverter;

import tools.jackson.databind.JsonNode;

/** Renders pages from real Atlassian responses, covering the adapter and converter together. */
class JiraDocumentFixtureTests {

	private final JiraResponseMapper mapper = new JiraResponseMapper(
			key -> "https://example.atlassian.net/browse/" + key);

	@Test
	void rendersARealSearchResult() {
		JiraIssue issue = mapper.searchPage(data("search-full-page-1.json")).issues().get(4);

		String page = JiraMarkdownConverter.toMarkdown(issue, issue.comments());

		assertThat(page).startsWith("# ACME-5: Rollback drill sign-off after restore credential issue\n")
				.contains("Status: In Progress", "Priority: Highest", "Assignee: Unassigned",
						"Parent: ACME-1 (Project Cedar: Atlas approval reliability)",
						"Link: https://example.atlassian.net/browse/ACME-5")
				.contains("## Description", "* Update the runbook to use the managed secret alias")
				.contains("## Links", "ACME-6")
				.contains("## Comments", "Runbook updated to the managed secret alias.");
	}

	@Test
	void writesAProjectLineTheEntityHintsCanRead() {
		JiraIssue issue = mapper.issue(data("issue-full-ACME-22.json"));

		assertThat(JiraMarkdownConverter.toMarkdown(issue, issue.comments()))
				.containsPattern("(?m)^Projects: Acme Delivery  $")
				.contains("## Expected");
	}

	private static JsonNode data(String fixture) {
		return MockJiraMcpServer.JSON.readTree(MockJiraMcpServer.fixture(fixture)).path("data");
	}

}
