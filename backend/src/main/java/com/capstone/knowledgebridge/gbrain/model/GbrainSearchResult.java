package com.capstone.knowledgebridge.gbrain.model;

import java.util.List;

/**
 * Ranked hits, best first, with the retrieval evidence needed to tell a clean miss from a degraded search.
 */
public record GbrainSearchResult(List<GbrainSearchHit> hits, GbrainRetrieval retrieval) {

	public GbrainSearchResult {
		hits = List.copyOf(hits);
	}

}
