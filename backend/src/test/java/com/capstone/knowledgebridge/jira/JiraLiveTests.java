package com.capstone.knowledgebridge.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.capstone.knowledgebridge.jira.model.JiraIssue;
import com.capstone.knowledgebridge.jira.model.JiraSearchPage;

import tools.jackson.databind.json.JsonMapper;

/**
 * Read-only end-to-end check of the adapter and routes against the real Jira site named by the JIRA_* variables. It
 * needs the seeded ACME project, and is excluded from {@code test}. Run it with {@code gradlew liveTest}.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "JIRA_API_TOKEN", matches = ".+")
@Testcontainers
@SpringBootTest(properties = {"knowledgebridge.gbrain.mode=in-memory", "knowledgebridge.gbrain.enabled=false",
		"knowledgebridge.jira.enabled=true", "knowledgebridge.jira.page-size=10"})
@AutoConfigureMockMvc
class JiraLiveTests {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer(
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JsonMapper json;

	@Autowired
	private JiraClient jira;

	@Test
	void routesReachTheRealSite() throws Exception {
		String login = mvc.perform(post("/api/auth/login").contentType("application/json")
				.content("{\"email\":\"admin@acme.example\",\"password\":\"admin123\"}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String admin = "Bearer " + json.readTree(login).path("token").asString();

		mvc.perform(get("/api/integrations/jira/status").header("Authorization", admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.configured").value(true))
				.andExpect(jsonPath("$.reachable").value(true));
		mvc.perform(get("/api/integrations/jira/projects").header("Authorization", admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.key == 'ACME')].name").value("Acme Delivery"));
	}

	@Test
	void readsAWholeProjectThroughPaging() {
		List<JiraIssue> issues = new ArrayList<>();
		String token = null;
		JiraSearchPage page;
		do {
			page = jira.searchIssues("project = ACME AND labels = kb-seed ORDER BY key ASC", token);
			issues.addAll(page.issues());
			token = page.nextPageToken();
		} while (!page.last());

		assertThat(issues).hasSize(22);
		JiraIssue drill = issues.stream().filter(issue -> issue.key().equals("ACME-5")).findFirst().orElseThrow();
		assertThat(drill.projectName()).isEqualTo("Acme Delivery");
		assertThat(drill.parentKey()).isEqualTo("ACME-1");
		assertThat(drill.description()).contains("restore drill");
		assertThat(drill.links()).isNotEmpty();
		assertThat(drill.commentTotal()).isEqualTo(1);
		assertThat(drill.comments()).hasSize(1);
		assertThat(jira.listComments("ACME-5")).hasSize(1);
		assertThat(jira.getIssue("ACME-5")).isPresent();
		assertThat(jira.getIssue("ACME-99999")).isEmpty();
	}

}
