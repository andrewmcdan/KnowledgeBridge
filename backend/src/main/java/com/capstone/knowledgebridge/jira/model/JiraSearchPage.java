package com.capstone.knowledgebridge.jira.model;

import java.util.List;

/** One page of search results; {@code nextPageToken} is null on the last page. */
public record JiraSearchPage(List<JiraIssue> issues, String nextPageToken, boolean last) {
}
