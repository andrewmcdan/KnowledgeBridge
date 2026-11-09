package com.capstone.knowledgebridge;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
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
import tools.jackson.databind.json.JsonMapper;

import com.capstone.knowledgebridge.gbrain.GbrainClient;
import com.capstone.knowledgebridge.gbrain.GbrainException;
import com.capstone.knowledgebridge.gbrain.GbrainErrorCode;
import com.capstone.knowledgebridge.gbrain.model.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class KnowledgeLifecycleTests {
	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer(
			DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));
	@Autowired
	MockMvc mvc;
	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	JsonMapper json;
	@MockitoBean
	GbrainClient engine;
	@Autowired
	com.capstone.knowledgebridge.knowledge.ManualNoteEditor editor;
	String admin;
	String user;

	@BeforeEach
	void setup() throws Exception {
		admin = login("admin@acme.example", "admin123");
		user = login("user@acme.example", "user123");
		when(engine.upsertDocument(any())).thenAnswer(
				call -> new GbrainWriteResult("knowledgebridge/" + ((GbrainDocument) call.getArgument(0)).itemKey(),
						GbrainWriteResult.Status.WRITTEN, 1));
		when(engine.extractEntities(any())).thenReturn(new GbrainEntityExtraction(List.of(), false));
		when(engine.deleteDocument(anyString())).thenReturn(GbrainDeleteResult.DELETED);
	}

	@Test
	void onlyAdminsCanPerformAnyLifecycleMutation() throws Exception {
		long id = create();
		clearInvocations(engine);
		for (String action : List.of("deactivate", "activate", "restore", "trash", "permanent")) {
			String path = "/api/knowledge-items/" + id + (action.equals("trash") ? "" : "/" + action);
			boolean deleting = action.equals("trash") || action.equals("permanent");
			mvc.perform(deleting ? delete(path) : post(path)).andExpect(status().isUnauthorized());
			mvc.perform((deleting ? delete(path) : post(path)).header("Authorization", user))
					.andExpect(status().isForbidden());
		}
		assertThat(state(id)).isEqualTo("ACTIVE");
		verifyNoInteractions(engine);
	}

	@Test
	void exclusionInvalidatesCacheBlocksReadsAndRejectsStaleEngineAnswers() throws Exception {
		long id = create();
		when(engine.synthesize(any()))
				.thenReturn(new GbrainSynthesisResult("Document answer", GbrainSynthesisResult.Status.SYNTHESIZED,
						List.of(new GbrainSynthesisResult.GbrainCitation(Long.toString(id), "knowledgebridge/" + id)),
						List.of(), null, List.of()));
		query("lifecycle " + id).andExpect(status().isOk()).andExpect(jsonPath("$.cacheHit").value(false));
		query("lifecycle " + id).andExpect(status().isOk()).andExpect(jsonPath("$.cacheHit").value(true));
		mvc.perform(post("/api/knowledge-items/{id}/deactivate", id).header("Authorization", admin))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/knowledge-items/{id}/content", id).header("Authorization", user))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/knowledge-items/{id}/content", id).header("Authorization", admin))
				.andExpect(status().isOk());
		mvc.perform(get("/api/knowledge-items/{id}/entities", id).header("Authorization", user))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/knowledge-items/{id}/entities", id).header("Authorization", admin))
				.andExpect(status().isOk());
		mvc.perform(post("/api/knowledge-items/{id}/extract-entities", id).header("Authorization", admin))
				.andExpect(status().isConflict());
		mvc.perform(get("/api/knowledge-items").header("Authorization", user))
				.andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
		query("lifecycle " + id).andExpect(status().isConflict());
		mvc.perform(put("/api/knowledge-items/{id}/manual", id).header("Authorization", admin)
				.contentType("application/json")
				.content(json
						.writeValueAsString(Map.of("title", "New", "type", "Note", "text", "New body", "revision", 1))))
				.andExpect(status().isConflict());
		mvc.perform(post("/api/knowledge-items/{id}/activate", id).header("Authorization", admin))
				.andExpect(status().isNoContent());
		query("lifecycle " + id).andExpect(status().isOk()).andExpect(jsonPath("$.cacheHit").value(false));
		mvc.perform(get("/api/knowledge-items/{id}/content", id).header("Authorization", user))
				.andExpect(status().isOk());
	}

	@Test
	void trashRestoresPreviousStateAndPermanentDeletionCleansOnlyAssociatedMetadata() throws Exception {
		long id = create();
		long other = create();
		long shared = jdbc.queryForObject(
				"INSERT INTO detected_entity(external_id,name,type,summary) VALUES (?, 'Shared', 'TEAM','Shared summary') RETURNING id",
				Long.class, "shared-" + id);
		long orphan = jdbc.queryForObject(
				"INSERT INTO detected_entity(external_id,name,type,summary) VALUES (?, 'Only this doc', 'TEAM','Private summary') RETURNING id",
				Long.class, "orphan-" + id);
		jdbc.update("INSERT INTO knowledge_item_entity VALUES (?, ?), (?, ?), (?, ?)", id, shared, other, shared, id,
				orphan);
		mvc.perform(delete("/api/knowledge-items/{id}/permanent", id).header("Authorization", admin))
				.andExpect(status().isConflict());
		mvc.perform(delete("/api/knowledge-items/{id}", id).header("Authorization", admin))
				.andExpect(status().isNoContent());
		mvc.perform(post("/api/knowledge-items/{id}/restore", id).header("Authorization", admin))
				.andExpect(status().isNoContent());
		assertThat(state(id)).isEqualTo("ACTIVE");
		mvc.perform(post("/api/knowledge-items/{id}/deactivate", id).header("Authorization", admin))
				.andExpect(status().isNoContent());
		mvc.perform(delete("/api/knowledge-items/{id}", id).header("Authorization", admin))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/knowledge-items/{id}", id).header("Authorization", admin))
				.andExpect(jsonPath("$.trashedFrom").value("INACTIVE"));
		mvc.perform(post("/api/knowledge-items/{id}/restore", id).header("Authorization", admin))
				.andExpect(status().isNoContent());
		assertThat(state(id)).isEqualTo("INACTIVE");
		mvc.perform(delete("/api/knowledge-items/{id}", id).header("Authorization", admin))
				.andExpect(status().isNoContent());
		mvc.perform(delete("/api/knowledge-items/{id}/permanent", id).header("Authorization", admin))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/knowledge-items/{id}", id).header("Authorization", admin))
				.andExpect(status().isNotFound());
		for (String table : List.of("knowledge_item_entity", "ingestion_attempt", "entity_extraction")) {
			assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE knowledge_item_id = ?", Long.class,
					id)).isZero();
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM detected_entity WHERE id = ?", Long.class, orphan))
				.isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM detected_entity WHERE id = ?", Long.class, shared))
				.isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM query_cache", Long.class)).isZero();
		assertThat(state(other)).isEqualTo("ACTIVE");
	}

	@Test
	void busyAndMissingDocumentsAreRejectedAndEngineFailuresRollBack() throws Exception {
		mvc.perform(delete("/api/knowledge-items/999999").header("Authorization", admin))
				.andExpect(status().isNotFound());
		long id = create();
		for (String state : List.of("PENDING", "PROCESSING")) {
			jdbc.update("UPDATE knowledge_item SET status = ? WHERE id = ?", state, id);
			mvc.perform(delete("/api/knowledge-items/{id}", id).header("Authorization", admin))
					.andExpect(status().isConflict());
		}
		jdbc.update("UPDATE knowledge_item SET status = 'COMPLETED' WHERE id = ?", id);
		when(engine.deleteDocument(anyString()))
				.thenThrow(new GbrainException(GbrainErrorCode.UNAVAILABLE, "Engine unavailable"));
		mvc.perform(delete("/api/knowledge-items/{id}", id).header("Authorization", admin))
				.andExpect(status().isServiceUnavailable());
		assertThat(state(id)).isEqualTo("ACTIVE");
	}

	private String state(long id) {
		return jdbc.queryForObject("SELECT lifecycle_state FROM knowledge_item WHERE id = ?", String.class, id);
	}

	@Test
	void overlappingEditsCannotReviveContentDuringDeactivation() throws Exception {
		long id = create();
		var entered = new java.util.concurrent.CountDownLatch(1);
		var proceed = new java.util.concurrent.CountDownLatch(1);
		when(engine.deleteDocument(Long.toString(id))).thenAnswer(call -> {
			entered.countDown();
			if (!proceed.await(10, java.util.concurrent.TimeUnit.SECONDS))
				throw new IllegalStateException("Test synchronization timed out");
			return GbrainDeleteResult.DELETED;
		});
		try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
			var hide = executor.submit(
					() -> mvc.perform(post("/api/knowledge-items/{id}/deactivate", id).header("Authorization", admin)));
			try {
				assertThat(entered.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
				var edit = executor
						.submit(() -> editor.prepareUpdate(id, "Stale edit", "Note", "Must not be reindexed", 1));
				assertThatThrownBy(() -> edit.get(100, java.util.concurrent.TimeUnit.MILLISECONDS))
						.isInstanceOf(java.util.concurrent.TimeoutException.class);
				proceed.countDown();
				hide.get(10, java.util.concurrent.TimeUnit.SECONDS).andExpect(status().isNoContent());
				assertThat(edit.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEmpty();
				assertThat(state(id)).isEqualTo("INACTIVE");
			} finally {
				proceed.countDown();
			}
		}
	}
	private org.springframework.test.web.servlet.ResultActions query(String question) throws Exception {
		return mvc.perform(post("/api/query/synthesize").header("Authorization", user).contentType("application/json")
				.content(json.writeValueAsString(Map.of("question", question))));
	}
	private String login(String email, String password) throws Exception {
		String result = mvc
				.perform(post("/api/auth/login").contentType("application/json")
						.content(json.writeValueAsString(Map.of("email", email, "password", password))))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return "Bearer " + json.readTree(result).path("token").asString();
	}
	private long create() throws Exception {
		String result = mvc
				.perform(post("/api/knowledge-items/manual").header("Authorization", admin)
						.contentType("application/json")
						.content(json.writeValueAsString(Map.of("title", "Lifecycle fixture", "type", "Policy", "text",
								"Only the lifecycle fixture contains this sentence."))))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return json.readTree(result).path("id").asLong();
	}
}
