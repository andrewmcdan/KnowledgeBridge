package com.capstone.knowledgebridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.capstone.knowledgebridge.extraction.DocxFixtures;
import com.capstone.knowledgebridge.gbrain.GbrainClient;
import com.capstone.knowledgebridge.gbrain.GbrainErrorCode;
import com.capstone.knowledgebridge.gbrain.GbrainException;
import com.capstone.knowledgebridge.knowledge.KnowledgeItem;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemRepository;
import com.capstone.knowledgebridge.video.VideoIngestionDispatcher;
import com.capstone.knowledgebridge.video.VideoIngestionJob;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@SpringBootTest(properties = {"knowledgebridge.gbrain.mode=in-memory", "knowledgebridge.gbrain.enabled=false",
		"knowledgebridge.titles.api-key=test-key", "knowledgebridge.titles.model=test-model"})
@AutoConfigureMockMvc
class UploadTitleTests {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer(
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));
	@TempDir
	static Path uploads;
	private static HttpServer provider;
	private static final AtomicInteger calls = new AtomicInteger();
	private static volatile boolean unavailable;
	private static volatile String lastInput;
	private static final JsonMapper json = new JsonMapper();

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) throws IOException {
		provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		provider.createContext("/chat/completions", exchange -> {
			calls.incrementAndGet();
			lastInput = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String result = json.writeValueAsString(Map.of("choices", List.of(Map.of("finish_reason", "stop", "message",
					Map.of("content", "{\"title\":\"Meeting October 27, 2026 - Budget Review\"}")))));
			byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(unavailable ? 503 : 200, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		provider.start();
		registry.add("knowledgebridge.titles.base-url", () -> "http://127.0.0.1:" + provider.getAddress().getPort());
		registry.add("knowledgebridge.video.storage-directory", () -> uploads.toString());
	}

	@AfterAll
	static void stopProvider() {
		provider.stop(0);
	}

	@Autowired
	private MockMvc mvc;
	@Autowired
	private KnowledgeItemRepository repository;
	@Autowired
	private VideoIngestionJob videoJob;
	@MockitoBean
	private VideoIngestionDispatcher dispatcher;
	@MockitoSpyBean
	private GbrainClient gbrain;

	@BeforeEach
	void resetProvider() {
		calls.set(0);
		unavailable = false;
	}

	@Test
	void markdownAndDocxUseTheSuppliedGuideAndPlainUploadsDoNotUseAi() throws Exception {
		String admin = login("admin@acme.example", "admin123");
		MockMultipartFile md = new MockMultipartFile("file", "budget_review.md", "text/markdown",
				"Budget review.".getBytes(StandardCharsets.UTF_8));
		MockMultipartFile docx = new MockMultipartFile("file", "budget.docx",
				"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
				DocxFixtures.docx(document -> DocxFixtures.paragraph(document, "Budget allocation discussion.")));
		for (MockMultipartFile file : List.of(md, docx)) {
			JsonNode item = upload(file, true, admin, 201);
			assertThat(item.path("title").asString()).isEqualTo("Meeting October 27, 2026 - Budget Review");
			assertThat(item.path("status").asString()).isEqualTo("COMPLETED");
			JsonNode input = json
					.readTree(json.readTree(lastInput).path("messages").path(1).path("content").asString());
			assertThat(input.path("titleGuidance").asString()).isEqualTo("Meeting 10/27/2026");
			assertThat(input.path("contentExcerpt").asString()).contains("Budget");
		}
		assertThat(upload(md, false, admin, 201).path("title").asString()).isEqualTo("Meeting 10/27/2026");
		assertThat(calls.get()).isEqualTo(2);
		mvc.perform(multipart("/api/knowledge-items/upload").file(md).param("title", "Title").param("type", "Note")
				.param("useAiTitle", "true").header("Authorization", login("user@acme.example", "user123")))
				.andExpect(status().isForbidden());
		mvc.perform(multipart("/api/knowledge-items/upload").file(md).param("title", "Title").param("type", "Note")
				.param("useAiTitle", "invalid").header("Authorization", admin)).andExpect(status().isBadRequest());
	}

	@Test
	void videoNamingUsesThePersistedTranscriptAndSurvivesAWorkerReload() throws Exception {
		MockMultipartFile video = new MockMultipartFile("file", "meeting.mp4", "video/mp4", new byte[]{1, 2, 3});
		long id = upload(video, true, login("admin@acme.example", "admin123"), 202).path("id").asLong();
		KnowledgeItem saved = repository.findById(id).orElseThrow();
		assertThat(saved.isAiTitleRequested()).isTrue();
		assertThat(calls.get()).isZero();
		saved.storeTranscript("Budget review from the video transcript.", 1000, "fixture", null, 1);
		repository.save(saved);
		videoJob.run(id);
		KnowledgeItem result = repository.findById(id).orElseThrow();
		assertThat(result.getTitle()).isEqualTo("Meeting October 27, 2026 - Budget Review");
		assertThat(result.getStatus().name()).isEqualTo("COMPLETED");
		assertThat(result.isAiTitleRequested()).isFalse();
		assertThat(lastInput).contains("video transcript");
		assertThat(calls.get()).isEqualTo(1);
	}

	@Test
	void namingFailureKeepsContentAndGuidanceForAnExplicitRetry() throws Exception {
		String admin = login("admin@acme.example", "admin123");
		unavailable = true;
		MockMultipartFile file = new MockMultipartFile("file", "meeting.md", "text/markdown",
				"Budget review.".getBytes(StandardCharsets.UTF_8));
		JsonNode failed = upload(file, true, admin, 201);
		assertThat(failed.path("lastErrorCode").asString()).isEqualTo("AI_TITLE_FAILED");
		long id = failed.path("id").asLong();
		KnowledgeItem saved = repository.findById(id).orElseThrow();
		assertThat(saved.getTitle()).isEqualTo("Meeting 10/27/2026");
		assertThat(saved.getBodyText()).isEqualTo("Budget review.");
		assertThat(saved.isAiTitleRequested()).isTrue();
		unavailable = false;
		JsonNode retried = retry(id, admin);
		assertThat(retried.path("status").asString()).isEqualTo("COMPLETED");
		assertThat(retried.path("title").asString()).contains("Budget Review");
		assertThat(calls.get()).isEqualTo(2);
	}

	@Test
	void indexingRetryReusesTheAlreadyGeneratedTitleWithoutAnotherAiCall() throws Exception {
		doThrow(new GbrainException(GbrainErrorCode.UNAVAILABLE, "Engine unavailable")).doCallRealMethod().when(gbrain)
				.upsertDocument(any());
		String admin = login("admin@acme.example", "admin123");
		JsonNode failed = upload(new MockMultipartFile("file", "budget.md", "text/markdown",
				"Budget review.".getBytes(StandardCharsets.UTF_8)), true, admin, 201);
		assertThat(failed.path("status").asString()).isEqualTo("FAILED");
		assertThat(failed.path("title").asString()).contains("Budget Review");
		assertThat(retry(failed.path("id").asLong(), admin).path("status").asString()).isEqualTo("COMPLETED");
		assertThat(calls.get()).isEqualTo(1);
	}

	private JsonNode upload(MockMultipartFile file, boolean ai, String auth, int expected) throws Exception {
		return json.readTree(mvc
				.perform(multipart(file.getOriginalFilename().endsWith("mp4")
						? "/api/knowledge-items/video"
						: "/api/knowledge-items/upload")
						.file(file).param("title", "Meeting 10/27/2026").param("type", "Meeting")
						.param("useAiTitle", Boolean.toString(ai)).header("Authorization", auth))
				.andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
	}

	private JsonNode retry(long id, String auth) throws Exception {
		return json.readTree(
				mvc.perform(post("/api/knowledge-items/" + id + "/retry-ingestion").header("Authorization", auth))
						.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}

	private String login(String email, String password) throws Exception {
		String response = mvc
				.perform(post("/api/auth/login").contentType("application/json")
						.content(json.writeValueAsString(Map.of("email", email, "password", password))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return "Bearer " + json.readTree(response).path("token").asString();
	}
}
