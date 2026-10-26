package com.capstone.knowledgebridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@SpringBootTest(properties = {"knowledgebridge.gbrain.mode=in-memory", "knowledgebridge.gbrain.enabled=false"})
@AutoConfigureMockMvc
class UserAdministrationTests {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer(
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));
	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private JsonMapper json;

	@Test
	void userListingAndPromotionRequireAnExistingAdmin() throws Exception {
		mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/admin/users/2/promote")).andExpect(status().isUnauthorized());
		String user = authorization(login("user@acme.example", "user123"));
		mvc.perform(get("/api/admin/users").header("Authorization", user))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mvc.perform(post("/api/admin/users/2/promote").header("Authorization", user)
				.contentType("application/json").content("{\"role\":\"ADMIN\"}"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		assertThat(jdbc.queryForObject("SELECT role FROM app_user WHERE id=2", String.class)).isEqualTo("USER");
		String admin = admin();
		String content = mvc.perform(get("/api/admin/users").header("Authorization", admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].displayName").value("Acme Admin"))
				.andExpect(jsonPath("$[0].enabled").value(true)).andReturn().getResponse().getContentAsString();
		assertThat(content).doesNotContain("password", "hash", "token");
	}

	@Test
	void promotionPreservesCredentialsAndOwnershipAndRequiresANewSessionForAdminAccess() throws Exception {
		JsonNode original = register("promoted@example.test");
		long id = original.path("user").path("id").asLong();
		String hash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class, id);
		jdbc.update(
				"INSERT INTO knowledge_item (title,type,owner_id,status,body_text,character_count) VALUES ('Owned','Note',?,'COMPLETED','Body',4)",
				id);
		mvc.perform(post("/api/admin/users/" + id + "/promote").header("Authorization", admin())
				.contentType("application/json").content("{\"role\":\"USER\",\"enabled\":false}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.role").value("ADMIN")).andExpect(jsonPath("$.enabled").value(true))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
		assertThat(jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class, id))
				.isEqualTo(hash);
		assertThat(jdbc.queryForObject("SELECT owner_id FROM knowledge_item WHERE title='Owned'", Long.class))
				.isEqualTo(id);
		mvc.perform(get("/api/admin/users").header("Authorization", authorization(original)))
				.andExpect(status().isForbidden());
		JsonNode updated = login("promoted@example.test", "test password");
		assertThat(updated.path("user").path("role").asString()).isEqualTo("ADMIN");
		mvc.perform(get("/api/admin/users").header("Authorization", authorization(updated))).andExpect(status().isOk());
		mvc.perform(post("/api/knowledge-items/manual").header("Authorization", authorization(updated))
				.contentType("application/json")
				.content("{\"title\":\"New admin note\",\"type\":\"Note\",\"text\":\"Body\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	void missingAndDisabledTargetsAreRejectedWithoutChangingAccounts() throws Exception {
		long id = register("disabled-promotion@example.test").path("user").path("id").asLong();
		jdbc.update("UPDATE app_user SET enabled=false WHERE id=?", id);
		String admin = admin();
		mvc.perform(post("/api/admin/users/" + id + "/promote").header("Authorization", admin))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
		assertThat(jdbc.queryForMap("SELECT role,enabled FROM app_user WHERE id=?", id))
				.containsEntry("role", "USER").containsEntry("enabled", false);
		mvc.perform(post("/api/admin/users/999999/promote").header("Authorization", admin))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mvc.perform(post("/api/admin/users/invalid/promote").header("Authorization", admin))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void repeatedAndConcurrentPromotionsAreSafeAndDoNotRewriteExistingAdmins() throws Exception {
		long id = register("concurrent-promotion@example.test").path("user").path("id").asLong();
		String admin = admin();
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> {
				start.await();
				return promoteStatus(id, admin);
			});
			var second = executor.submit(() -> {
				start.await();
				return promoteStatus(id, admin);
			});
			start.countDown();
			assertThat(first.get()).isEqualTo(200);
			assertThat(second.get()).isEqualTo(200);
		}
		Object updated = jdbc.queryForObject("SELECT updated_at FROM app_user WHERE id=?", Object.class, id);
		assertThat(promoteStatus(id, admin)).isEqualTo(200);
		assertThat(jdbc.queryForObject("SELECT updated_at FROM app_user WHERE id=?", Object.class, id))
				.isEqualTo(updated);
	}

	private int promoteStatus(long id, String admin) throws Exception {
		return mvc.perform(post("/api/admin/users/" + id + "/promote").header("Authorization", admin))
				.andReturn().getResponse().getStatus();
	}

	private JsonNode register(String email) throws Exception {
		return json.readTree(mvc.perform(post("/api/auth/register").contentType("application/json")
				.content(json.writeValueAsString(
						Map.of("displayName", "Reader", "email", email, "password", "test password"))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	private JsonNode login(String email, String password) throws Exception {
		return json.readTree(mvc.perform(post("/api/auth/login").contentType("application/json")
				.content(json.writeValueAsString(Map.of("email", email, "password", password))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private String admin() throws Exception {
		return authorization(login("admin@acme.example", "admin123"));
	}

	private String authorization(JsonNode session) {
		return "Bearer " + session.path("token").asString();
	}
}
