package com.capstone.knowledgebridge.jira;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;

import com.capstone.knowledgebridge.jira.model.JiraComment;
import com.capstone.knowledgebridge.jira.model.JiraIssue;
import com.capstone.knowledgebridge.jira.model.JiraProject;
import com.capstone.knowledgebridge.jira.model.JiraSearchPage;

import tools.jackson.databind.JsonNode;

/** Reads Jira through the tools of Atlassian's MCP server. */
final class McpJiraClient implements JiraClient {

	private static final String MARKDOWN = "markdown";

	private static final int COMMENT_PAGE_SIZE = 100;

	/** Requires the full view: the default search silently omits description, parent, comments, and links. */
	private static final String FULL_VIEW = "full";

	private static final List<String> ISSUE_FIELDS = List.of("summary", "project", "issuetype", "status", "priority",
			"labels", "assignee", "reporter", "parent", "created", "updated", "description", "comment", "issuelinks");

	private final JiraMcpTransport transport;

	private final JiraProperties properties;

	private final JiraResponseMapper mapper;

	McpJiraClient(JiraMcpTransport transport, JiraProperties properties) {
		this.transport = transport;
		this.properties = properties;
		this.mapper = new JiraResponseMapper(properties::browseUrl);
	}

	@Override
	public List<JiraProject> listProjects() {
		return offsetPages(startAt -> transport.callTool("executeRead",
				Map.of("cloudId", properties.cloudId(), "name", "listJiraProjects", "inputs",
						Map.of("startAt", startAt, "maxResults", properties.pageSize()))),
				"values", mapper::project);
	}

	@Override
	public JiraSearchPage searchIssues(String jql, String pageToken) {
		Map<String, Object> arguments = new LinkedHashMap<>();
		arguments.put("cloudId", properties.cloudId());
		arguments.put("jql", jql);
		arguments.put("maxResults", properties.pageSize());
		arguments.put("responseContentFormat", MARKDOWN);
		arguments.put("view", FULL_VIEW);
		arguments.put("fields", ISSUE_FIELDS);
		if (pageToken != null) {
			arguments.put("nextPageToken", pageToken);
		}
		return mapper.searchPage(transport.callTool("searchJiraIssuesUsingJql", arguments));
	}

	@Override
	public Optional<JiraIssue> getIssue(String key) {
		try {
			return Optional.of(mapper.issue(transport.callTool("getJiraIssue", Map.of("cloudId", properties.cloudId(),
					"issueIdOrKey", key, "responseContentFormat", MARKDOWN, "view", FULL_VIEW))));
		} catch (JiraException exception) {
			if (exception.code() == JiraErrorCode.NOT_FOUND) {
				return Optional.empty();
			}
			throw exception;
		}
	}

	@Override
	public List<JiraComment> listComments(String key) {
		return offsetPages(startAt -> transport.callTool("listJiraIssueComments",
				Map.of("cloudId", properties.cloudId(), "issueIdOrKey", key, "startAt", startAt, "maxResults",
						COMMENT_PAGE_SIZE, "orderBy", "created", "responseContentFormat", MARKDOWN)),
				"comments", mapper::comment);
	}

	private static <T> List<T> offsetPages(IntFunction<JsonNode> fetch, String field, Function<JsonNode, T> map) {
		List<T> items = new ArrayList<>();
		while (true) {
			JsonNode page = fetch.apply(items.size());
			int before = items.size();
			for (JsonNode item : page.path(field)) {
				items.add(map.apply(item));
			}
			if (page.path("isLast").asBoolean(true) || items.size() == before) {
				return items;
			}
		}
	}

}
