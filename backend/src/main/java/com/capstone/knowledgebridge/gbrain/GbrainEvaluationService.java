package com.capstone.knowledgebridge.gbrain;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;

import com.capstone.knowledgebridge.gbrain.model.GbrainSynthesisResult;
import com.capstone.knowledgebridge.knowledge.KnowledgeItemRepository;

/** Opt-in, uncached diagnostics. The normal query API never exposes prompts or accepts model overrides. */
@Service
public class GbrainEvaluationService {

	public record Result(GbrainSynthesisResult synthesis, JsonNode trace) {
	}

	private final GbrainMcpClient transport;
	private final KnowledgeItemRepository items;
	private final boolean enabled;
	private final Set<String> models;

	public GbrainEvaluationService(GbrainMcpClient transport, KnowledgeItemRepository items,
			@Value("${KNOWLEDGEBRIDGE_EVALUATION_ENABLED:false}") boolean enabled,
			@Value("${GBRAIN_EVALUATION_MODELS:}") String models) {
		this.transport = transport;
		this.items = items;
		this.enabled = enabled;
		this.models = Arrays.stream(models.split(",")).map(String::trim).collect(Collectors.toSet());
	}

	public Result evaluate(String question, String model, List<Long> allowedItemIds) {
		if (!enabled) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evaluation diagnostics are disabled.");
		}
		if (!models.contains(model)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evaluation model is not allowed.");
		}
		List<Long> unique = allowedItemIds.stream().distinct().toList();
		var available = items.findAllById(unique);
		if (available.size() != unique.size() || available.stream().anyMatch(item -> !item.isAvailable())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "A scoped document is unavailable.");
		}
		var payload = transport.callTool(GbrainTool.SYNTHESIZE, Map.of("question", question, "evaluation",
				Map.of("model", model, "allowed_slugs", unique.stream().map(id -> "knowledgebridge/" + id).toList())))
				.payload();
		JsonNode trace = payload.path("evaluation_trace");
		if (!trace.isObject() || !trace.path("candidates").isArray() || !trace.path("selected_pages").isArray()
				|| !trace.path("system_prompt").isString() || !trace.path("user_message").isString()
				|| !trace.path("context_sha256").asString("").matches("[0-9a-f]{64}")) {
			throw new GbrainException(GbrainErrorCode.PROTOCOL,
					"Engine does not provide a valid evaluation trace; rebuild the instrumented pinned image.");
		}
		return new Result(McpGbrainClient.parseSynthesis(payload), trace);
	}
}
