package com.capstone.knowledgebridge.gbrain;

import java.util.Optional;

import com.capstone.knowledgebridge.gbrain.model.GbrainDeleteResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainDocument;
import com.capstone.knowledgebridge.gbrain.model.GbrainDocumentState;
import com.capstone.knowledgebridge.gbrain.model.GbrainRestoreResult;
import com.capstone.knowledgebridge.gbrain.model.GbrainStoredDocument;
import com.capstone.knowledgebridge.gbrain.model.GbrainWriteResult;

/**
 * Application-facing gbrain operations. Implementations throw {@link GbrainException} with a stable
 * {@link GbrainErrorCode}; they never update knowledge items or ingestion attempts, and they never retry writes.
 *
 * <p>
 * After a {@link GbrainErrorCode#TIMEOUT} on a write the outcome is unknown: call {@link #documentState} before
 * deciding whether to retry.
 */
public interface GbrainClient {

	/** Creates or completely replaces the page for {@code document.itemKey()}. */
	GbrainWriteResult upsertDocument(GbrainDocument document);

	/** Returns the stored page for an item, including a soft-deleted one, or empty if gbrain has none. */
	Optional<GbrainStoredDocument> getDocument(String itemKey);

	/** Soft-deletes the page for an item. Not retried automatically. */
	GbrainDeleteResult deleteDocument(String itemKey);

	/** Restores a soft-deleted page for an item. Not retried automatically. */
	GbrainRestoreResult restoreDocument(String itemKey);

	/** Reconciles what gbrain holds with the document the application expects. */
	default GbrainDocumentState documentState(GbrainDocument document) {
		return getDocument(document.itemKey()).map(stored -> stored.stateFor(document))
				.orElse(GbrainDocumentState.MISSING);
	}

}
