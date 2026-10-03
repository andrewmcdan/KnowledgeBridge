package com.capstone.knowledgebridge.gbrain;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.capstone.knowledgebridge.gbrain.model.GbrainDeleteResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainDocument;
import com.capstone.knowledgebridge.gbrain.model.GbrainRestoreResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainRetrieval;
import com.capstone.knowledgebridge.gbrain.model.GbrainSearchHit;
import com.capstone.knowledgebridge.gbrain.model.GbrainSearchRequest;
import com.capstone.knowledgebridge.gbrain.model.GbrainSearchResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainStoredDocument;
import com.capstone.knowledgebridge.gbrain.model.GbrainSynthesisRequest;
import com.capstone.knowledgebridge.gbrain.model.GbrainSynthesisResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainSynthesisResult.GbrainCitation;
import com.capstone.knowledgebridge.gbrain.model.GbrainWriteResult;

import tools.jackson.databind.JsonNode;

/**
 * {@link GbrainClient} backed by the MCP transport. Response shapes follow the pinned revision's {@code put_page},
 * {@code get_page}, {@code delete_page}, {@code restore_page}, {@code search}, and {@code synthesize} operations;
 * anything else is a protocol failure.
 */
class McpGbrainClient implements GbrainClient {

	private static final Logger log = LoggerFactory.getLogger(McpGbrainClient.class);

	private final GbrainMcpClient transport;

	McpGbrainClient(GbrainMcpClient transport) {
		this.transport = transport;
	}

	@Override
	public GbrainWriteResult upsertDocument(GbrainDocument document) {
		String slug = GbrainPages.slugFor(document.itemKey());
		JsonNode payload = transport
				.callTool(GbrainTool.PUT_PAGE, Map.of("slug", slug, "content", GbrainPages.render(document)))
				.payload();
		// gbrain can dedup a write onto another page; never report that as this item's write.
		if (!slug.equals(payload.path("slug").asString(""))) {
			throw new GbrainException(GbrainErrorCode.ENGINE, "gbrain put_page resolved to a different page");
		}
		String status = payload.path("status").asString("");
		if (payload.has("error") || "error".equals(status)) {
			throw new GbrainException(GbrainErrorCode.ENGINE, "gbrain put_page did not store the page", status, null,
					null);
		}
		return switch (status) {
			case "created_or_updated" -> new GbrainWriteResult(slug, GbrainWriteResult.Status.WRITTEN,
					payload.path("chunks").asInt(0));
			case "skipped" -> new GbrainWriteResult(slug, GbrainWriteResult.Status.UNCHANGED, 0);
			default -> throw unexpected("put_page");
		};
	}

	@Override
	public Optional<GbrainStoredDocument> getDocument(String itemKey) {
		String slug = GbrainPages.slugFor(itemKey);
		JsonNode page;
		try {
			page = transport
					.callTool(GbrainTool.GET_PAGE,
							Map.of("slug", slug, "include_deleted", true, "source_id", GbrainPages.SOURCE_ID))
					.payload();
		} catch (GbrainException exception) {
			if (exception.code() == GbrainErrorCode.NOT_FOUND) {
				return Optional.empty();
			}
			throw exception;
		}
		if (!slug.equals(page.path("slug").asString(""))) {
			throw unexpected("get_page");
		}
		JsonNode frontmatter = page.path("frontmatter");
		JsonNode revision = frontmatter.path(GbrainPages.REVISION_KEY);
		return Optional.of(new GbrainStoredDocument(slug, page.path("title").asString(null),
				GbrainPages.documentType(page.path("type").asString(null)),
				revision.isIntegralNumber() ? revision.asLong() : null,
				frontmatter.path(GbrainPages.DIGEST_KEY).asString(null), deletedAt(page.path("deleted_at"))));
	}

	@Override
	public GbrainDeleteResult deleteDocument(String itemKey) {
		try {
			return switch (writeStatus(GbrainTool.DELETE_PAGE, itemKey)) {
				case "soft_deleted" -> GbrainDeleteResult.DELETED;
				case "already_soft_deleted" -> GbrainDeleteResult.ALREADY_DELETED;
				default -> throw unexpected("delete_page");
			};
		} catch (GbrainException exception) {
			return notFound(exception, GbrainDeleteResult.NOT_FOUND);
		}
	}

	@Override
	public GbrainRestoreResult restoreDocument(String itemKey) {
		try {
			return switch (writeStatus(GbrainTool.RESTORE_PAGE, itemKey)) {
				case "restored" -> GbrainRestoreResult.RESTORED;
				case "already_active" -> GbrainRestoreResult.ALREADY_ACTIVE;
				default -> throw unexpected("restore_page");
			};
		} catch (GbrainException exception) {
			return notFound(exception, GbrainRestoreResult.NOT_FOUND);
		}
	}

