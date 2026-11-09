package com.capstone.knowledgebridge.jira.model;

import com.capstone.knowledgebridge.jira.JiraErrorCode;

/** Whether Jira is configured and answering; {@code failure} is null when it is reachable. */
public record JiraConnectionStatus(boolean configured, boolean reachable, JiraErrorCode failure) {
}
