package com.capstone.knowledgebridge.jira.model;

import java.util.List;

/** One page of search results; {@code nextPageToken} is null on the last page. */
public record JiraSearchPage(List<JiraIssue> issues, String nextPageToken, boolean last, int failedCount) {
	public JiraSearchPage(List<JiraIssue> issues, String nextPageToken, boolean last) {
		this(issues, nextPageToken, last, 0);
	}
}
