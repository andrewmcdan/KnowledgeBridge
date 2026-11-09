package com.capstone.knowledgebridge.jiraintegration;

import java.time.Instant;

import com.capstone.knowledgebridge.jira.model.JiraIssue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The structured Jira fields of one synced work item, so the Jira View can filter without parsing page text. */
@Entity
@Table(name = "jira_work_item")
public class JiraWorkItem {

	@Id
	@Column(name = "knowledge_item_id")
	private Long knowledgeItemId;

	@Column(name = "issue_key", nullable = false)
	private String issueKey;

	@Column(nullable = false)
	private String summary;

	@Column(name = "project_key")
	private String projectKey;

	@Column(name = "project_name")
	private String projectName;

	@Column(name = "issue_type")
	private String issueType;

	private String status;

	private String priority;

	private String assignee;

	private String reporter;

	@Column(name = "parent_key")
	private String parentKey;

	@Column(name = "parent_summary")
	private String parentSummary;

	@Column(name = "jira_created_at", nullable = false)
	private Instant jiraCreatedAt;

	@Column(name = "jira_updated_at", nullable = false)
	private Instant jiraUpdatedAt;

	@Column(name = "browse_url", nullable = false)
	private String browseUrl;

	protected JiraWorkItem() {
		// JPA
	}

	static JiraWorkItem from(Long knowledgeItemId, JiraIssue issue) {
		JiraWorkItem item = new JiraWorkItem();
		item.knowledgeItemId = knowledgeItemId;
		item.issueKey = issue.key();
		item.summary = issue.summary();
		item.projectKey = issue.projectKey();
		item.projectName = issue.projectName();
		item.issueType = issue.type();
		item.status = issue.status();
		item.priority = issue.priority();
		item.assignee = issue.assignee();
		item.reporter = issue.reporter();
		item.parentKey = issue.parentKey();
		item.parentSummary = issue.parentSummary();
		item.jiraCreatedAt = issue.created();
		item.jiraUpdatedAt = issue.updated();
		item.browseUrl = issue.browseUrl();
		return item;
	}

	public Long getKnowledgeItemId() {
		return knowledgeItemId;
	}

	public String getIssueKey() {
		return issueKey;
	}

	public String getSummary() {
		return summary;
	}

	public String getProjectKey() {
		return projectKey;
	}

	public String getProjectName() {
		return projectName;
	}

	public String getIssueType() {
		return issueType;
	}

	public String getStatus() {
		return status;
	}

	public String getPriority() {
		return priority;
	}

	public String getAssignee() {
		return assignee;
	}

	public String getReporter() {
		return reporter;
	}

	public String getParentKey() {
		return parentKey;
	}

	public String getParentSummary() {
		return parentSummary;
	}

	public Instant getJiraCreatedAt() {
		return jiraCreatedAt;
	}

	public Instant getJiraUpdatedAt() {
		return jiraUpdatedAt;
	}

	public String getBrowseUrl() {
		return browseUrl;
	}

}
