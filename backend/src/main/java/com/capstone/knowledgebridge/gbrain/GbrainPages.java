package com.capstone.knowledgebridge.gbrain;

import com.capstone.knowledgebridge.gbrain.model.GbrainDocument;

/**
 * Maps knowledge items to gbrain pages: deterministic slugs inside the source-bound {@code knowledgebridge/} prefix and
 * complete Markdown with controlled frontmatter. Only the keys written here reach gbrain's frontmatter, so uploaded
 * content cannot set {@code id}, {@code visibility}, quarantine, or embedding controls.
 */
final class GbrainPages {

	/** Source and slug prefix the OAuth client is bound to by scripts/provision-gbrain.ps1. */
	static final String SOURCE_ID = "knowledgebridge";

	static final String SLUG_PREFIX = "knowledgebridge/";

	static final String ID_KEY = "knowledgebridge_id";

	static final String REVISION_KEY = "knowledgebridge_revision";

	static final String DIGEST_KEY = "knowledgebridge_digest";

	private GbrainPages() {
	}

	static String slugFor(String itemKey) {
		return SLUG_PREFIX + GbrainDocument.requireItemKey(itemKey);
	}

	static String render(GbrainDocument document) {
		return "---\n"
				+ "title: " + quote(document.title()) + "\n"
				+ "type: " + quote(document.documentType()) + "\n"
				+ ID_KEY + ": " + quote(document.itemKey()) + "\n"
				+ "knowledgebridge_owner: " + quote(document.ownerId()) + "\n"
				+ REVISION_KEY + ": " + document.revision() + "\n"
				+ "knowledgebridge_created_at: " + quote(document.createdAt().toString()) + "\n"
				+ "knowledgebridge_updated_at: " + quote(document.updatedAt().toString()) + "\n"
				+ DIGEST_KEY + ": " + quote(document.contentDigest()) + "\n"
				+ "---\n\n"
				+ document.body();
	}

	/** YAML double-quoted scalar. {@link GbrainDocument} already rejects control and line-separator characters. */
	static String quote(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

}
