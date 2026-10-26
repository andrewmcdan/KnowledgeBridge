package com.capstone.knowledgebridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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

import com.capstone.knowledgebridge.extraction.DocxFixtures;
import com.capstone.knowledgebridge.gbrain.GbrainClient;
import com.capstone.knowledgebridge.gbrain.GbrainErrorCode;
import com.capstone.knowledgebridge.gbrain.GbrainException;
import com.capstone.knowledgebridge.gbrain.model.GbrainSynthesisResult;
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

	// Replaces the real GbrainClient bean so these tests control gbrain's response deterministically
	// instead of depending on a running gbrain.
	@MockitoBean
	private GbrainClient gbrainClient;

	@BeforeEach
	void stubGbrainSuccessByDefault() {
		when(gbrainClient.extractEntities(any()))
				.thenReturn(new com.capstone.knowledgebridge.gbrain.model.GbrainEntityExtraction(List.of(), false));
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

		// The agreed logout contract is client-side token disposal, not server-side JWT revocation.
		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
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
	void authenticationAndAuthorizationErrorsShareTheApiEnvelope() throws Exception {
		mockMvc.perform(get("/api/auth/me").header("Origin", "http://localhost:5173"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer"))
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer invalid-token"))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		Instant now = Instant.now();
		String expired = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
				JwtClaimsSet.builder().issuer("knowledgebridge").subject("1").issuedAt(now.minusSeconds(3600))
						.expiresAt(now.minusSeconds(300)).claim("role", "ADMIN").build()))
				.getTokenValue();
		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		mockMvc.perform(post("/api/auth/login").contentType("application/json")
				.content("{\"email\":\"unknown@acme.example\",\"password\":\"wrong\"}"))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

		String user = "Bearer " + loginAndGetToken("user@acme.example", "user123", "USER");
		mockMvc.perform(post("/api/knowledge-items/manual").header("Authorization", user)
				.contentType("application/json").content("{"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mockMvc.perform(multipart("/api/knowledge-items/upload").header("Authorization", user))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mockMvc.perform(get("/api/admin/cache").header("Authorization", user))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		for (String path : List.of("/api/knowledge-items/manual", "/api/knowledge-items/1/retry-ingestion",
				"/api/knowledge-items/1/extract-entities")) {
			mockMvc.perform(post(path).header("Authorization", user).contentType("application/json")
					.content("{\"title\":\"x\",\"type\":\"note\",\"text\":\"body\"}"))
					.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		}
		for (String path : List.of("/api/knowledge-items/upload", "/api/knowledge-items/video")) {
			mockMvc.perform(
					multipart(path).file(new MockMultipartFile("file", "note.md", "text/markdown", new byte[]{1}))
							.param("title", "Note").param("type", "note").header("Authorization", user))
					.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		}
	}

	@Test
	void ingestionValidationAndMissingResourcesShareTheApiEnvelope() throws Exception {
		String admin = "Bearer " + loginAndGetToken("admin@acme.example", "admin123", "ADMIN");
		mockMvc.perform(post("/api/knowledge-items/manual").header("Authorization", admin)
				.contentType("application/json").content("{\"title\":\"\",\"type\":\"note\",\"text\":\"body\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mockMvc.perform(multipart("/api/knowledge-items/upload").param("title", "Title").param("type", "note")
				.header("Authorization", admin))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mockMvc.perform(multipart("/api/knowledge-items/upload")
				.file(new MockMultipartFile("file", "file.pdf", "application/pdf", new byte[]{1}))
				.param("title", "Title").param("type", "note").header("Authorization", admin))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
		mockMvc.perform(post("/api/knowledge-items/999999/retry-ingestion").header("Authorization", admin))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
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
	void docxUploadIsIngestedAsMarkdownWithItsSourceMetadata() throws Exception {
		String adminToken = loginAndGetToken("admin@acme.example", "admin123", "ADMIN");
		byte[] docx = DocxFixtures.docx(document -> {
			DocxFixtures.style(document, "Heading1", "heading 1");
			DocxFixtures.styled(document, "Heading1", "Equipment");
			DocxFixtures.paragraph(document, "Monitors are replaced every 5 years.");
		});
		MockMultipartFile file = new MockMultipartFile("file", "equipment.docx",
				"application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx);

		MvcResult result = mockMvc.perform(multipart("/api/knowledge-items/upload")
				.file(file)
				.param("title", "Equipment Guide")
				.param("type", "Guide")
				.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.sourceKind").value("DOCX"))
				.andExpect(jsonPath("$.originalFilename").value("equipment.docx"))
				.andReturn();

		long id = ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id"))
				.longValue();
		Map<String, Object> row = jdbcTemplate.queryForMap(
				"SELECT body_text, source_path, source_sha256 FROM knowledge_item WHERE id = ?", id);
		assertThat(row.get("body_text")).isEqualTo("# Equipment\n\nMonitors are replaced every 5 years.\n");
		assertThat(row.get("source_path")).isNull();
		assertThat((String) row.get("source_sha256")).hasSize(64);
	}

	@Test
	void uploadRejectsContentOverGbrainsBodyLimitWithoutCreatingAnItem() throws Exception {
		// MockMvc's multipart() bypasses servlet-container multipart parsing, so this reaches the
		// controller's own gbrain body-limit check rather than spring.servlet.multipart.max-file-size.
		String adminToken = loginAndGetToken("admin@acme.example", "admin123", "ADMIN");
		byte[] overGbrainLimitContent = "a".repeat(2 * 1024 * 1024).getBytes(StandardCharsets.UTF_8);
		MockMultipartFile file = new MockMultipartFile("file", "big.md", "text/markdown", overGbrainLimitContent);
		Integer itemsBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM knowledge_item", Integer.class);

		mockMvc.perform(multipart("/api/knowledge-items/upload")
				.file(file)
				.param("title", "Too Big For Gbrain")
				.param("type", "Policy")
				.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isContentTooLarge());

		Integer itemsAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM knowledge_item", Integer.class);
		assertThat(itemsAfter).isEqualTo(itemsBefore);
	}

	@Test
	void listAndGetExposeStatusAndLatestFailureToAdminsOnly() throws Exception {
		String adminToken = loginAndGetToken("admin@acme.example", "admin123", "ADMIN");
		when(gbrainClient.upsertDocument(any()))
				.thenThrow(new GbrainException(GbrainErrorCode.TIMEOUT, "gbrain did not respond in time"));

		MvcResult createResult = mockMvc.perform(post("/api/knowledge-items/manual")
				.header("Authorization", "Bearer " + adminToken)
				.contentType("application/json")
				.content("{\"title\":\"Listed Failure\",\"type\":\"Policy\",\"text\":\"Body.\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.lastErrorCode").value("TIMEOUT"))
				.andReturn();
		long id = ((Number) com.jayway.jsonpath.JsonPath.read(createResult.getResponse().getContentAsString(), "$.id"))
				.longValue();

		// Newest first, so the item just created leads the list.
		mockMvc.perform(get("/api/knowledge-items").header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(id))
				.andExpect(jsonPath("$[0].status").value("FAILED"))
				.andExpect(jsonPath("$[0].lastErrorCode").value("TIMEOUT"))
				.andExpect(jsonPath("$[0].lastErrorMessage").value("gbrain did not respond in time"));

		mockMvc.perform(get("/api/knowledge-items/" + id).header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Listed Failure"))
				.andExpect(jsonPath("$.lastErrorCode").value("TIMEOUT"));

		mockMvc.perform(get("/api/knowledge-items/999999").header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isNotFound());

		String userToken = loginAndGetToken("user@acme.example", "user123", "USER");
		mockMvc.perform(get("/api/knowledge-items").header("Authorization", "Bearer " + userToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
		mockMvc.perform(get("/api/knowledge-items/" + id).header("Authorization", "Bearer " + userToken))
				.andExpect(status().isForbidden());

		// A successful retry clears the error fields - they only describe the latest attempt. doReturn
		// because when(...) would invoke the mock, which is still stubbed to throw.
		doReturn(new GbrainWriteResult("stub-id", GbrainWriteResult.Status.WRITTEN, 1)).when(gbrainClient)
				.upsertDocument(any());
		mockMvc.perform(post("/api/knowledge-items/" + id + "/retry-ingestion")
				.header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.lastErrorCode").doesNotExist());
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

	@Test
	void repeatedSynthesisIsServedFromTheCacheUntilTheKnowledgeRevisionChanges() throws Exception {
		String userToken = loginAndGetToken("user@acme.example", "user123", "USER");
		String question = "{\"question\":\"How many PTO days do employees get?\"}";
		when(gbrainClient.synthesize(any())).thenReturn(new GbrainSynthesisResult("Employees get 15 PTO days.",
				GbrainSynthesisResult.Status.SYNTHESIZED, List.of(), List.of(),
				new GbrainSynthesisResult.Usage("openrouter:model", 600L, 80L, new BigDecimal("0.0002")), List.of()));

		// First ask: a miss, so gbrain answers and the answer is cached with its real cost.
		mockMvc.perform(post("/api/query/synthesize")
				.header("Authorization", "Bearer " + userToken)
				.contentType("application/json")
				.content(question))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.answer").value("Employees get 15 PTO days."))
				.andExpect(jsonPath("$.cacheHit").value(false))
				.andExpect(jsonPath("$.usage.inputTokens").value(600));

		// Same question again: served from the cache, no second gbrain call, and no cost reported.
		mockMvc.perform(post("/api/query/synthesize")
				.header("Authorization", "Bearer " + userToken)
				.contentType("application/json")
				.content(question))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.answer").value("Employees get 15 PTO days."))
				.andExpect(jsonPath("$.cacheHit").value(true))
				.andExpect(jsonPath("$.usage.model").value("openrouter:model"))
				.andExpect(jsonPath("$.usage.inputTokens").value(0))
				.andExpect(jsonPath("$.usage.estimatedCostUsd").value(0));
		verify(gbrainClient, times(1)).synthesize(any());
		assertThat(jdbcTemplate.queryForObject(
				"SELECT hit_count FROM query_cache WHERE exact_query_text = 'How many PTO days do employees get?'",
				Integer.class)).isEqualTo(1);

		// Ingesting a document bumps the knowledge revision, so the same question goes back to gbrain.
		String adminToken = loginAndGetToken("admin@acme.example", "admin123", "ADMIN");
		mockMvc.perform(post("/api/knowledge-items/manual")
				.header("Authorization", "Bearer " + adminToken)
				.contentType("application/json")
				.content("{\"title\":\"PTO Policy\",\"type\":\"Policy\",\"text\":\"Employees get 20 PTO days.\"}"))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/query/synthesize")
				.header("Authorization", "Bearer " + userToken)
				.contentType("application/json")
				.content(question))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cacheHit").value(false));
		verify(gbrainClient, times(2)).synthesize(any());
	}

	@Test
	void cacheAdminRoutesAreAdminOnly() throws Exception {
		mockMvc.perform(get("/api/admin/cache")).andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/admin/cache")).andExpect(status().isUnauthorized());

		String userToken = loginAndGetToken("user@acme.example", "user123", "USER");
		mockMvc.perform(get("/api/admin/cache").header("Authorization", "Bearer " + userToken))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/admin/cache").header("Authorization", "Bearer " + userToken))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminCanListAndClearTheCache() throws Exception {
		String userToken = loginAndGetToken("user@acme.example", "user123", "USER");
		String adminToken = loginAndGetToken("admin@acme.example", "admin123", "ADMIN");
		when(gbrainClient.synthesize(any())).thenReturn(new GbrainSynthesisResult("Answer.",
				GbrainSynthesisResult.Status.SYNTHESIZED, List.of(), List.of(), null, List.of()));
		mockMvc.perform(post("/api/query/synthesize")
				.header("Authorization", "Bearer " + userToken)
				.contentType("application/json")
				.content("{\"question\":\"Who approves admin cache tests?\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/admin/cache").header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].queryText").value("Who approves admin cache tests?"))
				.andExpect(jsonPath("$[0].mode").value("SYNTHESIS"))
				.andExpect(jsonPath("$[0].hitCount").value(0))
				.andExpect(jsonPath("$[0].current").value(true));

		mockMvc.perform(delete("/api/admin/cache").header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.deleted").isNumber());
		mockMvc.perform(get("/api/admin/cache").header("Authorization", "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void entitiesLinkIndexedDocumentsDeduplicateFilterAndReplaceOnRetry() throws Exception {
		String admin = "Bearer " + loginAndGetToken("admin@acme.example", "admin123", "ADMIN");
		String user = "Bearer " + loginAndGetToken("user@acme.example", "user123", "USER");
		var entity = new com.capstone.knowledgebridge.gbrain.model.GbrainEntity("people/entity-test-alice",
				"Alice Entitytest", "PERSON", "A noisy detection.");
		when(gbrainClient.extractEntities(any())).thenReturn(
				new com.capstone.knowledgebridge.gbrain.model.GbrainEntityExtraction(List.of(entity, entity), true));
		long first = createEntityTestDocument(admin, "Entity first");
		long second = createEntityTestDocument(admin, "Entity second");
		MvcResult list = mockMvc
				.perform(get("/api/entities").header("Authorization", user).param("name", "alice entitytest")
						.param("type", "person").param("limit", "1"))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].documentCount").value(2))
				.andExpect(jsonPath("$[0].verified").value(false)).andReturn();
		long id = ((Number) com.jayway.jsonpath.JsonPath.read(list.getResponse().getContentAsString(), "$[0].id"))
				.longValue();
		mockMvc.perform(get("/api/entities/{id}", id).header("Authorization", user)).andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Alice Entitytest"));
		mockMvc.perform(get("/api/entities/{id}/documents", id).header("Authorization", user))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
		mockMvc.perform(get("/api/knowledge-items/{id}/content", first).header("Authorization", user))
				.andExpect(status().isOk()).andExpect(jsonPath("$.text").value("Alice Entitytest owns Project Cedar."));
		mockMvc.perform(get("/api/knowledge-items/{id}/entities", first).header("Authorization", user))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.truncated").value(true));
		when(gbrainClient.extractEntities(any()))
				.thenReturn(new com.capstone.knowledgebridge.gbrain.model.GbrainEntityExtraction(List.of(), false));
		mockMvc.perform(post("/api/knowledge-items/{id}/extract-entities", first).header("Authorization", admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.entities").isEmpty());
		mockMvc.perform(get("/api/entities/{id}", id).header("Authorization", user))
				.andExpect(jsonPath("$.documentCount").value(1));
		jdbcTemplate.update("UPDATE knowledge_item SET revision = revision + 1 WHERE id = ?", second);
		mockMvc.perform(get("/api/entities/{id}", id).header("Authorization", user)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/knowledge-items/{id}/entities", second).header("Authorization", user))
				.andExpect(jsonPath("$.status").value("NOT_EXTRACTED"));
	}

	@Test
	void entityFailuresRemainSeparateFromIndexedContentAndCanBeRetried() throws Exception {
		String admin = "Bearer " + loginAndGetToken("admin@acme.example", "admin123", "ADMIN");
		when(gbrainClient.extractEntities(any())).thenThrow(new GbrainException(GbrainErrorCode.TIMEOUT, "Timed out"));
		long id = createEntityTestDocument(admin, "Entity failure");
		mockMvc.perform(get("/api/knowledge-items/{id}", id).header("Authorization", admin))
				.andExpect(jsonPath("$.status").value("COMPLETED"));
		mockMvc.perform(get("/api/knowledge-items/{id}/entities", id).header("Authorization", admin))
				.andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.errorCode").value("TIMEOUT"));
		mockMvc.perform(post("/api/knowledge-items/{id}/extract-entities", id).header("Authorization", admin))
				.andExpect(jsonPath("$.status").value("FAILED"));
		doReturn(new com.capstone.knowledgebridge.gbrain.model.GbrainEntityExtraction(List.of(), false))
				.when(gbrainClient).extractEntities(any());
		mockMvc.perform(post("/api/knowledge-items/{id}/extract-entities", id).header("Authorization", admin))
				.andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.errorCode").doesNotExist());
	}

	@Test
	void entityRoutesValidateFiltersRequireAuthenticationAndRestrictExtractionToAdmins() throws Exception {
		String user = "Bearer " + loginAndGetToken("user@acme.example", "user123", "USER");
		String admin = "Bearer " + loginAndGetToken("admin@acme.example", "admin123", "ADMIN");
		mockMvc.perform(get("/api/entities")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/entities").header("Authorization", user)).andExpect(status().isOk());
		mockMvc.perform(get("/api/entities").header("Authorization", user).param("name", "x".repeat(256)))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/entities").header("Authorization", user).param("type", "bad"))
				.andExpect(status().isBadRequest());
		for (String limit : List.of("0", "101", "oops")) {
			mockMvc.perform(get("/api/entities").header("Authorization", user).param("limit", limit))
					.andExpect(status().isBadRequest());
		}
		mockMvc.perform(get("/api/entities/999999999/documents").header("Authorization", user))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/knowledge-items/999999999/entities").header("Authorization", user))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/knowledge-items/999999999/content").header("Authorization", user))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/knowledge-items/999999999/extract-entities").header("Authorization", user))
				.andExpect(status().isForbidden());
		when(gbrainClient.upsertDocument(any())).thenThrow(new GbrainException(GbrainErrorCode.UNAVAILABLE, "Offline"));
		MvcResult result = mockMvc
				.perform(post("/api/knowledge-items/manual").header("Authorization", admin)
						.contentType("application/json")
						.content("{\"title\":\"Unindexed entity test\",\"type\":\"note\",\"text\":\"body\"}"))
				.andExpect(status().isCreated()).andReturn();
		long id = ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id"))
				.longValue();
		mockMvc.perform(post("/api/knowledge-items/{id}/extract-entities", id).header("Authorization", admin))
				.andExpect(status().isConflict());
		mockMvc.perform(get("/api/knowledge-items/{id}/content", id).header("Authorization", user))
				.andExpect(status().isNotFound());
	}

	private long createEntityTestDocument(String authorization, String title) throws Exception {
		MvcResult result = mockMvc
				.perform(post("/api/knowledge-items/manual").header("Authorization", authorization)
						.contentType("application/json")
						.content("{\"title\":\"" + title
								+ "\",\"type\":\"note\",\"text\":\"Alice Entitytest owns Project Cedar.\"}"))
				.andExpect(status().isCreated()).andReturn();
		return ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id"))
				.longValue();
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
