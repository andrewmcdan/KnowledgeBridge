package com.capstone.knowledgebridge.ingestion;

import java.util.Locale;

import com.capstone.knowledgebridge.gbrain.model.GbrainDocument;
import com.capstone.knowledgebridge.knowledge.KnowledgeItem;

/**
 * Builds the GbrainDocument the gbrain adapter expects from one of our KnowledgeItem rows.
 *
 * Kept out of IngestionService so the type-slugging rule below can be tested on its own: GbrainDocument only accepts
 * lower-case letters/digits/underscore/hyphen for documentType, but our "type" field is free text (e.g. "Finance
 * Policy"), and GbrainDocument only lower-cases for us, it doesn't strip spaces.
 */
final class GbrainDocumentMapper {

	private GbrainDocumentMapper() {
	}

	static GbrainDocument toDocument(KnowledgeItem item) {
		// Long.toString throws on a null id instead of silently producing the string "null" the way
		// String.valueOf would - item must already be saved (have a real id) before this is called.
		//
		// Both timestamp slots use createdAt, not getUpdatedAt(): updatedAt bumps on every status
		// transition (markProcessing/markFailed/...), but GbrainDocument.contentDigest() hashes this
		// field in. If we fed it updatedAt, a retry after a timeout would always hash differently from
		// the original attempt even though the actual content hasn't changed, which defeats gbrain's own
		// identical-content UNCHANGED check right when retry needs it most. There's no edit endpoint yet,
		// so content genuinely never changes after creation - createdAt is the right "last content
		// change" timestamp until one exists.
		return new GbrainDocument(
				Long.toString(item.getId()),
				item.getTitle(),
				slugifyType(item.getType()),
				Long.toString(item.getOwnerId()),
				item.getRevision(),
				item.getCreatedAt(),
				item.getCreatedAt(),
				item.getBodyText());
	}

	private static String slugifyType(String type) {
		String slug = type.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-").replaceAll("^-+|-+$", "");
		if (slug.isEmpty()) {
			slug = "document";
		}
		return slug.length() > 64 ? slug.substring(0, 64) : slug;
	}

}
