package com.capstone.knowledgebridge.gbrain.model;

import com.capstone.knowledgebridge.gbrain.GbrainErrorCode;

/**
 * Result of the unauthenticated {@code /health} probe. {@link Status#UP} proves only that the service and its database
 * respond; it does not prove that authentication, embeddings, or individual tools work.
 */
public record GbrainHealth(Status status, String version, GbrainErrorCode failure) {

	public enum Status {
		UP, DOWN, DISABLED
	}

	public static GbrainHealth up(String version) {
		return new GbrainHealth(Status.UP, version, null);
	}

	public static GbrainHealth down(GbrainErrorCode failure) {
		return new GbrainHealth(Status.DOWN, null, failure);
	}

	public static GbrainHealth disabled() {
		return new GbrainHealth(Status.DISABLED, null, null);
	}

}
