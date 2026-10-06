package com.capstone.knowledgebridge.gbrain.model;

import java.time.Instant;

/**
 * What gbrain currently holds for an item, including soft-deleted pages.
 *
 * @param externalId
 *            gbrain page slug
 * @param title
 *            page title
 * @param documentType
 *            page type
 * @param revision
 *            application revision recorded in the page, or {@code null} if the page lacks KnowledgeBridge metadata
 * @param contentDigest
 *            {@link GbrainDocument#contentDigest()} recorded in the page, or {@code null} if absent
 * @param deletedAt
 *            soft-deletion time, or {@code null} for an active page
 */
public record GbrainStoredDocument(String externalId, String title, String documentType, Long revision,
		String contentDigest, Instant deletedAt) {

	public boolean deleted() {
		return deletedAt != null;
	}

	/** Compares this stored page with the document the application believes is current. */
	public GbrainDocumentState stateFor(GbrainDocument document) {
		if (deleted()) {
			return GbrainDocumentState.DELETED;
		}
		return document.contentDigest().equals(contentDigest)
				? GbrainDocumentState.CURRENT
				: GbrainDocumentState.STALE;
	}

}
