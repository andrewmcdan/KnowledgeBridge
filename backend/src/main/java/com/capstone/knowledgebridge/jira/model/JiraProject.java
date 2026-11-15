package com.capstone.knowledgebridge.jira.model;

/** A Jira project visible to the configured account. */
public record JiraProject(String id, String key, String name) {
}
