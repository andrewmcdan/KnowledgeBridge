package com.capstone.knowledgebridge.jiraintegration;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.knowledgebridge.jira.JiraClient;
import com.capstone.knowledgebridge.jira.model.JiraConnectionStatus;
import com.capstone.knowledgebridge.jira.model.JiraProject;

/** Jira connection routes for administrators. Sync and work item routes join this controller in later stages. */
@RestController
@RequestMapping("/api/integrations/jira")
public class JiraIntegrationController {

	private final JiraClient jiraClient;

	public JiraIntegrationController(JiraClient jiraClient) {
		this.jiraClient = jiraClient;
	}

	@GetMapping("/status")
	@PreAuthorize("hasRole('ADMIN')")
	public JiraConnectionStatus status() {
		return jiraClient.status();
	}

	@GetMapping("/projects")
	@PreAuthorize("hasRole('ADMIN')")
	public List<JiraProject> projects() {
		return jiraClient.listProjects();
	}

}
