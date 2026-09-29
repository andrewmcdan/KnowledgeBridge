package com.capstone.knowledgebridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class KnowledgeBridgeApplicationTests {

	private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
			.parse("pgvector/pgvector:pg17")
			.asCompatibleSubstituteFor("postgres");

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
			.withDatabaseName("knowledgebridge")
			.withUsername("knowledgebridge")
			.withPassword("knowledgebridge");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Test
	void contextLoadsWithPgvector() {
		Boolean vectorExtensionInstalled = jdbcTemplate.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')",
				Boolean.class);

		assertThat(vectorExtensionInstalled).isTrue();
	}

	@Test
	void healthEndpointReportsBackendAndDatabaseUp() throws Exception {
		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.database").value("UP"))
				.andExpect(jsonPath("$.timestamp").isNotEmpty());
	}

	@Test
	void loginRejectsUnknownEmailWrongPasswordAndBlankInput() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType("application/json")
				.content("{\"email\":\"nobody@acme.example\",\"password\":\"wrong\"}"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/auth/login")
				.contentType("application/json")
				.content("{\"email\":\"user@acme.example\",\"password\":\"wrong\"}"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/auth/login")
				.contentType("application/json")
				.content("{\"email\":\"\",\"password\":\"\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void disabledUserCannotLogIn() throws Exception {
		jdbcTemplate.update("UPDATE app_user SET enabled = false WHERE email = ?", "user@acme.example");
		try {
			mockMvc.perform(post("/api/auth/login")
					.contentType("application/json")
					.content("{\"email\":\"user@acme.example\",\"password\":\"user123\"}"))
					.andExpect(status().isUnauthorized());
		} finally {
			jdbcTemplate.update("UPDATE app_user SET enabled = true WHERE email = ?", "user@acme.example");
		}
	}

	@Test
	void loginIssuesUsableTokenForMeAndLogout() throws Exception {
		String token = loginAndGetToken("  USER@ACME.EXAMPLE  ", "user123", "USER");

		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("user@acme.example"))
				.andExpect(jsonPath("$.displayName").value("Acme User"))
				.andExpect(jsonPath("$.role").value("USER"));

		mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());
	}

	@Test
	void securityEnforcesAuthenticationAndAdminRole() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized());

		String userToken = loginAndGetToken("user@acme.example", "user123", "USER");
		mockMvc.perform(get("/api/admin/missing").header("Authorization", "Bearer " + userToken))
				.andExpect(status().isForbidden());

		String adminToken = loginAndGetToken("admin@acme.example", "admin123", "ADMIN");
		mockMvc.perform(get("/api/admin/missing").header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isNotFound());
	}

	@Test
	void authenticatedTokenWithoutRoleHasNoGrantedRole() throws Exception {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("knowledgebridge")
				.subject("42")
				.issuedAt(now)
				.expiresAt(now.plusSeconds(60))
				.claim("email", "roleless@acme.example")
				.claim("name", "Roleless User")
				.build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();

		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").doesNotExist());

		mockMvc.perform(get("/api/admin/missing").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void corsPreflightAllowsConfiguredFrontend() throws Exception {
		mockMvc.perform(options("/api/auth/login")
				.header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "POST"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
				.andExpect(header().string("Access-Control-Allow-Credentials", "true"));
	}

	private String loginAndGetToken(String email, String password, String role) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login")
				.contentType("application/json")
				.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.expiresAt").isNotEmpty())
				.andExpect(jsonPath("$.user.role").value(role))
				.andReturn();
		return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.token");
	}

}
