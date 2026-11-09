package com.capstone.knowledgebridge.jira;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.capstone.knowledgebridge.jira.model.JiraComment;
import com.capstone.knowledgebridge.jira.model.JiraIssue;
import com.capstone.knowledgebridge.jira.model.JiraIssueLink;
import com.capstone.knowledgebridge.jira.model.JiraProject;
import com.capstone.knowledgebridge.jira.model.JiraSearchPage;

/** A small fixed Jira for development and tests. Search ignores the JQL and returns every work item in one page. */
final class InMemoryJiraClient implements JiraClient {

	private static final Instant CREATED = Instant.parse("2026-09-01T12:00:00Z");

	private static final Instant UPDATED = Instant.parse("2026-09-04T12:00:00Z");

	private static final JiraProject PROJECT = new JiraProject("10001", "ACME", "Acme Delivery");

	private static final JiraIssue ROLLBACK_DRILL = issue("10010", "ACME-5",
			"Rollback drill sign-off after restore credential issue", "In Progress",
			"The restore drill took 95 minutes against a 60-minute target.",
			List.of(new JiraComment("10005", "Morgan Reed", UPDATED, "Runbook updated to the managed secret alias.")),
			List.of(new JiraIssueLink("blocks", "ACME-6")));

	private static final JiraIssue READINESS = issue("10011", "ACME-6",
			"Joint Engineering and IT Operations readiness decision", "To Do",
			"Record the joint readiness decision for the Cedar rollout.", List.of(),
			List.of(new JiraIssueLink("is blocked by", "ACME-5")));

	private static final List<JiraIssue> ISSUES = List.of(ROLLBACK_DRILL, READINESS);

	@Override
	public List<JiraProject> listProjects() {
		return List.of(PROJECT);
	}

	@Override
	public JiraSearchPage searchIssues(String jql, String pageToken) {
		return new JiraSearchPage(ISSUES, null, true);
	}

	@Override
	public Optional<JiraIssue> getIssue(String key) {
		return ISSUES.stream().filter(issue -> issue.key().equals(key)).findFirst();
	}

	@Override
	public List<JiraComment> listComments(String key) {
		return getIssue(key).map(JiraIssue::comments).orElse(List.of());
	}

	private static JiraIssue issue(String id, String key, String summary, String status, String description,
			List<JiraComment> comments, List<JiraIssueLink> links) {
		return new JiraIssue(id, key, summary, PROJECT.key(), PROJECT.name(), "Task", status, "High",
				List.of("cedar"), null, "Test User", "ACME-1", "Project Cedar: Atlas approval reliability", CREATED,
				UPDATED, description, comments, comments.size(), links, "https://example.atlassian.net/browse/" + key);
	}

}
