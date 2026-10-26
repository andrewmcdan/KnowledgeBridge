package com.capstone.knowledgebridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.capstone.knowledgebridge.gbrain.GbrainClient;
import com.capstone.knowledgebridge.gbrain.GbrainErrorCode;
import com.capstone.knowledgebridge.gbrain.GbrainException;
import com.capstone.knowledgebridge.gbrain.model.GbrainEntityExtraction;
import com.capstone.knowledgebridge.gbrain.model.GbrainSearchResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainRetrieval;
import com.capstone.knowledgebridge.gbrain.model.GbrainSynthesisResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainWriteResult;
import com.capstone.knowledgebridge.knowledge.KnowledgeItem;
import com.capstone.knowledgebridge.knowledge.ManualNoteEditor;

import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class NoteEditingAndUsageTests {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer(
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private ManualNoteEditor editor;
	@Autowired
	private JsonMapper json;
	@MockitoBean
	private GbrainClient engine;

	private String admin;
	private String user;

	@BeforeEach
	void setup() throws Exception {
		admin = login("admin@acme.example", "admin123");
		user = login("user@acme.example", "user123");
		when(engine.upsertDocument(any())).thenAnswer(invocation -> {
			com.capstone.knowledgebridge.gbrain.model.GbrainDocument document = invocation.getArgument(0);
			return new GbrainWriteResult("knowledgebridge/" + document.itemKey(), GbrainWriteResult.Status.WRITTEN, 1);
		});
		when(engine.extractEntities(any())).thenReturn(new GbrainEntityExtraction(List.of(), false));
	}

	@Test
	void editingReindexesTheSameIdentityAndInvalidatesCachedAnswers() throws Exception {
		long id = createNote("Travel allowance is 100 dollars.");
		String question = "What is the editable travel allowance?";
		when(engine.synthesize(any())).thenReturn(answer(id, "100 dollars."));
		query(question, user).andExpect(jsonPath("$.cacheHit").value(false));
		query(question, user).andExpect(jsonPath("$.cacheHit").value(true));
		mvc.perform(get("/api/knowledge-items/{id}/manual", id).header("Authorization", admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1));
		long previousGlobal = globalRevision();
		mvc.perform(put("/api/knowledge-items/{id}/manual", id).header("Authorization", admin)
				.contentType("application/json").content(edit("Travel allowance is 200 dollars.", 1)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.status").value("COMPLETED"));
		assertThat(globalRevision()).isGreaterThan(previousGlobal);
		org.mockito.Mockito.verify(engine)
				.upsertDocument(org.mockito.ArgumentMatchers
						.argThat(document -> document.itemKey().equals(Long.toString(id)) && document.revision() == 2
								&& document.body().equals("Travel allowance is 200 dollars.\n")));
		when(engine.synthesize(any())).thenReturn(answer(id, "200 dollars."));
		query(question, user).andExpect(jsonPath("$.cacheHit").value(false))
				.andExpect(jsonPath("$.answer").value("200 dollars."))
				.andExpect(jsonPath("$.usage.estimated").value(true));
		mvc.perform(put("/api/knowledge-items/{id}/manual", id).header("Authorization", admin)
				.contentType("application/json").content(edit("Stale overwrite", 1)))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOTE_CONFLICT"));
		assertThat(jdbc.queryForObject("SELECT body_text FROM knowledge_item WHERE id = ?", String.class, id))
				.isEqualTo("Travel allowance is 200 dollars.");
	}

	@Test
	void failedReindexPreservesTheEditAndRetriesItsRevisionAndTimestamp() throws Exception {
		long id = createNote("Original note");
		long previousGlobal = globalRevision();
		org.mockito.Mockito.doThrow(new GbrainException(GbrainErrorCode.TIMEOUT, "Timed out"))
				.when(engine).upsertDocument(any());
		mvc.perform(put("/api/knowledge-items/{id}/manual", id).header("Authorization", admin)
				.contentType("application/json").content(edit("Saved despite engine outage", 1)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.lastErrorCode").value("TIMEOUT"));
		assertThat(globalRevision()).isGreaterThan(previousGlobal);
		mvc.perform(get("/api/knowledge-items/{id}/manual", id).header("Authorization", admin))
				.andExpect(jsonPath("$.text").value("Saved despite engine outage"))
				.andExpect(jsonPath("$.revision").value(2));
		Object contentTime = jdbc.queryForMap("SELECT content_updated_at FROM knowledge_item WHERE id = ?", id)
				.get("content_updated_at");
		org.mockito.Mockito.doReturn(new GbrainWriteResult("knowledgebridge/" + id,
				GbrainWriteResult.Status.WRITTEN, 1)).when(engine).upsertDocument(any());
		mvc.perform(post("/api/knowledge-items/{id}/retry-ingestion", id).header("Authorization", admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
		assertThat(jdbc.queryForMap("SELECT revision, content_updated_at FROM knowledge_item WHERE id = ?", id))
				.containsEntry("revision", 2L).containsEntry("content_updated_at", contentTime);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM ingestion_attempt WHERE knowledge_item_id = ?",
				Long.class, id)).isEqualTo(3);
	}

	@Test
	void noteRoutesRejectUnauthorizedInvalidMissingAndNonManualRequests() throws Exception {
		long id = createNote("Editable body");
		mvc.perform(get("/api/knowledge-items/{id}/manual", id)).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/knowledge-items/{id}/manual", id).header("Authorization", user))
				.andExpect(status().isForbidden());
		mvc.perform(put("/api/knowledge-items/{id}/manual", id).contentType("application/json").content("{}"))
				.andExpect(status().isUnauthorized());
		mvc.perform(put("/api/knowledge-items/{id}/manual", id).header("Authorization", user)
				.contentType("application/json").content("not json"))
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/knowledge-items/999999999/manual").header("Authorization", admin))
				.andExpect(status().isNotFound());
		mvc.perform(put("/api/knowledge-items/999999999/manual").header("Authorization", admin)
				.contentType("application/json").content(edit("Valid text", 1))).andExpect(status().isNotFound());
		for (String invalid : List.of(edit("", 1), edit("Valid", 0), "{}")) {
			mvc.perform(put("/api/knowledge-items/{id}/manual", id).header("Authorization", admin)
					.contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
		}
		mvc.perform(put("/api/knowledge-items/{id}/manual", id).header("Authorization", admin)
				.contentType("application/json").content(edit("x".repeat(1024 * 1024), 1)))
				.andExpect(status().isContentTooLarge());
		jdbc.update("UPDATE knowledge_item SET source_kind = 'MARKDOWN' WHERE id = ?", id);
		mvc.perform(get("/api/knowledge-items/{id}/manual", id).header("Authorization", admin))
				.andExpect(status().isNotFound());
		mvc.perform(put("/api/knowledge-items/{id}/manual", id).header("Authorization", admin)
				.contentType("application/json").content(edit("Cannot edit an upload", 1)))
				.andExpect(status().isConflict());
	}

	@Test
	void concurrentEditorsCannotBothClaimTheSameRevision() throws Exception {
		long id = createNote("Original");
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> {
				start.await();
				return editor.prepareUpdate(id, "First", "Note", "One", 1);
			});
			var second = executor.submit(() -> {
				start.await();
				return editor.prepareUpdate(id, "Second", "Note", "Two", 1);
			});
			start.countDown();
			List<Optional<KnowledgeItem>> results = List.of(first.get(), second.get());
			assertThat(results.stream().filter(Optional::isPresent).count()).isEqualTo(1);
		}
		assertThat(editor.prepareRetry(id, 2)).isEmpty();
	}

	@Test
	void usageTotalsAreDurableScopedAndDoNotDoubleCountCacheHits() throws Exception {
		jdbc.update("TRUNCATE query_usage");
		jdbc.update("TRUNCATE query_cache");
		mvc.perform(get("/api/usage")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/usage").header("Authorization", user))
				.andExpect(jsonPath("$.scope").value("personal")).andExpect(jsonPath("$.totalTokens").value(0));
		when(engine.synthesize(any())).thenReturn(new GbrainSynthesisResult("abcdefgh",
				GbrainSynthesisResult.Status.SYNTHESIZED, List.of(), List.of(), null, List.of()));
		query("abcdefgh", user).andExpect(jsonPath("$.usage.inputTokens").value(2))
				.andExpect(jsonPath("$.usage.outputTokens").value(2))
				.andExpect(jsonPath("$.usage.estimated").value(true));
		query("abcdefgh", user).andExpect(jsonPath("$.cacheHit").value(true))
				.andExpect(jsonPath("$.usage.inputTokens").value(0));
		query("abcdefghijkl", admin).andExpect(jsonPath("$.usage.inputTokens").value(3));
		when(engine.search(any()))
				.thenReturn(new GbrainSearchResult(List.of(), new GbrainRetrieval(true, false, true, List.of())));
		mvc.perform(post("/api/query/search").header("Authorization", user).contentType("application/json")
				.content("{\"question\":\"Search only\"}")).andExpect(status().isOk());
		mvc.perform(get("/api/usage").header("Authorization", user))
				.andExpect(jsonPath("$.queryCount").value(3)).andExpect(jsonPath("$.totalTokens").value(4))
				.andExpect(jsonPath("$.cacheHits").value(1)).andExpect(jsonPath("$.estimatedQueryCount").value(1));
		mvc.perform(get("/api/usage").header("Authorization", admin))
				.andExpect(jsonPath("$.scope").value("workspace")).andExpect(jsonPath("$.queryCount").value(4))
				.andExpect(jsonPath("$.inputTokens").value(5)).andExpect(jsonPath("$.outputTokens").value(4))
				.andExpect(jsonPath("$.totalTokens").value(9));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM query_usage", Long.class)).isEqualTo(4);
	}

	private String login(String email, String password) throws Exception {
		String response = mvc.perform(post("/api/auth/login").contentType("application/json")
				.content(json.writeValueAsString(java.util.Map.of("email", email, "password", password))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return "Bearer " + json.readTree(response).path("token").asString();
	}

	private long createNote(String text) throws Exception {
		String response = mvc.perform(post("/api/knowledge-items/manual").header("Authorization", admin)
				.contentType("application/json")
				.content(json.writeValueAsString(java.util.Map.of("title", "Demo note", "type", "Note", "text", text))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return json.readTree(response).path("id").asLong();
	}

	private String edit(String text, long revision) {
		return json.writeValueAsString(
				java.util.Map.of("title", "Updated note", "type", "Policy", "text", text, "revision", revision));
	}

	private org.springframework.test.web.servlet.ResultActions query(String question, String token) throws Exception {
		return mvc.perform(post("/api/query/synthesize").header("Authorization", token).contentType("application/json")
				.content(json.writeValueAsString(java.util.Map.of("question", question)))).andExpect(status().isOk());
	}

	private GbrainSynthesisResult answer(long id, String text) {
		return new GbrainSynthesisResult(text, GbrainSynthesisResult.Status.SYNTHESIZED,
				List.of(new GbrainSynthesisResult.GbrainCitation(Long.toString(id), "knowledgebridge/" + id)),
				List.of(), null, List.of());
	}

	private long globalRevision() {
		return jdbc.queryForObject("SELECT knowledge_revision FROM system_state WHERE id = 1", Long.class);
	}
}
