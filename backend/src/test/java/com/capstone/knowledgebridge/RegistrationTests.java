package com.capstone.knowledgebridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.capstone.knowledgebridge.auth.AuthController;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@SpringBootTest(properties = {"knowledgebridge.gbrain.mode=in-memory", "knowledgebridge.gbrain.enabled=false"})
@AutoConfigureMockMvc
class RegistrationTests {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer(
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private PasswordEncoder passwords;
	@Autowired
	private JsonMapper json;

	@Test
	void registrationNormalizesIdentityHashesTheExactPasswordAndIssuesAUserSession() throws Exception {
		String password = "  Exact password  ";
		String content = json
				.writeValueAsString(Map.of("displayName", "  New Reader  ", "email", "  NEW.READER@EXAMPLE.TEST  ",
						"password", password, "role", "ADMIN", "enabled", false));
		String response = mvc.perform(post("/api/auth/register").contentType("application/json").content(content))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.expiresAt").isNotEmpty())
				.andExpect(jsonPath("$.user.displayName").value("New Reader"))
				.andExpect(jsonPath("$.user.email").value("new.reader@example.test"))
				.andExpect(jsonPath("$.user.role").value("USER"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.user.passwordHash").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		JsonNode session = json.readTree(response);
		long id = session.path("user").path("id").asLong();
		String authorization = "Bearer " + session.path("token").asString();
		Map<String, Object> row = jdbc.queryForMap("SELECT password_hash, role, enabled FROM app_user WHERE id = ?",
				id);
		String hash = (String) row.get("password_hash");
		assertThat(hash).isNotEqualTo(password);
		assertThat(passwords.matches(password, hash)).isTrue();
		assertThat(passwords.matches(password.trim(), hash)).isFalse();
		assertThat(row).containsEntry("role", "USER").containsEntry("enabled", true);
		mvc.perform(get("/api/auth/me").header("Authorization", authorization)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.role").value("USER"));
		mvc.perform(post("/api/knowledge-items/manual").header("Authorization", authorization)
				.contentType("application/json").content("{}"))
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/knowledge-items").header("Authorization", authorization)).andExpect(status().isOk());
		mvc.perform(post("/api/auth/logout").header("Authorization", authorization)).andExpect(status().isNoContent());
		mvc.perform(post("/api/auth/login").contentType("application/json")
				.content(json.writeValueAsString(Map.of("email", " NEW.READER@EXAMPLE.TEST ", "password", password))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(id));
	}

	@Test
	void invalidRegistrationRequestsAreRejectedAndPasswordBoundariesCountCodePointsAndBytes() throws Exception {
		for (String invalid : List.of("{}", "{\"displayName\":null,\"email\":null,\"password\":null}",
				body(" ", "blank-name@example.test", "abcdefgh"),
				body("x".repeat(256), "long-name@example.test", "abcdefgh"),
				body("Reader", " ", "abcdefgh"), body("Reader", "not-an-email", "abcdefgh"),
				body("Reader", "a".repeat(256) + "@example.test", "abcdefgh"),
				body("Reader", "invalid-password@example.test", "1234567"),
				body("Reader", "invalid-password@example.test", "a".repeat(73)),
				body("Reader", "invalid-password@example.test", "\uD83D\uDE00".repeat(4)),
				body("Reader", "invalid-password@example.test", "\uD83D\uDE00".repeat(19)))) {
			mvc.perform(post("/api/auth/register").contentType("application/json").content(invalid))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
		int index = 0;
		for (String valid : List.of("12345678", "        ", "a".repeat(72), "\uD83D\uDE00".repeat(8),
				"\uD83D\uDE00".repeat(18))) {
			mvc.perform(post("/api/auth/register").contentType("application/json")
					.content(body("x".repeat(255), "boundary-" + index++ + "@example.test", valid)))
					.andExpect(status().isCreated());
			mvc.perform(post("/api/auth/login").contentType("application/json")
					.content(json.writeValueAsString(
							Map.of("email", "boundary-" + (index - 1) + "@example.test", "password", valid))))
					.andExpect(status().isOk());
		}
		mvc.perform(get("/api/auth/register")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/auth/register").contentType("application/json").content("not json"))
				.andExpect(status().isBadRequest());
		assertThat(new AuthController.RegistrationRequest("Reader", "reader@example.test", "secret value").toString())
				.isEqualTo("RegistrationRequest[credentials=<redacted>]");
	}

	@Test
	void normalizedDuplicatesIncludingDisabledLegacyAccountsCannotBeReplaced() throws Exception {
		String hash = passwords.encode("legacy-password");
		Long id = jdbc.queryForObject(
				"INSERT INTO app_user (email, password_hash, display_name, role, enabled) VALUES (?, ?, 'Legacy', 'USER', false) RETURNING id",
				Long.class, " Disabled.Reader@Example.Test ", hash);
		mvc.perform(post("/api/auth/register").contentType("application/json")
				.content(body("Replacement", "disabled.reader@example.test", "replacement-password")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
		assertThat(jdbc.queryForMap("SELECT password_hash, display_name, enabled FROM app_user WHERE id = ?", id))
				.containsEntry("password_hash", hash).containsEntry("display_name", "Legacy")
				.containsEntry("enabled", false);
		jdbc.update("UPDATE app_user SET enabled = true WHERE id = ?", id);
		mvc.perform(post("/api/auth/login").contentType("application/json")
				.content(json.writeValueAsString(
						Map.of("email", "DISABLED.READER@EXAMPLE.TEST", "password", "legacy-password"))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(id));
	}

	@Test
	void concurrentRegistrationsCreateOnlyOneAccountForANormalizedEmail() throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> {
				start.await();
				return registrationStatus("simultaneous@example.test");
			});
			var second = executor.submit(() -> {
				start.await();
				return registrationStatus(" SIMULTANEOUS@EXAMPLE.TEST ");
			});
			start.countDown();
			assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(201, 409);
		}
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM app_user WHERE lower(btrim(email)) = 'simultaneous@example.test'", Long.class))
				.isEqualTo(1);
	}

	private int registrationStatus(String email) throws Exception {
		return mvc
				.perform(post("/api/auth/register").contentType("application/json")
						.content(body("Reader", email, "test-password")))
				.andReturn().getResponse().getStatus();
	}

	private String body(String name, String email, String password) {
		return json.writeValueAsString(Map.of("displayName", name, "email", email, "password", password));
	}
}
