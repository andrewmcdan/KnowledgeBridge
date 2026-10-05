package com.capstone.knowledgebridge.gbrain.model;

/** Outcome of restoring a soft-deleted page. {@code NOT_FOUND} means the page was purged and must be re-ingested. */
public enum GbrainRestoreResult {
	RESTORED, ALREADY_ACTIVE, NOT_FOUND
}
