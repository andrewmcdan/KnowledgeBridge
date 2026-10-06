package com.capstone.knowledgebridge.gbrain.model;

/**
 * Confirmed outcome of an upsert. Store {@code externalId} on the knowledge item only after receiving this result or
 * after {@link GbrainDocumentState#CURRENT} reconciliation.
 *
 * @param externalId
 *            gbrain page slug for the item
 * @param status
 *            whether gbrain wrote new content or already held identical content
 * @param chunks
 *            number of searchable chunks written; zero when the content was unchanged
 */
public record GbrainWriteResult(String externalId, Status status, int chunks) {

	public enum Status {
		/** gbrain stored and chunked the content (a soft-deleted page is also revived). */
		WRITTEN,
		/** gbrain already held identical content; nothing changed. */
		UNCHANGED
	}

}
