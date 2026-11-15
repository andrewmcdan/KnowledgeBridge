package com.capstone.knowledgebridge.jira.model;

import java.time.Instant;
import java.util.List;

/**
 * A Jira work item in application terms. {@code description} and comment bodies are Markdown. {@code comments} may hold
 * fewer entries than {@code commentTotal}; use {@code JiraClient.listComments} when they differ.
 */
public record JiraIssue(
		String id,
		String key,
		String summary,
		String projectKey,
		String projectName,
		String type,
		String status,
		String priority,
		List<String> labels,
		String assignee,
		String reporter,
		String parentKey,
		String parentSummary,
		Instant created,
		Instant updated,
		String description,
		List<JiraComment> comments,
		int commentTotal,
		List<JiraIssueLink> links,
		String browseUrl) {
}
