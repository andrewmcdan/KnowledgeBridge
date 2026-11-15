package com.capstone.knowledgebridge.gbrain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import com.capstone.knowledgebridge.knowledge.KnowledgeItem;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemRepository;

class GbrainEvaluationServiceTests {

	private final GbrainMcpClient transport = mock(GbrainMcpClient.class);
	private final KnowledgeItemRepository items = mock(KnowledgeItemRepository.class);
	private final JsonMapper json = JsonMapper.builder().build();
	private final GbrainEvaluationService service = new GbrainEvaluationService(transport, items, true,
			"model-a, model-b");

	private ObjectNode payload() {
		ObjectNode root = (ObjectNode) json.readTree("""
				{"answer":"Supported answer","sources":[],"gaps":[],"warnings":[],"synthesis_status":"ok",
				"evaluation_trace":{"candidates":[],"selected_pages":[],"system_prompt":"system","user_message":"user"}}
				""");
		((ObjectNode) root.path("evaluation_trace")).put("context_sha256", "0".repeat(64));
		return root;
	}

	@Test
	void disabledAndUnlistedModelsMakeNoEngineCalls() {
		assertThatThrownBy(() -> new GbrainEvaluationService(transport, items, false, "model-a")
				.evaluate("Question", "model-a", List.of())).hasMessageContaining("disabled");
		assertThatThrownBy(() -> service.evaluate("Question", "not-allowed", List.of()))
				.hasMessageContaining("not allowed");
		verifyNoInteractions(transport, items);
	}

	@Test
	void unavailableOrMissingScopeItemsFailBeforePaidCalls() {
		when(items.findAllById(List.of(1L))).thenReturn(List.of());
		assertThatThrownBy(() -> service.evaluate("Q", "model-a", List.of(1L))).hasMessageContaining("unavailable");
		when(items.findAllById(List.of(1L))).thenReturn(List.of(new KnowledgeItem("Title", "Note", 1L, "body")));
		assertThatThrownBy(() -> service.evaluate("Q", "model-a", List.of(1L))).hasMessageContaining("unavailable");
		verifyNoInteractions(transport);
	}

	@Test
	void tracesTheActualUncachedCallWithDistinctAvailableScopeIds() {
		KnowledgeItem item = new KnowledgeItem("Title", "Note", 1L, "body");
		item.markCompleted("knowledgebridge/1");
		when(items.findAllById(List.of(1L))).thenReturn(List.of(item));
		ObjectNode payload = payload();
		when(transport.callTool(eq(GbrainTool.SYNTHESIZE), any()))
				.thenReturn(new GbrainToolResult(payload, json.createObjectNode()));
		var result = service.evaluate("Question", "model-b", List.of(1L, 1L));
		assertThat(result.synthesis().answer()).isEqualTo("Supported answer");
		assertThat(result.trace()).isEqualTo(payload.path("evaluation_trace"));
		verify(transport).callTool(GbrainTool.SYNTHESIZE, Map.of("question", "Question", "evaluation",
				Map.of("model", "model-b", "allowed_slugs", List.of("knowledgebridge/1"))));
		assertThat(GbrainTool.SYNTHESIZE.safeToRetry()).isFalse();
	}

	@Test
	void rejectsMissingOrMalformedTracesRatherThanInventingEvidence() {
		ObjectNode badRoot = payload();
		badRoot.remove("evaluation_trace");
		assertInvalid(badRoot);
		for (String field : List.of("candidates", "selected_pages", "system_prompt", "user_message",
				"context_sha256")) {
			ObjectNode root = payload();
			((ObjectNode) root.path("evaluation_trace")).remove(field);
			assertInvalid(root);
		}
	}

	private void assertInvalid(ObjectNode root) {
		when(transport.callTool(eq(GbrainTool.SYNTHESIZE), any()))
				.thenReturn(new GbrainToolResult(root, json.createObjectNode()));
		assertThatThrownBy(() -> service.evaluate("Q", "model-a", List.of()))
				.isInstanceOf(GbrainException.class).hasMessageContaining("valid evaluation trace");
	}
}
