package com.capstone.knowledgebridge.gbrain.model;

import java.util.Set;
import java.util.stream.Collectors;

import com.capstone.knowledgebridge.gbrain.GbrainErrorCode;
import com.capstone.knowledgebridge.gbrain.GbrainException;

/**
 * A ranked retrieval request. gbrain's {@code search} is cheap hybrid retrieval: vector, keyword, and title arms fused
 * without LLM query expansion, so it has no per-call model cost.
 *
 * @param query
 *            search text; surrounding whitespace is removed
 * @param limit
 *            maximum number of hits, 1-{@value #MAX_LIMIT}
 * @param documentTypes
 *            application document types to restrict results to; empty for every type
 */
public record GbrainSearchRequest(String query, int limit, Set<String> documentTypes) {

	public static final int MAX_QUERY_LENGTH = 1000;

	public static final int MAX_LIMIT = 50;

	public GbrainSearchRequest {
		query = requireQuestionText("search query", query, MAX_QUERY_LENGTH);
		if (limit < 1 || limit > MAX_LIMIT) {
			throw invalid("search limit must be 1-" + MAX_LIMIT);
		}
		documentTypes = documentTypes == null
				? Set.of()
				: documentTypes.stream()
						.map(GbrainDocument::requireDocumentType)
						.collect(Collectors.toUnmodifiableSet());
	}

	/**
	 * Validates free text sent to gbrain as a query or question. It is never logged or written to frontmatter, so line
	 * breaks are allowed; NUL is not.
	 */
	static String requireQuestionText(String name, String value, int maxLength) {
		String stripped = value == null ? "" : value.strip();
		if (stripped.isEmpty() || stripped.length() > maxLength) {
			throw invalid(name + " must be 1-" + maxLength + " characters");
		}
		if (stripped.indexOf('\u0000') >= 0) {
			throw invalid(name + " must not contain NUL characters");
		}
		return stripped;
	}

	private static GbrainException invalid(String message) {
		return new GbrainException(GbrainErrorCode.VALIDATION, "gbrain " + message);
	}

}
