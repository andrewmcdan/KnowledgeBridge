package com.capstone.knowledgebridge.gbrain.model;

/** Reconciliation result used after an ambiguous write before deciding whether to retry. */
public enum GbrainDocumentState {

	/** gbrain holds exactly this document; the write succeeded and must not be repeated. */
	CURRENT,

	/** gbrain holds a different version of the item; a deliberate retry is required. */
	STALE,

	/** gbrain has no page for the item; a deliberate retry is required. */
	MISSING,

	/** gbrain holds the item only as a soft-deleted page. Writing it again would restore it. */
	DELETED

}
