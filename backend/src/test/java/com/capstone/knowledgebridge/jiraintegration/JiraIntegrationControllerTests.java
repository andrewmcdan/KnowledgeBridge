package com.capstone.knowledgebridge.jiraintegration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.capstone.knowledgebridge.jira.JiraClient;
import com.capstone.knowledgebridge.jira.JiraErrorCode;
import com.capstone.knowledgebridge.jira.JiraException;
import com.capstone.knowledgebridge.jira.model.JiraConnectionStatus;
import com.capstone.knowledgebridge.jira.model.JiraIssue;
import com.capstone.knowledgebridge.jira.model.JiraProject;
import com.capstone.knowledgebridge.jira.model.JiraSearchPage;

import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@SpringBootTest(properties = {"knowledgebridge.gbrain.mode=in-memory", "knowledgebridge.gbrain.enabled=false"})
@AutoConfigureMockMvc
class JiraIntegrationControllerTests {

	private static final String STATUS = "/api/integrations/jira/status";

	private static final String PROJECTS = "/api/integrations/jira/projects";

	private static final String SYNC_RUNS = "/api/integrations/jira/sync-runs";

	private static final String ISSUES = "/api/integrations/jira/issues";

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer(
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JsonMapper json;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private JiraClient jira;

	@Test
	void routesAreAdminOnly() throws Exception {
		mvc.perform(get(STATUS)).andExpect(status().isUnauthorized());
		mvc.perform(get(PROJECTS)).andExpect(status().isUnauthorized());
		String user = token("user@acme.example", "user123");
		mvc.perform(get(STATUS).header("Authorization", user)).andExpect(status().isForbidden());
		mvc.perform(get(PROJECTS).header("Authorization", user)).andExpect(status().isForbidden());
	}

	@Test
	void adminsSeeStatusAndProjects() throws Exception {
		when(jira.status()).thenReturn(new JiraConnectionStatus(true, false, JiraErrorCode.UNAUTHORIZED));
		when(jira.listProjects()).thenReturn(List.of(new JiraProject("1", "ACME", "Acme Delivery")));
		String admin = admin();

		mvc.perform(get(STATUS).header("Authorization", admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.configured").value(true))
				.andExpect(jsonPath("$.reachable").value(false))
				.andExpect(jsonPath("$.failure").value("UNAUTHORIZED"));
		mvc.perform(get(PROJECTS).header("Authorization", admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].key").value("ACME"));
	}

	@Test
	void syncRoutesAreAdminOnly() throws Exception {
		mvc.perform(post(PROJECTS + "/ACME/sync")).andExpect(status().isUnauthorized());
		mvc.perform(get(SYNC_RUNS)).andExpect(status().isUnauthorized());
		String user = token("user@acme.example", "user123");
		mvc.perform(post(PROJECTS + "/ACME/sync").header("Authorization", user)).andExpect(status().isForbidden());
		mvc.perform(get(SYNC_RUNS).header("Authorization", user)).andExpect(status().isForbidden());
		mvc.perform(get(SYNC_RUNS + "/1").header("Authorization", user)).andExpect(status().isForbidden());
	}

	@Test
	void syncRejectsKeysThatAreNotJiraProjectKeys() throws Exception {
		String admin = admin();
		mvc.perform(post(PROJECTS + "/acme\"OR/sync").header("Authorization", admin))
				.andExpect(status().isBadRequest());
	}

	@Test
	void aSyncRunsInTheBackgroundThenASecondSyncReportsNoChanges() throws Exception {
		Instant updated = Instant.parse("2026-11-05T22:37:59.818Z");
		JiraIssue issue = new JiraIssue("1", "SYNC-1", "Rollback drill", "SYNC", "Sync Project", "Task", "To Do",
				"High",
				List.of(), null, "R", null, null, updated, updated, "Description", List.of(), 0, List.of(),
				"https://example.atlassian.net/browse/SYNC-1");
		when(jira.status()).thenReturn(new JiraConnectionStatus(true, true, null));
		when(jira.searchIssues(any(), isNull())).thenReturn(new JiraSearchPage(List.of(issue), null, true));
		String admin = admin();

		long first = startSync(admin);
		awaitCompleted(admin, first).andExpect(jsonPath("$.createdCount").value(1))
				.andExpect(jsonPath("$.unchangedCount").value(0))
				.andExpect(jsonPath("$.finishedAt").isNotEmpty());
		assertThat(jdbc.queryForObject("""
				SELECT status FROM knowledge_item WHERE external_source = 'JIRA' AND external_key = 'SYNC-1'
				AND source_kind = 'JIRA' AND external_url LIKE '%/browse/SYNC-1'
				""", String.class)).isEqualTo("COMPLETED");

		long second = startSync(admin);
		awaitCompleted(admin, second).andExpect(jsonPath("$.createdCount").value(0))
				.andExpect(jsonPath("$.unchangedCount").value(1));

		mvc.perform(get(SYNC_RUNS).header("Authorization", admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(second))
				.andExpect(jsonPath("$[1].id").value(first));
	}

	@Test
	void signedInUsersBrowseAndFilterSyncedWorkItems() throws Exception {
		Instant older = Instant.parse("2026-11-01T00:00:00Z");
		Instant newer = Instant.parse("2026-11-02T00:00:00Z");
		JiraIssue first = view("VIEW-1", "Older item", "Task", "To Do", "Jane Doe", older);
		JiraIssue second = view("VIEW-2", "Newer item", "Bug", "Done", null, newer);
		when(jira.status()).thenReturn(new JiraConnectionStatus(true, true, null));
		when(jira.searchIssues(any(), isNull())).thenReturn(new JiraSearchPage(List.of(first, second), null, true));
		String admin = admin();
		awaitCompleted(admin, startSync(admin, "VIEW"));
		String user = token("user@acme.example", "user123");

		mvc.perform(get(ISSUES).param("project", "VIEW").header("Authorization", user)).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].key").value("VIEW-2"))
				.andExpect(jsonPath("$[1].summary").value("Older item"))
				.andExpect(jsonPath("$[1].projectName").value("View Project"))
				.andExpect(jsonPath("$[1].type").value("Task"))
				.andExpect(jsonPath("$[1].priority").value("High"))
				.andExpect(jsonPath("$[1].reporter").value("R"))
				.andExpect(jsonPath("$[1].parentKey").value("VIEW-0"))
				.andExpect(jsonPath("$[1].parentSummary").value("Epic"))
				.andExpect(jsonPath("$[1].browseUrl").value("https://example.atlassian.net/browse/VIEW-1"));
		assertKeys(user, "type", "Bug", "VIEW-2");
		assertKeys(user, "status", "To Do", "VIEW-1");
		assertKeys(user, "assignee", "Jane Doe", "VIEW-1");
		mvc.perform(get(ISSUES).param("project", "VIEW").param("status", " ").header("Authorization", user))
				.andExpect(jsonPath("$.length()").value(2));

		mvc.perform(get(ISSUES + "/VIEW-1").header("Authorization", user)).andExpect(status().isOk())
				.andExpect(jsonPath("$.workItem.key").value("VIEW-1"))
				.andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.containsString("# VIEW-1: Older item")));

		jdbc.update("UPDATE knowledge_item SET lifecycle_state = 'INACTIVE' WHERE external_key = 'VIEW-1'");
		mvc.perform(get(ISSUES + "/VIEW-1").header("Authorization", user)).andExpect(status().isNotFound());
		mvc.perform(get(ISSUES).param("project", "VIEW").header("Authorization", user))
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void workItemRoutesRequireSignIn() throws Exception {
		mvc.perform(get(ISSUES)).andExpect(status().isUnauthorized());
		mvc.perform(get(ISSUES + "/ACME-1")).andExpect(status().isUnauthorized());
	}

	@Test
	void anUnsyncedWorkItemIsNotFound() throws Exception {
		mvc.perform(get(ISSUES + "/NOPE-1").header("Authorization", admin())).andExpect(status().isNotFound());
	}

	@Test
	void aSecondSyncOfAnActiveProjectConflicts() throws Exception {
		when(jira.status()).thenReturn(new JiraConnectionStatus(true, true, null));
		jdbc.update("""
				INSERT INTO jira_sync_run (project_key, status, started_at, triggered_by)
				VALUES ('BUSY', 'RUNNING', now(), (SELECT min(id) FROM app_user))
				""");

		mvc.perform(post(PROJECTS + "/BUSY/sync").header("Authorization", admin()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONFLICT"));
	}

	@Test
	void anUnknownRunIsNotFound() throws Exception {
		mvc.perform(get(SYNC_RUNS + "/999999").header("Authorization", admin())).andExpect(status().isNotFound());
	}

	@Test
	void jiraFailuresBecomeSafeApiErrors() throws Exception {
		String admin = admin();
		assertFailure(admin, JiraErrorCode.NOT_CONFIGURED, 503, "JIRA_NOT_CONFIGURED");
		assertFailure(admin, JiraErrorCode.NOT_FOUND, 404, "NOT_FOUND");
		assertFailure(admin, JiraErrorCode.UNAUTHORIZED, 502, "JIRA_UNAVAILABLE");
	}

	private void assertKeys(String user, String filter, String value, String key) throws Exception {
		mvc.perform(get(ISSUES).param("project", "VIEW").param(filter, value).header("Authorization", user))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].key").value(key));
	}

	private static JiraIssue view(String key, String summary, String type, String status, String assignee,
			Instant updated) {
		return new JiraIssue("1", key, summary, "VIEW", "View Project", type, status, "High", List.of(), assignee, "R",
				"VIEW-0", "Epic", updated, updated, "Description", List.of(), 0, List.of(),
				"https://example.atlassian.net/browse/" + key);
	}

	private long startSync(String admin) throws Exception {
		return startSync(admin, "SYNC");
	}

	private long startSync(String admin, String project) throws Exception {
		String response = mvc.perform(post(PROJECTS + "/" + project + "/sync").header("Authorization", admin))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.projectKey").value(project))
				.andReturn().getResponse().getContentAsString();
		return json.readTree(response).path("id").asLong();
	}

	private ResultActions awaitCompleted(String admin, long runId) throws Exception {
		String path = SYNC_RUNS + "/" + runId;
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mvc
				.perform(get(path).header("Authorization", admin))
				.andExpect(jsonPath("$.status").value("COMPLETED")));
		return mvc.perform(get(path).header("Authorization", admin));
	}

	private void assertFailure(String admin, JiraErrorCode code, int httpStatus, String apiCode) throws Exception {
		when(jira.listProjects()).thenThrow(new JiraException(code, "internal detail"));
		mvc.perform(get(PROJECTS).header("Authorization", admin))
				.andExpect(status().is(httpStatus))
				.andExpect(jsonPath("$.code").value(apiCode))
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not("internal detail")));
		org.mockito.Mockito.reset(jira);
	}

	private String admin() throws Exception {
		return token("admin@acme.example", "admin123");
	}

	private String token(String email, String password) throws Exception {
		String response = mvc.perform(post("/api/auth/login").contentType("application/json")
				.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return "Bearer " + json.readTree(response).path("token").asString();
	}

}
