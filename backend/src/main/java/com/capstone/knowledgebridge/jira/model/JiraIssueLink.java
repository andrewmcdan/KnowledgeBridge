package com.capstone.knowledgebridge.jira.model;

/** A link from a work item to another, read as "this item {@code relation} {@code otherKey}". */
public record JiraIssueLink(String relation, String otherKey) {
}
