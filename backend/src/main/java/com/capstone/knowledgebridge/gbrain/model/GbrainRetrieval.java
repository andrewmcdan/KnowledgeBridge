package com.capstone.knowledgebridge.gbrain.model;

import java.util.List;

/**
 * How gbrain actually ran a retrieval, from the {@code _meta.retrieval} block. This is the evidence that semantic
 * search was used; {@code /health} does not prove it.
 *
 * @param vectorEnabled
 *            whether the vector arm ran; false means keyword-only retrieval
 * @param expansionApplied
 *            whether LLM query expansion ran; always false for {@code search}
 * @param degraded
 *            stages that failed open, such as {@code embed_unavailable}; empty for a clean run
 */
public record GbrainRetrieval(boolean vectorEnabled, boolean expansionApplied, List<Degradation> degraded) {

	public GbrainRetrieval {
		degraded = List.copyOf(degraded);
	}

	/** True when semantic retrieval ran and no stage degraded. */
	public boolean healthy() {
		return vectorEnabled && degraded.isEmpty();
	}

	/**
	 * One degraded stage from gbrain's closed vocabulary.
	 *
	 * @param stage
	 *            stage code such as {@code embed_unavailable} or {@code vector_arm_failed}
	 * @param reason
	 *            reason code such as {@code provider_error}, or {@code null} when gbrain gave none
	 */
	public record Degradation(String stage, String reason) {
	}

}
