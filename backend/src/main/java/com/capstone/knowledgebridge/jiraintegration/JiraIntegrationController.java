package com.capstone.knowledgebridge.jiraintegration;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.knowledgebridge.jira.JiraClient;
import com.capstone.knowledgebridge.jira.model.JiraConnectionStatus;
import com.capstone.knowledgebridge.jira.model.JiraProject;

/** Jira connection and sync routes for administrators, and the Jira View routes for every signed-in user. */
@RestController
@RequestMapping("/api/integrations/jira")
public class JiraIntegrationController {

	public record SyncRunResponse(long id, String projectKey, JiraSyncRunStatus status, Instant startedAt,
			Instant finishedAt, int createdCount, int updatedCount, int unchangedCount, int failedCount,
			String errorCode, String errorMessage) {

		static SyncRunResponse from(JiraSyncRun run) {
			return new SyncRunResponse(run.getId(), run.getProjectKey(), run.getStatus(), run.getStartedAt(),
					run.getFinishedAt(), run.getCreatedCount(), run.getUpdatedCount(), run.getUnchangedCount(),
					run.getFailedCount(), run.getErrorCode(), run.getErrorMessage());
		}

	}

	public record WorkItemResponse(long knowledgeItemId, String key, String summary, String projectKey,
			String projectName, String type, String status, String priority, String assignee, String reporter,
			String parentKey, String parentSummary, Instant created, Instant updated, String browseUrl) {

		static WorkItemResponse from(JiraWorkItem item) {
			return new WorkItemResponse(item.getKnowledgeItemId(), item.getIssueKey(), item.getSummary(),
					item.getProjectKey(), item.getProjectName(), item.getIssueType(), item.getStatus(),
					item.getPriority(), item.getAssignee(), item.getReporter(), item.getParentKey(),
					item.getParentSummary(), item.getJiraCreatedAt(), item.getJiraUpdatedAt(), item.getBrowseUrl());
		}

	}

	public record WorkItemDetailResponse(WorkItemResponse workItem, String body) {
	}

	/** Also keeps the key safe to place in a JQL string. */
	private static final String PROJECT_KEY = "[A-Z][A-Z0-9_]{0,63}";

	private final JiraClient jiraClient;

	private final JiraSyncService syncService;

	private final JiraWorkItemService workItemService;

	public JiraIntegrationController(JiraClient jiraClient, JiraSyncService syncService,
			JiraWorkItemService workItemService) {
		this.jiraClient = jiraClient;
		this.syncService = syncService;
		this.workItemService = workItemService;
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

	@PostMapping("/projects/{key}/sync")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<SyncRunResponse> sync(@PathVariable String key, @AuthenticationPrincipal Jwt jwt) {
		if (!key.matches(PROJECT_KEY)) {
			return ResponseEntity.badRequest().build();
		}
		return syncService.start(key, Long.parseLong(jwt.getSubject()))
				.map(run -> ResponseEntity.accepted().body(SyncRunResponse.from(run)))
				.orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT).build());
	}

	@GetMapping("/sync-runs")
	@PreAuthorize("hasRole('ADMIN')")
	public List<SyncRunResponse> syncRuns() {
		return syncService.recentRuns().stream().map(SyncRunResponse::from).toList();
	}

	@GetMapping("/sync-runs/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<SyncRunResponse> syncRun(@PathVariable long id) {
		return ResponseEntity.of(syncService.findRun(id).map(SyncRunResponse::from));
	}

	@GetMapping("/issues")
	public List<WorkItemResponse> issues(@RequestParam(required = false) String project,
			@RequestParam(required = false) String type, @RequestParam(required = false) String status,
			@RequestParam(required = false) String assignee) {
		return workItemService.list(project, type, status, assignee).stream().map(WorkItemResponse::from).toList();
	}

	@GetMapping("/issues/{key}")
	public ResponseEntity<WorkItemDetailResponse> issue(@PathVariable String key) {
		return ResponseEntity.of(workItemService.find(key)
				.map(detail -> new WorkItemDetailResponse(WorkItemResponse.from(detail.workItem()), detail.body())));
	}

}
