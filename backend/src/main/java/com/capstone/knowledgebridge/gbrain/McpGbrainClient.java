package com.capstone.knowledgebridge.gbrain;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;

import com.capstone.knowledgebridge.gbrain.model.GbrainDeleteResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainDocument;
import com.capstone.knowledgebridge.gbrain.model.GbrainRestoreResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainStoredDocument;
import com.capstone.knowledgebridge.gbrain.model.GbrainWriteResult;

import tools.jackson.databind.JsonNode;

/**
 * {@link GbrainClient} backed by the MCP transport. Response shapes follow the pinned revision's {@code put_page},
 * {@code get_page}, {@code delete_page}, and {@code restore_page} operations; anything else is a protocol failure.
 */
class McpGbrainClient implements GbrainClient {

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
