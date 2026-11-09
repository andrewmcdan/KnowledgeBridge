package com.capstone.knowledgebridge.jiraintegration;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.capstone.knowledgebridge.jira.JiraClient;
import com.capstone.knowledgebridge.jira.JiraErrorCode;
import com.capstone.knowledgebridge.jira.JiraException;
import com.capstone.knowledgebridge.jira.model.JiraConnectionStatus;
import com.capstone.knowledgebridge.jira.model.JiraProject;

import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@SpringBootTest(properties = {"knowledgebridge.gbrain.mode=in-memory", "knowledgebridge.gbrain.enabled=false"})
@AutoConfigureMockMvc
class JiraIntegrationControllerTests {

	private static final String STATUS = "/api/integrations/jira/status";

	private static final String PROJECTS = "/api/integrations/jira/projects";

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer(
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JsonMapper json;

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
	void jiraFailuresBecomeSafeApiErrors() throws Exception {
		String admin = admin();
		assertFailure(admin, JiraErrorCode.NOT_CONFIGURED, 503, "JIRA_NOT_CONFIGURED");
		assertFailure(admin, JiraErrorCode.NOT_FOUND, 404, "NOT_FOUND");
		assertFailure(admin, JiraErrorCode.UNAUTHORIZED, 502, "JIRA_UNAVAILABLE");
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
