package com.capstone.knowledgebridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.capstone.knowledgebridge.gbrain.GbrainClient;
import com.capstone.knowledgebridge.gbrain.model.GbrainWriteResult;

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

	// Replaces whatever GbrainClient bean is on the classpath (right now, the throwaway local stub) so
	// these tests control gbrain's response deterministically instead of depending on it.
	@MockitoBean
	private GbrainClient gbrainClient;

	@BeforeEach
	void stubGbrainSuccessByDefault() {
		when(gbrainClient.upsertDocument(any()))
				.thenReturn(new GbrainWriteResult("stub-id", GbrainWriteResult.Status.WRITTEN, 1));
	}

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

	@Test
	void manualIngestionCreatesCompletedKnowledgeItemAndRecordsAttempt() throws Exception {
		String adminToken = loginAndGetToken("admin@acme.example", "admin123", "ADMIN");

		MvcResult result = mockMvc.perform(post("/api/knowledge-items/manual")
				.header("Authorization", "Bearer " + adminToken)
				.contentType("application/json")
				.content("{\"title\":\"Test Policy\",\"type\":\"Finance Policy\",\"text\":\"Body text.\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.externalEngineId").value("stub-id"))
				.andReturn();

		long id = ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id"))
				.longValue();
		Integer attemptCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM ingestion_attempt WHERE knowledge_item_id = ?", Integer.class, id);
		assertThat(attemptCount).isEqualTo(1);
	}

	@Test
	void manualIngestionRequiresAdminRole() throws Exception {
		mockMvc.perform(post("/api/knowledge-items/manual")
				.contentType("application/json")
				.content("{\"title\":\"x\",\"type\":\"x\",\"text\":\"x\"}"))
				.andExpect(status().isUnauthorized());

		String userToken = loginAndGetToken("user@acme.example", "user123", "USER");
		mockMvc.perform(post("/api/knowledge-items/manual")
				.header("Authorization", "Bearer " + userToken)
				.contentType("application/json")
				.content("{\"title\":\"x\",\"type\":\"x\",\"text\":\"x\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void uploadIngestionCreatesCompletedKnowledgeItemFromMarkdownFile() throws Exception {
		String adminToken = loginAndGetToken("admin@acme.example", "admin123", "ADMIN");
		MockMultipartFile file = new MockMultipartFile("file", "doc.md", "text/markdown",
				"# Hello\n\nBody.".getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(multipart("/api/knowledge-items/upload")
				.file(file)
				.param("title", "Upload Test")
				.param("type", "Guideline")
				.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("COMPLETED"));
	}

	@Test
	void uploadFailsGracefullyWhenContentExceedsGbrainsBodyLimit() throws Exception {
		// Note: Spring's own spring.servlet.multipart.max-file-size (413) can't be exercised through
		// MockMvc's multipart() builder - it bypasses real servlet-container multipart parsing entirely,
		// which is where that limit is actually enforced. Verified live against a real running server
		// instead. This test covers something MockMvc *can* prove: GbrainDocument's own 1MB body limit
		// (smaller than our 5MB upload limit) throwing during construction gets caught the same as any
		// other gbrain failure, instead of escaping as an uncaught 500.
		String adminToken = loginAndGetToken("admin@acme.example", "admin123", "ADMIN");
		byte[] overGbrainLimitContent = "a".repeat(2 * 1024 * 1024).getBytes(StandardCharsets.UTF_8);
		MockMultipartFile file = new MockMultipartFile("file", "big.md", "text/markdown", overGbrainLimitContent);

		mockMvc.perform(multipart("/api/knowledge-items/upload")
				.file(file)
				.param("title", "Too Big For Gbrain")
				.param("type", "Policy")
				.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("FAILED"));
	}

	@Test
	void retryIngestionRoundTripPreservesAttemptHistory() throws Exception {
		String adminToken = loginAndGetToken("admin@acme.example", "admin123", "ADMIN");

		MvcResult createResult = mockMvc.perform(post("/api/knowledge-items/manual")
				.header("Authorization", "Bearer " + adminToken)
				.contentType("application/json")
				.content("{\"title\":\"Retry Test\",\"type\":\"Policy\",\"text\":\"Body.\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		long id = ((Number) com.jayway.jsonpath.JsonPath.read(createResult.getResponse().getContentAsString(), "$.id"))
				.longValue();

		// Simulate a real gbrain failure directly in the DB - the stub can't actually fail.
		jdbcTemplate.update("UPDATE knowledge_item SET status = 'FAILED', external_engine_id = NULL WHERE id = ?", id);

		mockMvc.perform(post("/api/knowledge-items/" + id + "/retry-ingestion")
				.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"));

		Integer attemptCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM ingestion_attempt WHERE knowledge_item_id = ?", Integer.class, id);
		assertThat(attemptCount).isEqualTo(2);

		mockMvc.perform(post("/api/knowledge-items/999999/retry-ingestion")
				.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/knowledge-items/" + id + "/retry-ingestion")
				.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isConflict());
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
