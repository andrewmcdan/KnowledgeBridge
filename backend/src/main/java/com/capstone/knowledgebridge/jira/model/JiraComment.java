package com.capstone.knowledgebridge.jira.model;

import java.time.Instant;

/** One work item comment; {@code body} is Markdown. */
public record JiraComment(String id, String author, Instant created, String body) {
}
