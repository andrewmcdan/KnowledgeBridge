package com.capstone.knowledgebridge.jira;

import java.util.List;
import java.util.Optional;

import com.capstone.knowledgebridge.jira.model.JiraComment;
import com.capstone.knowledgebridge.jira.model.JiraConnectionStatus;
import com.capstone.knowledgebridge.jira.model.JiraIssue;
import com.capstone.knowledgebridge.jira.model.JiraProject;
import com.capstone.knowledgebridge.jira.model.JiraSearchPage;

/** The application's only view of Jira. Failures surface as {@link JiraException}. */
public interface JiraClient {

	List<JiraProject> listProjects();

	/** Returns one page of work items for the JQL query; pass null as the token for the first page. */
	JiraSearchPage searchIssues(String jql, String pageToken);

	Optional<JiraIssue> getIssue(String key);

	/** Returns every comment on the work item. */
	List<JiraComment> listComments(String key);

	/** Probes with a real tool call, because Atlassian accepts initialize and tools/list for under-scoped tokens. */
	default JiraConnectionStatus status() {
		try {
			listProjects();
			return new JiraConnectionStatus(true, true, null);
		} catch (JiraException exception) {
			return new JiraConnectionStatus(exception.code() != JiraErrorCode.NOT_CONFIGURED, false, exception.code());
		}
	}

}
