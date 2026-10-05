package com.capstone.knowledgebridge.gbrain.model;

/**
 * Outcome of a soft deletion. gbrain hides the page immediately and its autopilot purges soft-deleted pages after a
 * 72-hour recovery window, so a deletion is restorable only within that window.
 */
public enum GbrainDeleteResult {
	DELETED, ALREADY_DELETED, NOT_FOUND
}
