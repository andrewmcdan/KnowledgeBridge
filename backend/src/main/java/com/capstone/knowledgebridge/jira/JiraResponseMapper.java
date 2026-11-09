package com.capstone.knowledgebridge.jira;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.capstone.knowledgebridge.jira.model.JiraComment;
import com.capstone.knowledgebridge.jira.model.JiraIssue;
import com.capstone.knowledgebridge.jira.model.JiraIssueLink;
import com.capstone.knowledgebridge.jira.model.JiraProject;
import com.capstone.knowledgebridge.jira.model.JiraSearchPage;

import tools.jackson.databind.JsonNode;

/** Turns Atlassian's JSON into application records, so the rest of the adapter never sees Jira field names. */
final class JiraResponseMapper {

	private static final DateTimeFormatter JIRA_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

	private final Function<String, String> browseUrl;

	JiraResponseMapper(Function<String, String> browseUrl) {
		this.browseUrl = browseUrl;
	}

	JiraProject project(JsonNode node) {
		return new JiraProject(required(node, "id"), required(node, "key"), required(node, "name"));
	}

	JiraSearchPage searchPage(JsonNode data) {
		List<JiraIssue> issues = new ArrayList<>();
		for (JsonNode issue : data.path("issues")) {
			issues.add(issue(issue));
		}
		return new JiraSearchPage(issues, data.path("nextPageToken").asString(null),
				data.path("isLast").asBoolean(true));
	}

	JiraIssue issue(JsonNode node) {
		String key = required(node, "key");
		JsonNode fields = node.path("fields");
		JsonNode parent = fields.path("parent");
		JsonNode comment = fields.path("comment");
		List<JiraComment> comments = new ArrayList<>();
		for (JsonNode entry : comment.path("comments")) {
			comments.add(comment(entry));
		}
		List<String> labels = new ArrayList<>();
		for (JsonNode label : fields.path("labels")) {
			labels.add(label.asString());
		}
		List<JiraIssueLink> links = new ArrayList<>();
		for (JsonNode link : fields.path("issuelinks")) {
			links.add(link(link));
		}
		return new JiraIssue(required(node, "id"), key, required(fields, "summary"),
				fields.path("project").path("key").asString(null), fields.path("project").path("name").asString(null),
				fields.path("issuetype").path("name").asString(null), fields.path("status").path("name").asString(null),
				fields.path("priority").path("name").asString(null), labels,
				fields.path("assignee").path("displayName").asString(null),
				fields.path("reporter").path("displayName").asString(null), parent.path("key").asString(null),
				parent.path("fields").path("summary").asString(null), time(fields, "created"), time(fields, "updated"),
				markdown(fields.path("description")), comments, comment.path("total").asInt(comments.size()), links,
				browseUrl.apply(key));
	}

	JiraComment comment(JsonNode node) {
		return new JiraComment(required(node, "id"), node.path("author").path("displayName").asString(null),
				time(node, "created"), markdown(node.path("body")));
	}

	/** Jira labels the link from the side of whichever issue is absent from the entry. */
	private static JiraIssueLink link(JsonNode node) {
		boolean inward = node.has("inwardIssue");
		return new JiraIssueLink(required(node.path("type"), inward ? "inward" : "outward"),
				required(node.path(inward ? "inwardIssue" : "outwardIssue"), "key"));
	}

	/** Rejects ADF so a request that lost its Markdown format can't silently ingest an empty body. */
	private static String markdown(JsonNode node) {
		if (node.isContainer()) {
			throw protocol("Jira returned rich content instead of Markdown");
		}
		return node.asString("");
	}

	private static Instant time(JsonNode node, String field) {
		try {
			return OffsetDateTime.parse(required(node, field), JIRA_TIME).toInstant();
		} catch (DateTimeParseException exception) {
			throw new JiraException(JiraErrorCode.PROTOCOL, "Jira returned an invalid " + field + " time", exception);
		}
	}

	private static String required(JsonNode node, String field) {
		String value = node.path(field).asString("");
		if (value.isBlank()) {
			throw protocol("Jira response is missing " + field);
		}
		return value;
	}

	private static JiraException protocol(String message) {
		return new JiraException(JiraErrorCode.PROTOCOL, message);
	}

}