	@Override
	public GbrainSearchResult search(GbrainSearchRequest request) {
		Map<String, Object> arguments = new LinkedHashMap<>();
		arguments.put("query", request.query());
		arguments.put("limit", request.limit());
		arguments.put("source_id", GbrainPages.SOURCE_ID);
		if (!request.documentTypes().isEmpty()) {
			arguments.put("types", request.documentTypes().stream().map(GbrainPages::pageType).sorted().toList());
		}
		GbrainToolResult result = transport.callTool(GbrainTool.SEARCH, arguments);
		if (!result.payload().isArray()) {
			throw unexpected("search");
		}
		List<GbrainSearchHit> hits = new ArrayList<>();
		int foreign = 0;
		for (JsonNode hit : result.payload()) {
			String slug = hit.path("slug").asString("");
			Optional<String> itemKey = GbrainPages.itemKey(slug);
			if (itemKey.isEmpty()) {
				foreign++;
				continue;
			}
			if (!hit.path("score").isNumber() || !hit.path("chunk_text").isString()) {
				throw unexpected("search");
			}
			hits.add(new GbrainSearchHit(itemKey.get(), slug, hit.path("title").asString(null),
					GbrainPages.documentType(hit.path("type").asString(null)), hit.path("chunk_text").asString(),
					hit.path("score").asDouble()));
		}
		warnForeignPages("search", foreign);
		return new GbrainSearchResult(hits, retrieval(result.metadata().path("retrieval")));
	}

	@Override
	public GbrainSynthesisResult synthesize(GbrainSynthesisRequest request) {
		JsonNode payload = transport.callTool(GbrainTool.SYNTHESIZE, Map.of("question", request.question()))
				.payload();
		GbrainSynthesisResult.Status status = switch (payload.path("synthesis_status").asString("")) {
			case "ok" -> GbrainSynthesisResult.Status.SYNTHESIZED;
			case "extractive_fallback" -> GbrainSynthesisResult.Status.EXTRACTIVE_FALLBACK;
			default -> throw unexpected("synthesize");
		};
		if (!payload.path("answer").isString()) {
			throw unexpected("synthesize");
		}
		Set<GbrainCitation> citations = new LinkedHashSet<>();
		int foreign = 0;
		for (String slug : strings(payload.path("sources"), "synthesize")) {
			Optional<String> itemKey = GbrainPages.itemKey(slug);
			if (itemKey.isPresent()) {
				citations.add(new GbrainCitation(itemKey.get(), slug));
			} else {
				foreign++;
			}
		}
		warnForeignPages("synthesize", foreign);
		JsonNode cost = payload.path("cost");
		GbrainSynthesisResult.Usage usage = new GbrainSynthesisResult.Usage(cost.path("model").asString(null),
				cost.path("input_tokens").isIntegralNumber() ? cost.path("input_tokens").asLong() : null,
				cost.path("output_tokens").isIntegralNumber() ? cost.path("output_tokens").asLong() : null,
				cost.path("usd_estimate").isNumber() ? cost.path("usd_estimate").decimalValue() : null);
		return new GbrainSynthesisResult(payload.path("answer").asString(), status, List.copyOf(citations),
				strings(payload.path("gaps"), "synthesize"), usage, strings(payload.path("warnings"), "synthesize"));
	}

	/** Search evidence is required: without it a keyword-only fallback would look like semantic retrieval. */
	private static GbrainRetrieval retrieval(JsonNode retrieval) {
		if (!retrieval.isObject()) {
			throw new GbrainException(GbrainErrorCode.PROTOCOL, "gbrain search returned no retrieval metadata");
		}
		List<GbrainRetrieval.Degradation> degraded = new ArrayList<>();
		JsonNode stages = retrieval.path("degraded");
		if (!stages.isMissingNode() && !stages.isArray()) {
			throw unexpected("search");
		}
		for (JsonNode stage : stages) {
			if (!stage.path("stage").isString()) {
				throw unexpected("search");
			}
			degraded.add(new GbrainRetrieval.Degradation(stage.path("stage").asString(),
					stage.path("reason").asString(null)));
		}
		return new GbrainRetrieval(retrieval.path("vector_enabled").asBoolean(false),
				retrieval.path("expansion_applied").asBoolean(false), degraded);
	}

	/** Reads an optional array of strings; any other shape is a protocol failure. */
	private static List<String> strings(JsonNode values, String tool) {
		if (values.isMissingNode()) {
			return List.of();
		}
		if (!values.isArray()) {
			throw unexpected(tool);
		}
		List<String> strings = new ArrayList<>();
		for (JsonNode value : values) {
			if (!value.isString()) {
				throw unexpected(tool);
			}
			strings.add(value.asString());
		}
		return strings;
	}

	/**
	 * The client is source-bound, so pages outside the application's slug prefix are operator-written content that
	 * cannot be mapped to a knowledge item. They are dropped; the count is logged without slugs or content.
	 */
	private static void warnForeignPages(String tool, int foreign) {
		if (foreign > 0) {
			log.warn("gbrain {} returned {} page(s) outside the {} prefix; they were omitted", tool, foreign,
					GbrainPages.SLUG_PREFIX);
		}
	}

	private String writeStatus(GbrainTool tool, String itemKey) {
		return transport
				.callTool(tool, Map.of("slug", GbrainPages.slugFor(itemKey), "source_id", GbrainPages.SOURCE_ID))
				.payload()
				.path("status")
				.asString("");
	}

	private static <T> T notFound(GbrainException exception, T result) {
		if (exception.code() == GbrainErrorCode.NOT_FOUND) {
			return result;
		}
		throw exception;
	}

	private static Instant deletedAt(JsonNode value) {
		if (value.isNull() || value.isMissingNode()) {
			return null;
		}
		try {
			return Instant.parse(value.asString(""));
		} catch (DateTimeParseException exception) {
			throw new GbrainException(GbrainErrorCode.PROTOCOL, "gbrain returned an invalid deleted_at", exception);
		}
	}

	private static GbrainException unexpected(String tool) {
		return new GbrainException(GbrainErrorCode.PROTOCOL, "gbrain " + tool + " returned an unexpected result");
	}

}
